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
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.internal.compatibility.Thrift2ProtoAdapter;
import com.uber.cadence.internal.compatibility.proto.serviceclient.IGrpcServiceStubs;
import com.uber.cadence.samples.common.SampleConstants;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;

/**
 * Hosts the AES-256-GCM encryption sample worker. Constructs a {@link WorkflowClient} configured
 * with {@link EncryptedJsonDataConverter} so every workflow input, output, and activity parameter
 * is transparently encrypted before Cadence history sees it. The encryption key comes from {@link
 * EncryptionKeyLoader} (env var {@code CADENCE_ENCRYPTION_KEY}, or a hardcoded demo key with a
 * warning).
 */
public final class EncryptionWorker {

  private EncryptionWorker() {}

  public static void main(String[] args) {
    DataConverter converter =
        new EncryptedJsonDataConverter(EncryptionKeyLoader.loadEncryptionKey());
    WorkflowClient client =
        WorkflowClient.newInstance(
            new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()),
            WorkflowClientOptions.newBuilder()
                .setDomain(SampleConstants.DOMAIN)
                .setDataConverter(converter)
                .build());

    WorkerFactory factory = WorkerFactory.newInstance(client);
    Worker worker = factory.newWorker(EncryptedDataConverterWorkflow.TASK_LIST);
    worker.registerWorkflowImplementationTypes(EncryptedDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new EncryptedDataConverterWorkflow.ActivitiesImpl());
    factory.start();

    printEncryptionStats(converter);

    System.out.println(
        "EncryptionWorker listening on \""
            + EncryptedDataConverterWorkflow.TASK_LIST
            + "\" (domain \""
            + SampleConstants.DOMAIN
            + "\").");

    Runtime.getRuntime().addShutdownHook(new Thread(factory::shutdown));
  }

  private static void printEncryptionStats(DataConverter converter) {
    EncryptedDataConverterWorkflow.SensitiveCustomerRecord record =
        EncryptedDataConverterWorkflow.createSensitiveCustomerRecord();
    byte[] plaintext = JsonDataConverter.getInstance().toData(record);
    byte[] ciphertext = converter.toData(record);
    int plaintextSize = plaintext == null ? 0 : plaintext.length;
    int ciphertextSize = ciphertext == null ? 0 : ciphertext.length;
    String preview = ciphertext == null ? "" : hexPreview(ciphertext, 40);

    System.out.println();
    System.out.println("=== Encryption Sample Statistics ===");
    System.out.printf("Plaintext JSON size:  %d bytes%n", plaintextSize);
    System.out.printf(
        "Encrypted payload:    %d bytes (growth: %d bytes vs plaintext JSON)%n",
        ciphertextSize, ciphertextSize - plaintextSize);
    System.out.printf("Ciphertext preview:   %s%n", preview);
    System.out.printf(
        "Start workflow: cadence --domain %s workflow start --tl %s --workflow_type %s --et 60%n",
        SampleConstants.DOMAIN,
        EncryptedDataConverterWorkflow.TASK_LIST,
        EncryptedDataConverterWorkflow.WORKFLOW_TYPE);
    System.out.println("====================================");
    System.out.println();
  }

  private static String hexPreview(byte[] data, int byteLimit) {
    int len = Math.min(byteLimit, data.length);
    StringBuilder sb = new StringBuilder(len * 2 + 3);
    for (int i = 0; i < len; i++) {
      sb.append(String.format("%02x", data[i] & 0xff));
    }
    if (data.length > byteLimit) {
      sb.append("...");
    }
    return sb.toString();
  }
}
