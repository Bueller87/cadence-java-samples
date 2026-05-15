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

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.internal.compatibility.Thrift2ProtoAdapter;
import com.uber.cadence.internal.compatibility.proto.serviceclient.IGrpcServiceStubs;
import com.uber.cadence.samples.common.SampleConstants;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;

/**
 * Hosts the gzip-compression sample worker. Constructs a {@link WorkflowClient} configured with
 * {@link CompressedJsonDataConverter} so every workflow input, output, and activity parameter is
 * transparently gzip-compressed in Cadence history. On startup it prints a stats banner showing the
 * before/after size of the sample payload so the benefit is visible at a glance.
 */
public final class CompressionWorker {

  private CompressionWorker() {}

  public static void main(String[] args) {
    DataConverter converter = new CompressedJsonDataConverter();
    WorkflowClient client =
        WorkflowClient.newInstance(
            new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()),
            WorkflowClientOptions.newBuilder()
                .setDomain(SampleConstants.DOMAIN)
                .setDataConverter(converter)
                .build());

    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(CompressedDataConverterWorkflow.TASK_LIST);
    worker.registerWorkflowImplementationTypes(CompressedDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new CompressedDataConverterWorkflow.ActivitiesImpl());
    factory.start();

    printCompressionStats(converter);

    System.out.println(
        "CompressionWorker listening on \""
            + CompressedDataConverterWorkflow.TASK_LIST
            + "\" (domain \""
            + SampleConstants.DOMAIN
            + "\").");

    Runtime.getRuntime().addShutdownHook(new Thread(factory::shutdown));
  }

  private static void printCompressionStats(DataConverter converter) {
    CompressedDataConverterWorkflow.LargePayload payload =
        CompressedDataConverterWorkflow.createLargePayload();
    byte[] originalJson = JsonDataConverter.getInstance().toData(payload);
    byte[] compressed = converter.toData(payload);
    int originalSize = originalJson == null ? 0 : originalJson.length;
    int compressedSize = compressed == null ? 0 : compressed.length;
    double pct = originalSize == 0 ? 0.0 : (1.0 - (double) compressedSize / originalSize) * 100.0;

    System.out.println();
    System.out.println("=== Compression Sample Statistics ===");
    System.out.printf(
        "Original JSON size:  %d bytes (%.2f KB)%n", originalSize, originalSize / 1024.0);
    System.out.printf(
        "Compressed size:     %d bytes (%.2f KB)%n", compressedSize, compressedSize / 1024.0);
    System.out.printf("Compression ratio:   %.2f%% reduction%n", pct);
    System.out.printf(
        "Space saved:         %d bytes (%.2f KB)%n",
        originalSize - compressedSize, (originalSize - compressedSize) / 1024.0);
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        SampleConstants.DOMAIN,
        CompressedDataConverterWorkflow.TASK_LIST,
        CompressedDataConverterWorkflow.WORKFLOW_TYPE);
    System.out.println("=====================================");
    System.out.println();
  }
}
