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

package com.uber.cadence.samples.compression;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.uber.cadence.converter.DataConverterException;
import org.junit.Test;

public class CompressedJsonDataConverterTest {

  @Test
  public void testCompressedConverterRoundTrip() {
    CompressedJsonDataConverter converter = new CompressedJsonDataConverter();
    CompressedDataConverterWorkflow.LargePayload payload =
        CompressedDataConverterWorkflow.createLargePayload();

    byte[] encoded = converter.toData(payload);
    CompressedDataConverterWorkflow.LargePayload decoded =
        converter.fromData(
            encoded,
            CompressedDataConverterWorkflow.LargePayload.class,
            CompressedDataConverterWorkflow.LargePayload.class);

    assertEquals(payload.id, decoded.id);
    assertEquals(payload.name, decoded.name);
    assertEquals(payload.items.size(), decoded.items.size());
    assertEquals(payload.history.size(), decoded.history.size());
  }

  @Test
  public void testCompressedConverterRejectsMalformedPayload() {
    CompressedJsonDataConverter converter = new CompressedJsonDataConverter();

    try {
      converter.fromData(new byte[] {1, 2, 3}, String.class, String.class);
      fail("expected malformed gzip payload to fail");
    } catch (DataConverterException e) {
      assertTrue(e.getMessage().contains("gunzip"));
    }
  }

  @Test
  public void testCompressedConverterRejectsPayloadAboveLimit() {
    CompressedJsonDataConverter encoder = new CompressedJsonDataConverter();
    CompressedJsonDataConverter decoder = new CompressedJsonDataConverter(8);
    byte[] encoded = encoder.toData("this string inflates beyond the configured limit");

    try {
      decoder.fromData(encoded, String.class, String.class);
      fail("expected oversized decompressed payload to fail");
    } catch (DataConverterException e) {
      assertTrue(e.getMessage().contains("maximum size"));
    }
  }
}
