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

package com.uber.cadence.samples.encryption;

import java.nio.charset.StandardCharsets;

/**
 * Loads the 32-byte AES-256 key for {@link EncryptedJsonDataConverter}.
 *
 * <p>Reads the key from the {@code CADENCE_ENCRYPTION_KEY} environment variable as 64 hex
 * characters (32 bytes). If the env var is unset, falls back to a hardcoded demo key with a
 * warning. If the env var is set but invalid, throws — silently falling back to the public demo key
 * when the user clearly intended their own key would be a security hole.
 */
public final class EncryptionKeyLoader {

  private EncryptionKeyLoader() {}

  /** Hardcoded 32-byte key used ONLY when {@code CADENCE_ENCRYPTION_KEY} is unset. */
  static final byte[] DEMO_ENCRYPTION_KEY =
      "cadence-demo-key-NOT-FOR-PROD!!!".getBytes(StandardCharsets.US_ASCII);

  /**
   * Returns a 32-byte AES-256 key from {@code CADENCE_ENCRYPTION_KEY} or the demo key.
   *
   * @throws IllegalStateException if the env var is set but not valid hex or not 32 bytes long.
   */
  public static byte[] loadEncryptionKey() {
    String hexKey = System.getenv("CADENCE_ENCRYPTION_KEY");
    if (hexKey == null || hexKey.isEmpty()) {
      System.out.println("WARNING: CADENCE_ENCRYPTION_KEY not set. Using hardcoded demo key.");
      System.out.println("WARNING: DO NOT USE THE DEMO KEY IN PRODUCTION.");
      return DEMO_ENCRYPTION_KEY.clone();
    }
    byte[] key;
    try {
      key = hexDecode(hexKey);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "CADENCE_ENCRYPTION_KEY is not valid hex: " + e.getMessage(), e);
    }
    if (key.length != 32) {
      throw new IllegalStateException(
          "CADENCE_ENCRYPTION_KEY must be exactly 64 hex chars (32 bytes), got "
              + hexKey.length()
              + " hex chars ("
              + key.length
              + " bytes)");
    }
    return key;
  }

  private static byte[] hexDecode(String s) {
    int len = s.length();
    if ((len & 1) != 0) {
      throw new IllegalArgumentException("odd-length hex string");
    }
    byte[] out = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      int hi = Character.digit(s.charAt(i), 16);
      int lo = Character.digit(s.charAt(i + 1), 16);
      if (hi < 0 || lo < 0) {
        throw new IllegalArgumentException("non-hex character at offset " + i);
      }
      out[i / 2] = (byte) ((hi << 4) | lo);
    }
    return out;
  }
}
