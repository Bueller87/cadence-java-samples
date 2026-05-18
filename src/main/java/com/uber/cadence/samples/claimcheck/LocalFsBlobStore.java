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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * {@link BlobStore} implementation backed by the local filesystem.
 *
 * <p>The default zero-config implementation used by {@link ClaimCheckDataConverter} when running
 * the demo without a real object store. Files are written under {@code
 * ${java.io.tmpdir}/cadence-java-samples-claimcheck/}.
 */
public final class LocalFsBlobStore implements BlobStore {

  private final Path baseDir;

  public LocalFsBlobStore() {
    this(Paths.get(System.getProperty("java.io.tmpdir"), "cadence-java-samples-claimcheck"));
  }

  public LocalFsBlobStore(Path baseDir) {
    if (baseDir == null) {
      throw new IllegalArgumentException("baseDir must not be null");
    }
    this.baseDir = baseDir.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.baseDir);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create blob store dir " + this.baseDir, e);
    }
  }

  /** Returns the directory the store writes to (useful for stats banners). */
  public Path baseDir() {
    return baseDir;
  }

  @Override
  public void put(String key, byte[] data) throws IOException {
    Files.write(baseDir.resolve(filenameForKey(key)), data);
  }

  @Override
  public byte[] get(String key) throws IOException {
    return Files.readAllBytes(baseDir.resolve(filenameForKey(key)));
  }

  /**
   * Turns any blob-store key into a fixed safe filename. Keys are usually generated internally by
   * the DataConverter, but hashing prevents directory traversal even if a future caller passes a
   * user-controlled key.
   */
  private static String filenameForKey(String key) throws IOException {
    if (key == null || key.isEmpty()) {
      throw new IOException("BlobStore key must not be null or empty");
    }
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(digest.length * 2);
      for (byte b : digest) {
        sb.append(String.format("%02x", b & 0xff));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IOException("SHA-256 is not available in this JVM", e);
    }
  }
}
