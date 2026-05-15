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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.testing.TestEnvironmentOptions;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import java.time.Duration;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;

public class EncryptedJsonDataConverterTest {

  private TestWorkflowEnvironment testEnv;

  @After
  public void tearDown() {
    if (testEnv != null) {
      testEnv.close();
    }
  }

  @Test
  public void testEncryptedConverterRoundTripAndRandomNonce() {
    EncryptedJsonDataConverter converter =
        new EncryptedJsonDataConverter(EncryptionKeyLoader.DEMO_ENCRYPTION_KEY);
    EncryptedDataConverterWorkflow.SensitiveCustomerRecord record =
        EncryptedDataConverterWorkflow.createSensitiveCustomerRecord();

    byte[] first = converter.toData(record);
    byte[] second = converter.toData(record);

    assertFalse(Arrays.equals(first, second));
    EncryptedDataConverterWorkflow.SensitiveCustomerRecord decoded =
        converter.fromData(
            first,
            EncryptedDataConverterWorkflow.SensitiveCustomerRecord.class,
            EncryptedDataConverterWorkflow.SensitiveCustomerRecord.class);
    assertEquals(record.customerId, decoded.customerId);
    assertEquals(record.ssn, decoded.ssn);
    assertEquals(record.medicalNotes, decoded.medicalNotes);
  }

  @Test
  public void testEncryptedConverterRejectsShortCiphertext() {
    EncryptedJsonDataConverter converter =
        new EncryptedJsonDataConverter(EncryptionKeyLoader.DEMO_ENCRYPTION_KEY);

    try {
      converter.fromData(new byte[] {1, 2, 3}, String.class, String.class);
      fail("expected short ciphertext to fail");
    } catch (DataConverterException e) {
      assertTrue(e.getMessage().contains("Ciphertext too short"));
    }
  }

  @Test
  public void testEncryptedConverterWorksInWorkflowEnvironment() {
    EncryptedJsonDataConverter converter =
        new EncryptedJsonDataConverter(EncryptionKeyLoader.DEMO_ENCRYPTION_KEY);
    TestEnvironmentOptions options =
        new TestEnvironmentOptions.Builder()
            .setWorkflowClientOptions(
                WorkflowClientOptions.newBuilder().setDataConverter(converter).build())
            .build();
    testEnv = TestWorkflowEnvironment.newInstance(options);
    Worker worker = testEnv.newWorker(EncryptedDataConverterWorkflow.TASK_LIST);
    worker.registerWorkflowImplementationTypes(EncryptedDataConverterWorkflow.WorkflowImpl.class);
    worker.registerActivitiesImplementations(new EncryptedDataConverterWorkflow.ActivitiesImpl());
    testEnv.start();

    WorkflowClient workflowClient =
        testEnv.newWorkflowClient(
            WorkflowClientOptions.newBuilder().setDataConverter(converter).build());
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(EncryptedDataConverterWorkflow.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofMinutes(1))
            .build();
    EncryptedDataConverterWorkflow.WorkflowIface workflow =
        workflowClient.newWorkflowStub(
            EncryptedDataConverterWorkflow.WorkflowIface.class, workflowOptions);

    EncryptedDataConverterWorkflow.SensitiveCustomerRecord result = workflow.run();

    assertEquals("cust_8a7f3b2e", result.customerId);
    assertEquals("workflow-processor-v2 (Encrypted)", result.processedBy);
  }
}
