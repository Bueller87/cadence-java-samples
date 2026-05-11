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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * {@link BlobStore} implementation backed by the local filesystem.
 *
 * <p>The default zero-config implementation used by {@link S3OffloadDataConverter} when running
 * the demo without real AWS. Files are written under {@code
 * ${java.io.tmpdir}/cadence-java-samples-data-s3/}.
 */
public final class LocalFsBlobStore implements BlobStore {

  private final Path baseDir;

  public LocalFsBlobStore() {
    this(Paths.get(System.getProperty("java.io.tmpdir"), "cadence-java-samples-data-s3"));
  }

  public LocalFsBlobStore(Path baseDir) {
    this.baseDir = baseDir;
    try {
      Files.createDirectories(baseDir);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create blob store dir " + baseDir, e);
    }
  }

  /** Returns the directory the store writes to (useful for stats banners). */
  public Path baseDir() {
    return baseDir;
  }

  @Override
  public void put(String key, byte[] data) throws IOException {
    Files.write(baseDir.resolve(sanitizeKey(key)), data);
  }

  @Override
  public byte[] get(String key) throws IOException {
    return Files.readAllBytes(baseDir.resolve(sanitizeKey(key)));
  }

  /**
   * Turns a {@code bucket/sha256hex} key into a single safe filename. Keys are always generated
   * internally by the DataConverter, but this provides a belt-and-suspenders guarantee against
   * directory traversal in case a future caller passes a user-controlled key.
   */
  private static String sanitizeKey(String key) {
    String flat = key.replace('/', '_').replace('\\', '_');
    int slash = Math.max(flat.lastIndexOf('/'), flat.lastIndexOf('\\'));
    return slash >= 0 ? flat.substring(slash + 1) : flat;
  }
}
