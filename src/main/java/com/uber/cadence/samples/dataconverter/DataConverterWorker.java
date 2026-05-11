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

package com.uber.cadence.samples.dataconverter;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;

/**
 * Hosts all three DataConverter sample workers in a single process. Each sample uses its own
 * {@link WorkflowClient} (and therefore its own {@link WorkerFactory}) because the
 * {@code DataConverter} is bound to {@code WorkflowClientOptions}.
 *
 * <p>On startup the worker prints a stats banner per sample showing the visible benefit of each
 * pattern (compression ratio, ciphertext preview, claim-check size), then begins polling all three
 * task lists in the background.
 */
public final class DataConverterWorker {

  private DataConverterWorker() {}

  public static void main(String[] args) {
    DataConverter compressionConverter = new CompressedJsonDataConverter();
    DataConverter encryptionConverter = new EncryptedJsonDataConverter(EncryptionKeyLoader.loadEncryptionKey());
    LocalFsBlobStore blobStore = new LocalFsBlobStore();
    DataConverter s3Converter =
        new S3OffloadDataConverter(
            blobStore,
            DataConverterConstants.S3_BUCKET,
            DataConverterConstants.S3_DEFAULT_THRESHOLD_BYTES);

    WorkerFactory compressionFactory = startCompressionWorker(compressionConverter);
    WorkerFactory encryptionFactory = startEncryptionWorker(encryptionConverter);
    WorkerFactory s3Factory = startS3OffloadWorker(s3Converter);

    printCompressionStats(compressionConverter);
    printEncryptionStats(encryptionConverter);
    printS3OffloadStats(blobStore);

    System.out.println(
        "DataConverterWorker listening on \""
            + DataConverterConstants.TASK_LIST_COMPRESSION
            + "\", \""
            + DataConverterConstants.TASK_LIST_ENCRYPTION
            + "\", \""
            + DataConverterConstants.TASK_LIST_S3
            + "\" (domain \""
            + DataConverterConstants.DOMAIN
            + "\").");

    // Keep references so the factories aren't GC'd while the process runs.
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  compressionFactory.shutdown();
                  encryptionFactory.shutdown();
                  s3Factory.shutdown();
                }));
  }

  private static WorkerFactory startCompressionWorker(DataConverter converter) {
    WorkflowClient client = DataConverterSupport.newWorkflowClient(converter);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(DataConverterConstants.TASK_LIST_COMPRESSION);
    worker.registerWorkflowImplementationTypes(CompressedDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new CompressedDataConverterWorkflow.ActivitiesImpl());
    factory.start();
    return factory;
  }

  private static WorkerFactory startEncryptionWorker(DataConverter converter) {
    WorkflowClient client = DataConverterSupport.newWorkflowClient(converter);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(DataConverterConstants.TASK_LIST_ENCRYPTION);
    worker.registerWorkflowImplementationTypes(EncryptedDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new EncryptedDataConverterWorkflow.ActivitiesImpl());
    factory.start();
    return factory;
  }

  private static WorkerFactory startS3OffloadWorker(DataConverter converter) {
    WorkflowClient client = DataConverterSupport.newWorkflowClient(converter);
    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(DataConverterConstants.TASK_LIST_S3);
    worker.registerWorkflowImplementationTypes(S3OffloadDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new S3OffloadDataConverterWorkflow.ActivitiesImpl());
    factory.start();
    return factory;
  }

  // ---------------- Stats banners ----------------

  private static void printCompressionStats(DataConverter converter) {
    CompressedDataConverterWorkflow.LargePayload payload =
        CompressedDataConverterWorkflow.createLargePayload();
    byte[] originalJson = JsonDataConverter.getInstance().toData(payload);
    byte[] compressed = converter.toData(payload);
    int originalSize = originalJson == null ? 0 : originalJson.length;
    int compressedSize = compressed == null ? 0 : compressed.length;
    double pct = originalSize == 0 ? 0.0 : (1.0 - (double) compressedSize / originalSize) * 100.0;

    System.out.println();
    System.out.println("=== Compression Sample Statistics ===");
    System.out.printf("Original JSON size:  %d bytes (%.2f KB)%n", originalSize, originalSize / 1024.0);
    System.out.printf("Compressed size:     %d bytes (%.2f KB)%n", compressedSize, compressedSize / 1024.0);
    System.out.printf("Compression ratio:   %.2f%% reduction%n", pct);
    System.out.printf(
        "Space saved:         %d bytes (%.2f KB)%n",
        originalSize - compressedSize, (originalSize - compressedSize) / 1024.0);
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        DataConverterConstants.DOMAIN,
        DataConverterConstants.TASK_LIST_COMPRESSION,
        DataConverterConstants.COMPRESSION_WORKFLOW_TYPE);
    System.out.println("=====================================");
    System.out.println();
  }

  private static void printEncryptionStats(DataConverter converter) {
    EncryptedDataConverterWorkflow.SensitiveCustomerRecord record =
        EncryptedDataConverterWorkflow.createSensitiveCustomerRecord();
    byte[] plaintext = JsonDataConverter.getInstance().toData(record);
    byte[] ciphertext = converter.toData(record);
    int plaintextSize = plaintext == null ? 0 : plaintext.length;
    int ciphertextSize = ciphertext == null ? 0 : ciphertext.length;
    String preview = ciphertext == null ? "" : hexPreview(ciphertext, 40);

    System.out.println();
    System.out.println("=== Encryption Sample Statistics ===");
    System.out.printf("Plaintext JSON size:  %d bytes%n", plaintextSize);
    System.out.printf(
        "Ciphertext size:      %d bytes (overhead: %d bytes nonce+tag)%n",
        ciphertextSize, ciphertextSize - plaintextSize);
    System.out.printf("Ciphertext preview:   %s%n", preview);
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        DataConverterConstants.DOMAIN,
        DataConverterConstants.TASK_LIST_ENCRYPTION,
        DataConverterConstants.ENCRYPTION_WORKFLOW_TYPE);
    System.out.println("====================================");
    System.out.println();
  }

  private static void printS3OffloadStats(LocalFsBlobStore store) {
    S3OffloadDataConverterWorkflow.S3LargePayload payload =
        S3OffloadDataConverterWorkflow.createS3LargePayload();
    byte[] jsonBytes = JsonDataConverter.getInstance().toData(payload);
    int jsonSize = jsonBytes == null ? 0 : jsonBytes.length;
    // History footprint = 1 prefix byte + JSON envelope {"__s3_ref":"<bucket>/<sha256hex>"}.
    // SHA-256 hex digest is 64 chars; bucket + "/" + 64 hex chars.
    int cadenceBytes =
        1
            + ("{\"__s3_ref\":\""
                    + DataConverterConstants.S3_BUCKET
                    + "/"
                    + repeatChar('a', 64)
                    + "\"}")
                .length();

    System.out.println();
    System.out.println("=== S3 Offload Sample Statistics ===");
    System.out.printf("Full payload JSON size:    %d bytes (%.2f KB)%n", jsonSize, jsonSize / 1024.0);
    System.out.printf("Stored in BlobStore:       %d bytes (%.2f KB)%n", jsonSize, jsonSize / 1024.0);
    System.out.printf(
        "Stored in Cadence history: %d bytes (claim-check reference only)%n", cadenceBytes);
    System.out.printf(
        "Reduction in Cadence:      %.1f%%%n",
        jsonSize == 0 ? 0.0 : 100.0 * (1.0 - (double) cadenceBytes / jsonSize));
    System.out.printf("BlobStore location:        %s%n", store.baseDir());
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        DataConverterConstants.DOMAIN,
        DataConverterConstants.TASK_LIST_S3,
        DataConverterConstants.S3_OFFLOAD_WORKFLOW_TYPE);
    System.out.println("=====================================");
    System.out.println();
  }

  private static String hexPreview(byte[] data, int byteLimit) {
    int len = Math.min(byteLimit, data.length);
    StringBuilder sb = new StringBuilder(len * 2 + 3);
    for (int i = 0; i < len; i++) {
      sb.append(String.format("%02x", data[i] & 0xff));
    }
    if (data.length > byteLimit) {
      sb.append("...");
    }
    return sb.toString();
  }

  private static String repeatChar(char c, int n) {
    char[] buf = new char[n];
    java.util.Arrays.fill(buf, c);
    return new String(buf);
  }
}
