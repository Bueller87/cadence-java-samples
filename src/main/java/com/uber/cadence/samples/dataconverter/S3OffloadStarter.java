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
import com.uber.cadence.client.WorkflowOptions;
import java.time.Duration;
import java.util.UUID;

/**
 * Starts {@link S3OffloadDataConverterWorkflow} (async, fire-and-forget).
 *
 * <p>The workflow takes no inputs and generates its own payload, so this starter does not need to
 * use the matching {@link S3OffloadDataConverter}. The same effect can be achieved from the
 * Cadence CLI via:
 *
 * <pre>
 * cadence --domain samples-domain \
 *   workflow start \
 *   --workflow_type S3OffloadDataConverterWorkflow \
 *   --tl data-s3 \
 *   --et 60
 * </pre>
 */
public final class S3OffloadStarter {

  private S3OffloadStarter() {}

  public static void main(String[] args) {
    try {
      WorkflowClient client = DataConverterSupport.newWorkflowClient();
      WorkflowOptions options =
          new WorkflowOptions.Builder()
              .setTaskList(DataConverterConstants.TASK_LIST_S3)
              .setExecutionStartToCloseTimeout(Duration.ofMinutes(1))
              .setWorkflowId("s3-offload-" + UUID.randomUUID())
              .build();

      S3OffloadDataConverterWorkflow.WorkflowIface workflow =
          client.newWorkflowStub(S3OffloadDataConverterWorkflow.WorkflowIface.class, options);

      WorkflowClient.start(workflow::run);
      System.out.println(
          "Started S3OffloadDataConverterWorkflow on task list \""
              + DataConverterConstants.TASK_LIST_S3
              + "\".");
      System.exit(0);
    } catch (RuntimeException e) {
      if (DataConverterSupport.printHintIfDomainMissing(e)) {
        System.exit(1);
      }
      throw e;
    }
  }
}
