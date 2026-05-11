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
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.internal.compatibility.Thrift2ProtoAdapter;
import com.uber.cadence.internal.compatibility.proto.serviceclient.IGrpcServiceStubs;

/** Shared client factory and friendly errors for the DataConverter sample starters and worker. */
final class DataConverterSupport {

  private DataConverterSupport() {}

  /**
   * Builds a WorkflowClient with the given DataConverter on the configured domain. The Worker
   * derived from this client will use the same converter for all serialization.
   */
  static WorkflowClient newWorkflowClient(DataConverter dataConverter) {
    WorkflowClientOptions.Builder builder =
        WorkflowClientOptions.newBuilder().setDomain(DataConverterConstants.DOMAIN);
    if (dataConverter != null) {
      builder.setDataConverter(dataConverter);
    }
    return WorkflowClient.newInstance(
        new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()), builder.build());
  }

  /** Builds a WorkflowClient using the default JSON DataConverter. */
  static WorkflowClient newWorkflowClient() {
    return newWorkflowClient(null);
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
            "Cadence reported that the domain \""
                + DataConverterConstants.DOMAIN
                + "\" does not exist.");
        System.err.println("Register it once against your cluster, then run this again:");
        System.err.println();
        System.err.println(
            "  ./gradlew -q execute -PmainClass=com.uber.cadence.samples.common.RegisterDomain");
        System.err.println();
        System.err.println("Or with Cadence CLI:");
        System.err.println("  cadence --domain " + DataConverterConstants.DOMAIN + " domain register");
        System.err.println();
        return true;
      }
    }
    return false;
  }
}
