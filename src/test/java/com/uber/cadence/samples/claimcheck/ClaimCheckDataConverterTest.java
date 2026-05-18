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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.uber.cadence.converter.DataConverterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ClaimCheckDataConverterTest {

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testClaimCheckConverterInlinesBelowThreshold() {
    RecordingBlobStore store = new RecordingBlobStore();
    ClaimCheckDataConverter converter = new ClaimCheckDataConverter(store, "bucket", 1024);

    byte[] encoded = converter.toData("small");
    String decoded = converter.fromData(encoded, String.class, String.class);

    assertEquals(ClaimCheckDataConverter.INLINE_PREFIX, encoded[0]);
    assertEquals("small", decoded);
    assertTrue(store.blobs.isEmpty());
  }

  @Test
  public void testClaimCheckConverterOffloadsAndUsesIdempotentReference() {
    RecordingBlobStore store = new RecordingBlobStore();
    ClaimCheckDataConverter converter = new ClaimCheckDataConverter(store, "bucket", 1);

    byte[] first = converter.toData("large enough to offload");
    byte[] second = converter.toData("large enough to offload");
    String decoded = converter.fromData(first, String.class, String.class);

    assertEquals(ClaimCheckDataConverter.OFFLOAD_PREFIX, first[0]);
    assertArrayEquals(first, second);
    assertEquals("large enough to offload", decoded);
    assertEquals(1, store.blobs.size());
  }

  @Test
  public void testClaimCheckConverterRejectsUnknownPrefix() {
    ClaimCheckDataConverter converter =
        new ClaimCheckDataConverter(new RecordingBlobStore(), "bucket", 1);

    try {
      converter.fromData(new byte[] {0x7f}, String.class, String.class);
      fail("expected unknown prefix to fail");
    } catch (DataConverterException e) {
      assertTrue(e.getMessage().contains("unknown prefix"));
    }
  }

  @Test
  public void testClaimCheckConverterValidatesConstructorInputs() {
    expectIllegalArgument(() -> new ClaimCheckDataConverter(null, "bucket", 1));
    expectIllegalArgument(() -> new ClaimCheckDataConverter(new RecordingBlobStore(), " ", 1));
    expectIllegalArgument(
        () -> new ClaimCheckDataConverter(new RecordingBlobStore(), "bucket", -1));
  }

  @Test
  public void testLocalFsBlobStoreHashesUnsafeKeys() throws Exception {
    Path baseDir = temporaryFolder.newFolder("blobs").toPath();
    LocalFsBlobStore store = new LocalFsBlobStore(baseDir);
    byte[] data = new byte[] {1, 2, 3};

    store.put("../escape", data);
    store.put(".", data);
    store.put("bucket\\nested/key", data);

    assertArrayEquals(data, store.get("../escape"));
    assertArrayEquals(data, store.get("."));
    assertArrayEquals(data, store.get("bucket\\nested/key"));
    try (Stream<Path> files = Files.list(baseDir)) {
      assertEquals(3, files.filter(Files::isRegularFile).count());
    }
    try (Stream<Path> files = Files.list(baseDir)) {
      assertTrue(files.allMatch(path -> path.getFileName().toString().matches("[0-9a-f]{64}")));
    }
  }

  private static void expectIllegalArgument(Runnable runnable) {
    try {
      runnable.run();
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      // Expected.
    }
  }

  private static final class RecordingBlobStore implements BlobStore {
    final Map<String, byte[]> blobs = new LinkedHashMap<>();

    @Override
    public void put(String key, byte[] data) {
      blobs.put(key, data);
    }

    @Override
    public byte[] get(String key) throws IOException {
      byte[] data = blobs.get(key);
      if (data == null) {
        throw new IOException("missing key " + key);
      }
      return data;
    }
  }
}
