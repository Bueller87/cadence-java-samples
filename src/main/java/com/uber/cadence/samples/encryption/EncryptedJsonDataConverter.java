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

import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.converter.JsonDataConverter;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@link DataConverter} that JSON-encodes via {@link JsonDataConverter} and then encrypts with
 * AES-256-GCM.
 *
 * <p>Every workflow input, output, and activity parameter is encrypted before being written to
 * Cadence history. Without the key, payloads stored by the Cadence server are unreadable to
 * operators browsing workflow history. Logs, metrics, and search attributes are separate disclosure
 * surfaces and must be handled separately.
 *
 * <p>Output layout: {@code nonce(12 bytes) || ciphertext || tag(16 bytes)}. The random nonce means
 * the same plaintext produces different ciphertext on every call, which preserves semantic security
 * for repeated payloads. The GCM authentication tag ensures any ciphertext tampering is detected at
 * decode time.
 */
public final class EncryptedJsonDataConverter implements DataConverter {

  private static final DataConverter delegate = JsonDataConverter.getInstance();
  private static final String TRANSFORM = "AES/GCM/NoPadding";
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param keyBytes 32-byte AES-256 key. The caller is responsible for sourcing this from a secrets
   *     manager in production; see {@link EncryptionKeyLoader}.
   * @throws IllegalArgumentException if the key is not 32 bytes.
   */
  public EncryptedJsonDataConverter(byte[] keyBytes) {
    if (keyBytes == null || keyBytes.length != 32) {
      throw new IllegalArgumentException(
          "AES-256 key must be exactly 32 bytes, got " + (keyBytes == null ? 0 : keyBytes.length));
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
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
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance(TRANSFORM);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(jsonBytes);

      byte[] out = new byte[NONCE_BYTES + ciphertext.length];
      System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
      System.arraycopy(ciphertext, 0, out, NONCE_BYTES, ciphertext.length);
      return out;
    } catch (GeneralSecurityException e) {
      throw new DataConverterException("Failed to AES-256-GCM encrypt payload", e);
    }
  }

  @Override
  public <T> T fromData(byte[] content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    if (content == null || content.length == 0) {
      return delegate.fromData(content, valueClass, valueType);
    }
    return delegate.fromData(decrypt(content), valueClass, valueType);
  }

  @Override
  public Object[] fromDataArray(byte[] content, Type... valueTypes) throws DataConverterException {
    if (content == null || content.length == 0) {
      return delegate.fromDataArray(content, valueTypes);
    }
    return delegate.fromDataArray(decrypt(content), valueTypes);
  }

  private byte[] decrypt(byte[] content) throws DataConverterException {
    if (content.length < NONCE_BYTES) {
      throw new DataConverterException(
          "Ciphertext too short: " + content.length + " bytes (need at least " + NONCE_BYTES + ")",
          null);
    }
    try {
      byte[] nonce = new byte[NONCE_BYTES];
      System.arraycopy(content, 0, nonce, 0, NONCE_BYTES);
      Cipher cipher = Cipher.getInstance(TRANSFORM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      return cipher.doFinal(content, NONCE_BYTES, content.length - NONCE_BYTES);
    } catch (GeneralSecurityException e) {
      throw new DataConverterException("Failed to AES-256-GCM decrypt payload", e);
    }
  }
}
