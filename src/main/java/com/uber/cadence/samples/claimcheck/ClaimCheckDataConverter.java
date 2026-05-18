/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.uber.cadence.samples.claimcheck;

import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.converter.JsonDataConverter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@link DataConverter} that implements the <em>claim-check</em> pattern: payloads larger than
 * {@code thresholdBytes} are stored in an external {@link BlobStore} and only a small reference
 * travels through Cadence workflow history.
 *
 * <p>This solves the practical problem of Cadence's per-payload size limits (~2 MB) for workflows
 * that must pass very large datasets between the workflow and its activities, and reduces history
 * storage cost for long-running workflows that pass large repeatable data.
 *
 * <p>Wire format (after the JSON delegate produces the bytes):
 *
 * <ul>
 *   <li>{@code 0x00 || json} — payload is small enough to inline.
 *   <li>{@code 0x01 || jsonEnvelope} — payload was offloaded; the envelope JSON has the form {@code
 *       {"blobRef":"<bucket>/<sha256hex>"}}.
 * </ul>
 *
 * <p>Keys are derived from the SHA-256 of the payload so {@code toData} is idempotent across
 * Cadence workflow replays. Using a fresh UUID per call would write a new orphaned blob on every
 * replay because the SDK calls {@code toData} again each time the workflow re-executes from the
 * top. If the workflow needs to control the key (e.g. to encode routing metadata), generate it with
 * {@code Workflow.sideEffect} and pass it alongside the payload instead.
 */
/*
 * =============================================================================
 * Swapping LocalFsBlobStore for a real object store
 *
 * The DataConverter is storage-agnostic: any class that implements `BlobStore` (two methods, `put`
 * and `get`) will work. Swap `new LocalFsBlobStore()` in ClaimCheckWorker for your own impl and the
 * workflow/activity code stays the same. Backend pointers:
 *
 *  - AWS S3:        software.amazon.awssdk:s3:2.25.0  (S3Client + PutObjectRequest/GetObjectRequest)
 *  - GCS:           com.google.cloud:google-cloud-storage  (Storage.create(blobInfo, bytes))
 *  - Azure Blob:    com.azure:azure-storage-blob  (BlobContainerClient.getBlobClient(...))
 *  - MinIO / R2 /
 *    LocalStack:    same as S3, just call S3Client.builder().endpointOverride(URI.create("..."))
 *
 * Reference S3 sketch using AWS SDK v2:
 *
 * public final class S3BlobStore implements BlobStore {
 *   private final S3Client s3;
 *   private final String bucket;
 *
 *   public S3BlobStore(String bucket, String region) {
 *     this.s3 = S3Client.builder().region(Region.of(region)).build();
 *     this.bucket = bucket;
 *   }
 *
 *   public void put(String key, byte[] data) {
 *     s3.putObject(
 *         PutObjectRequest.builder().bucket(bucket).key(key).build(),
 *         RequestBody.fromBytes(data));
 *   }
 *
 *   public byte[] get(String key) {
 *     return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
 *         .asByteArray();
 *   }
 * }
 *
 * Wiring steps for any backend:
 *  1. Add the backend's SDK as a runtime dependency in build.gradle.
 *  2. Implement BlobStore against that SDK (≈30 lines, like the sketch above).
 *  3. Replace `new LocalFsBlobStore()` with your `BlobStore` impl in ClaimCheckWorker.
 *  4. Provide credentials via the SDK's standard mechanism (env vars, IAM role, etc.).
 *
 * Note on cleanup: this DataConverter does not delete blobs after the workflow completes. In
 * production, use the object store's lifecycle policies (S3 object lifecycle, GCS object lifecycle
 * management, Azure Blob lifecycle management, etc.) to automatically expire old blobs.
 * =============================================================================
 */
public final class ClaimCheckDataConverter implements DataConverter {

  /** Prefix byte for inline (below-threshold) payloads. */
  static final byte INLINE_PREFIX = (byte) 0x00;

