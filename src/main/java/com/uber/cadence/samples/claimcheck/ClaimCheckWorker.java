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
 * Hosts the claim-check sample worker. Constructs a {@link WorkflowClient} configured with {@link
 * ClaimCheckDataConverter} backed by {@link LocalFsBlobStore} so payloads above the threshold are
 * stored on disk and replaced in Cadence history with a small reference. Swap in any real {@link
 * BlobStore} (S3, GCS, Azure Blob, MinIO — see comments in {@link ClaimCheckDataConverter}) to move
 * blobs to a remote object store without changing any workflow or activity code.
 */
public final class ClaimCheckWorker {

  private ClaimCheckWorker() {}

  public static void main(String[] args) {
    LocalFsBlobStore blobStore = new LocalFsBlobStore();
    DataConverter converter =
        new ClaimCheckDataConverter(
            blobStore,
            ClaimCheckDataConverterWorkflow.BLOB_BUCKET,
            ClaimCheckDataConverterWorkflow.DEFAULT_THRESHOLD_BYTES);
    WorkflowClient client =
        WorkflowClient.newInstance(
            new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()),
            WorkflowClientOptions.newBuilder()
                .setDomain(SampleConstants.DOMAIN)
                .setDataConverter(converter)
                .build());

    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(ClaimCheckDataConverterWorkflow.TASK_LIST);
    worker.registerWorkflowImplementationTypes(ClaimCheckDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new ClaimCheckDataConverterWorkflow.ActivitiesImpl());
    factory.start();

    printClaimCheckStats(blobStore);

    System.out.println(
        "ClaimCheckWorker listening on \""
            + ClaimCheckDataConverterWorkflow.TASK_LIST
            + "\" (domain \""
            + SampleConstants.DOMAIN
            + "\").");

    Runtime.getRuntime().addShutdownHook(new Thread(factory::shutdown));
  }

  private static void printClaimCheckStats(LocalFsBlobStore store) {
    ClaimCheckDataConverterWorkflow.LargePayload payload =
        ClaimCheckDataConverterWorkflow.createLargePayload();
    byte[] jsonBytes = JsonDataConverter.getInstance().toData(payload);
    int jsonSize = jsonBytes == null ? 0 : jsonBytes.length;
    // History footprint = 1 prefix byte + JSON envelope {"blobRef":"<bucket>/<sha256hex>"}.
    // SHA-256 hex digest is 64 chars; bucket + "/" + 64 hex chars.
    int cadenceBytes =
        1
            + ("{\"blobRef\":\""
                    + ClaimCheckDataConverterWorkflow.BLOB_BUCKET
                    + "/"
                    + repeatChar('a', 64)
                    + "\"}")
                .length();

    System.out.println();
    System.out.println("=== Claim-Check Sample Statistics ===");
    System.out.printf(
        "Full payload JSON size:    %d bytes (%.2f KB)%n", jsonSize, jsonSize / 1024.0);
    System.out.printf(
        "Stored in BlobStore:       %d bytes (%.2f KB)%n", jsonSize, jsonSize / 1024.0);
    System.out.printf(
        "Stored in Cadence history: %d bytes (claim-check reference only)%n", cadenceBytes);
    System.out.printf(
        "Reduction in Cadence:      %.1f%%%n",
        jsonSize == 0 ? 0.0 : 100.0 * (1.0 - (double) cadenceBytes / jsonSize));
    System.out.printf("BlobStore location:        %s%n", store.baseDir());
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        SampleConstants.DOMAIN,
        ClaimCheckDataConverterWorkflow.TASK_LIST,
        ClaimCheckDataConverterWorkflow.WORKFLOW_TYPE);
    System.out.println("=====================================");
    System.out.println();
  }

  private static String repeatChar(char c, int n) {
    char[] buf = new char[n];
    java.util.Arrays.fill(buf, c);
    return new String(buf);
  }
}
