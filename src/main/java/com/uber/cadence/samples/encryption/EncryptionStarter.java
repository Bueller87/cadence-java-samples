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

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.internal.compatibility.Thrift2ProtoAdapter;
import com.uber.cadence.internal.compatibility.proto.serviceclient.IGrpcServiceStubs;
import com.uber.cadence.samples.common.SampleConstants;
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
      WorkflowClient client =
          WorkflowClient.newInstance(
              new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()),
              WorkflowClientOptions.newBuilder().setDomain(SampleConstants.DOMAIN).build());
      WorkflowOptions options =
          new WorkflowOptions.Builder()
              .setTaskList(EncryptedDataConverterWorkflow.TASK_LIST)
              .setExecutionStartToCloseTimeout(Duration.ofMinutes(1))
              .setWorkflowId("encryption-" + UUID.randomUUID())
              .build();

      EncryptedDataConverterWorkflow.WorkflowIface workflow =
          client.newWorkflowStub(EncryptedDataConverterWorkflow.WorkflowIface.class, options);

      WorkflowClient.start(workflow::run);
      System.out.println(
          "Started "
              + EncryptedDataConverterWorkflow.WORKFLOW_TYPE
              + " on task list \""
              + EncryptedDataConverterWorkflow.TASK_LIST
              + "\".");
      System.exit(0);
    } catch (RuntimeException e) {
      if (printHintIfDomainMissing(e)) {
        System.exit(1);
      }
      throw e;
    }
  }

  /**
   * Prints a copy-paste hint when the Cadence error indicates the sample domain has not been
   * registered.
   *
   * @return true if {@code t} was a missing-domain error and a hint was printed (caller should
   *     exit).
   */
  static boolean printHintIfDomainMissing(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      String m = c.getMessage();
      if (m != null && m.contains("Domain") && m.contains("does not exist")) {
        System.err.println();
        System.err.println(
            "Cadence reported that the domain \"" + SampleConstants.DOMAIN + "\" does not exist.");
        System.err.println("Register it once against your cluster, then run this again:");
        System.err.println();
        System.err.println(
            "  ./gradlew -q execute -PmainClass=com.uber.cadence.samples.common.RegisterDomain");
        System.err.println();
        System.err.println("Or with Cadence CLI:");
        System.err.println("  cadence --domain " + SampleConstants.DOMAIN + " domain register");
        System.err.println();
        return true;
      }
    }
    return false;
  }
}
