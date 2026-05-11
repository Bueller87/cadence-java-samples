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
 * Starts {@link EncryptedDataConverterWorkflow} (async, fire-and-forget).
 *
 * <p>The workflow takes no inputs and generates its own payload, so this starter does not need the
 * encryption key — the worker owns the key. The same effect can be achieved from the Cadence CLI
 * via:
 *
 * <pre>
 * cadence --domain samples-domain \
 *   workflow start \
 *   --workflow_type EncryptedDataConverterWorkflow \
 *   --tl data-encryption \
 *   --et 60
 * </pre>
 */
public final class EncryptionStarter {

  private EncryptionStarter() {}

  public static void main(String[] args) {
    try {
      WorkflowClient client = DataConverterSupport.newWorkflowClient();
      WorkflowOptions options =
          new WorkflowOptions.Builder()
              .setTaskList(DataConverterConstants.TASK_LIST_ENCRYPTION)
              .setExecutionStartToCloseTimeout(Duration.ofMinutes(1))
              .setWorkflowId("encryption-" + UUID.randomUUID())
              .build();

      EncryptedDataConverterWorkflow.WorkflowIface workflow =
          client.newWorkflowStub(EncryptedDataConverterWorkflow.WorkflowIface.class, options);

      WorkflowClient.start(workflow::run);
      System.out.println(
          "Started EncryptedDataConverterWorkflow on task list \""
              + DataConverterConstants.TASK_LIST_ENCRYPTION
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
