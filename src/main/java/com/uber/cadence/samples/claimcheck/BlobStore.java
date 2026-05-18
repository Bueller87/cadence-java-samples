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

/**
 * Abstraction over any external object store (local filesystem, S3, GCS, Azure Blob, etc.).
 *
 * <p>{@link ClaimCheckDataConverter} uses this interface to store large payloads outside Cadence
 * history. The default implementation is {@link LocalFsBlobStore}, which writes to the system
 * temporary directory and requires no external services.
 */
public interface BlobStore {

  /** Stores {@code data} under {@code key}, overwriting any existing value. */
  void put(String key, byte[] data) throws IOException;

  /** Returns the bytes previously stored under {@code key}. */
  byte[] get(String key) throws IOException;
}
