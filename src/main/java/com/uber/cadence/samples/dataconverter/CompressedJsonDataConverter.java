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

import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.converter.JsonDataConverter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * {@link DataConverter} that serializes values to JSON via {@link JsonDataConverter} and then
 * compresses the resulting bytes with gzip.
 *
 * <p>For repetitive JSON payloads this typically achieves 60-80% size reduction, lowering storage
 * cost and bandwidth without changing any workflow or activity code. Apply by setting it on the
 * {@code WorkflowClientOptions} used by both the worker and any client that triggers the workflow.
 */
public final class CompressedJsonDataConverter implements DataConverter {

  private static final DataConverter delegate = JsonDataConverter.getInstance();

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
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
        gzip.write(jsonBytes);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new DataConverterException("Failed to gzip-compress JSON payload", e);
    }
  }

  @Override
  public <T> T fromData(byte[] content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    if (content == null || content.length == 0) {
      return delegate.fromData(content, valueClass, valueType);
    }
    return delegate.fromData(decompress(content), valueClass, valueType);
  }

  @Override
  public Object[] fromDataArray(byte[] content, Type... valueTypes) throws DataConverterException {
    if (content == null || content.length == 0) {
      return delegate.fromDataArray(content, valueTypes);
    }
    return delegate.fromDataArray(decompress(content), valueTypes);
  }

  private static byte[] decompress(byte[] content) throws DataConverterException {
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[4096];
      int read;
      while ((read = gzip.read(buf)) != -1) {
        out.write(buf, 0, read);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new DataConverterException("Failed to gunzip payload", e);
    }
  }
}