  /** Prefix byte for offloaded payloads. */
  static final byte OFFLOAD_PREFIX = (byte) 0x01;

  private static final DataConverter delegate = JsonDataConverter.getInstance();

  private final BlobStore store;
  private final String bucket;
  private final int thresholdBytes;

  static final class BlobReference {
    public String blobRef;

    public BlobReference() {}

    BlobReference(String blobRef) {
      this.blobRef = blobRef;
    }
  }

  /**
   * @param store the BlobStore backend (use {@link LocalFsBlobStore} for zero-config demo).
   * @param bucket logical bucket / prefix name embedded in the reference key.
   * @param thresholdBytes max inline payload size; larger payloads are offloaded.
   */
  public ClaimCheckDataConverter(BlobStore store, String bucket, int thresholdBytes) {
    if (store == null) {
      throw new IllegalArgumentException("store must not be null");
    }
    if (bucket == null || bucket.trim().isEmpty()) {
      throw new IllegalArgumentException("bucket must not be null or empty");
    }
    if (thresholdBytes < 0) {
      throw new IllegalArgumentException("thresholdBytes must not be negative");
    }
    this.store = store;
    this.bucket = bucket;
    this.thresholdBytes = thresholdBytes;
  }

  @Override
  public byte[] toData(Object... values) throws DataConverterException {
    if (values == null || values.length == 0) {
      return null;
    }
    byte[] jsonBytes = delegate.toData(values);
    if (jsonBytes == null || jsonBytes.length == 0) {
      return jsonBytes;
    }

    if (jsonBytes.length <= thresholdBytes) {
      byte[] result = new byte[1 + jsonBytes.length];
      result[0] = INLINE_PREFIX;
      System.arraycopy(jsonBytes, 0, result, 1, jsonBytes.length);
      return result;
    }

    String key = bucket + "/" + sha256Hex(jsonBytes);
    try {
      store.put(key, jsonBytes);
    } catch (IOException e) {
      throw new DataConverterException(
          "Failed to offload payload to blob store (key=" + key + ")", e);
    }

    byte[] envBytes = delegate.toData(new BlobReference(key));
    byte[] result = new byte[1 + envBytes.length];
    result[0] = OFFLOAD_PREFIX;
    System.arraycopy(envBytes, 0, result, 1, envBytes.length);
    return result;
  }

  @Override
  public <T> T fromData(byte[] content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    byte[] payload = unwrap(content);
    return delegate.fromData(payload, valueClass, valueType);
  }

  @Override
  public Object[] fromDataArray(byte[] content, Type... valueTypes) throws DataConverterException {
    byte[] payload = unwrap(content);
    return delegate.fromDataArray(payload, valueTypes);
  }

  private byte[] unwrap(byte[] content) throws DataConverterException {
    if (content == null || content.length == 0) {
      return content;
    }
    byte prefix = content[0];
    byte[] body = new byte[content.length - 1];
    System.arraycopy(content, 1, body, 0, body.length);

    switch (prefix) {
      case INLINE_PREFIX:
        return body;
      case OFFLOAD_PREFIX:
        String key = extractBlobRef(body);
        try {
          return store.get(key);
        } catch (IOException e) {
          throw new DataConverterException(
              "claimcheck: failed to fetch payload from blob store (key=" + key + ")", e);
        }
      default:
        throw new DataConverterException(
            "claimcheck: unknown prefix byte 0x" + String.format("%02x", prefix & 0xff), null);
    }
  }

  private static String extractBlobRef(byte[] envelopeJson) throws DataConverterException {
    BlobReference reference =
        delegate.fromData(envelopeJson, BlobReference.class, BlobReference.class);
    if (reference == null || reference.blobRef == null || reference.blobRef.isEmpty()) {
      throw new DataConverterException("claimcheck: envelope missing blobRef field", null);
    }
    return reference.blobRef;
  }

  private static String sha256Hex(byte[] data) throws DataConverterException {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(data);
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new DataConverterException("SHA-256 is not available in this JVM", e);
    }
  }
}
