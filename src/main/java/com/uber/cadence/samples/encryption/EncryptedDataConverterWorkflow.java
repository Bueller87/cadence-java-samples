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

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Demonstrates AES-256-GCM encryption as a Cadence {@code DataConverter}. Every workflow input,
 * output, and activity parameter is encrypted before being written to Cadence history. Without the
 * key, payloads in workflow history are unreadable to anyone browsing history — including Cadence
 * operators. Application logs, metrics, and search attributes are not encrypted by a DataConverter.
 *
 * <p>The workflow takes no inputs and builds its own sensitive payload internally so it can be
 * started from the Cadence CLI without bundling the encryption key into the caller.
 */
public final class EncryptedDataConverterWorkflow {

  private EncryptedDataConverterWorkflow() {}

  /** Task list polled by {@link EncryptionWorker}. */
  public static final String TASK_LIST = "data-encryption";

  /**
   * Registered workflow type, used for both {@code @WorkflowMethod} and CLI {@code workflow start}.
   */
  public static final String WORKFLOW_TYPE = "EncryptedDataConverterWorkflow";

  // ---------------- POJOs ----------------

  /** PII / PHI-style record that must be encrypted in workflow history. */
  public static final class SensitiveCustomerRecord {
    public String customerId;
    public String fullName;
    public String email;
    public String ssn;
    public String creditCardNumber;
    public String billingAddress;
    public String medicalNotes;
    public String diagnosisCode;
    public String prescriptions;
    public String insuranceId;
    public String processedBy;

    public SensitiveCustomerRecord() {}
  }

  /** Builds a sample customer record with realistic-looking PII and PHI fields. */
  public static SensitiveCustomerRecord createSensitiveCustomerRecord() {
    SensitiveCustomerRecord r = new SensitiveCustomerRecord();
    r.customerId = "cust_8a7f3b2e";
    r.fullName = "Jane A. Doe";
    r.email = "jane.doe@example.com";
    r.ssn = "123-45-6789";
    r.creditCardNumber = "4111-1111-1111-1111";
    r.billingAddress = "1234 Elm Street, Springfield, IL 62701";
    r.medicalNotes =
        "Patient presents with hypertension and type-2 diabetes. Advised dietary changes and "
            + "increased physical activity. Follow-up scheduled in 3 months.";
    r.diagnosisCode = "I10, E11.9";
    r.prescriptions = "Lisinopril 10mg once daily; Metformin 500mg twice daily";
    r.insuranceId = "INS-987654321";
    r.processedBy = "workflow-processor-v2";
    return r;
  }

  // ---------------- Workflow + activity ----------------

  public interface WorkflowIface {

    @WorkflowMethod(
      name = WORKFLOW_TYPE,
      executionStartToCloseTimeoutSeconds = 60,
      taskList = TASK_LIST
    )
    SensitiveCustomerRecord run();
  }

  public interface Activities {

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
    SensitiveCustomerRecord processCustomerRecord(SensitiveCustomerRecord record);
  }

  public static final class WorkflowImpl implements WorkflowIface {

    private final Activities activities =
        Workflow.newActivityStub(
            Activities.class,
            new ActivityOptions.Builder()
                .setScheduleToStartTimeout(Duration.ofMinutes(1))
                .setStartToCloseTimeout(Duration.ofMinutes(1))
                .build());

    @Override
    public SensitiveCustomerRecord run() {
      SensitiveCustomerRecord record = createSensitiveCustomerRecord();

      Workflow.getLogger(EncryptedDataConverterWorkflow.class)
          .info(
              "Encryption workflow started: customer_id={}. All PII/PHI will be encrypted before storage.",
              record.customerId);

      SensitiveCustomerRecord result = activities.processCustomerRecord(record);

      Workflow.getLogger(EncryptedDataConverterWorkflow.class)
          .info(
              "Encryption workflow completed: customer_id={}. PII/PHI was automatically AES-256-GCM encrypted/decrypted.",
              result.customerId);
      return result;
    }
  }

  public static final class ActivitiesImpl implements Activities {

    @Override
    public SensitiveCustomerRecord processCustomerRecord(SensitiveCustomerRecord record) {
      record.processedBy = record.processedBy + " (Encrypted)";
      return record;
    }
  }
}
