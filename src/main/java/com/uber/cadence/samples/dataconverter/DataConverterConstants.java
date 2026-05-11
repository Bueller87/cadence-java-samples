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

import com.uber.cadence.samples.common.SampleConstants;

/**
 * Shared identifiers for the DataConverter samples.
 *
 * <p>Each of the three samples runs on its own task list so it can have its own {@code
 * DataConverter}. {@code DataConverter} is bound to a {@code WorkflowClient}, and each task list
 * maps to one worker built from one client; that is why one process needs three clients to host all
 * three samples.
 */
public final class DataConverterConstants {

  private DataConverterConstants() {}

  /** Cadence domain shared with the rest of the samples (registered via {@code RegisterDomain}). */
  public static final String DOMAIN = SampleConstants.DOMAIN;

  /** Task list for the gzip-compression sample worker. */
  public static final String TASK_LIST_COMPRESSION = "data-compression";

  /** Task list for the AES-256-GCM encryption sample worker. */
  public static final String TASK_LIST_ENCRYPTION = "data-encryption";

  /** Task list for the S3 / claim-check offload sample worker. */
  public static final String TASK_LIST_S3 = "data-s3";

  /** Registered workflow type for {@code CompressedDataConverterWorkflow}. */
  public static final String COMPRESSION_WORKFLOW_TYPE = "CompressedDataConverterWorkflow";

  /** Registered workflow type for {@code EncryptedDataConverterWorkflow}. */
  public static final String ENCRYPTION_WORKFLOW_TYPE = "EncryptedDataConverterWorkflow";

  /** Registered workflow type for {@code S3OffloadDataConverterWorkflow}. */
  public static final String S3_OFFLOAD_WORKFLOW_TYPE = "S3OffloadDataConverterWorkflow";

  /** Logical bucket / prefix embedded in S3-offload reference keys. */
  public static final String S3_BUCKET = "data-s3";

  /**
   * Payloads larger than this are offloaded to the BlobStore by {@link S3OffloadDataConverter}.
   * Cadence's default max payload is roughly 2 MB; the threshold is set intentionally low so the
   * demo workflow comfortably triggers offloading.
   */
  public static final int S3_DEFAULT_THRESHOLD_BYTES = 4096;
}
