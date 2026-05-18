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

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates the claim-check pattern: payloads larger than the configured threshold are stored in
 * an external {@link BlobStore} and only a small reference travels through Cadence history.
 *
 * <p>The workflow takes no inputs and builds a payload well above the threshold internally so it
 * can be started from the Cadence CLI and every run exercises the offload path.
 */
public final class ClaimCheckDataConverterWorkflow {

  private ClaimCheckDataConverterWorkflow() {}

  /** Task list polled by {@link ClaimCheckWorker}. */
  public static final String TASK_LIST = "data-claimcheck";

  /**
   * Registered workflow type, used for both {@code @WorkflowMethod} and CLI {@code workflow start}.
   */
  public static final String WORKFLOW_TYPE = "ClaimCheckDataConverterWorkflow";

  /** Logical bucket / prefix embedded in claim-check reference keys. */
  public static final String BLOB_BUCKET = "claimcheck-blobs";

  /**
   * Payloads larger than this are offloaded to the BlobStore by {@link ClaimCheckDataConverter}.
   * Cadence's default max payload is roughly 2 MB; the threshold is set intentionally low so the
   * demo workflow comfortably triggers offloading.
   */
  public static final int DEFAULT_THRESHOLD_BYTES = 4096;

  // ---------------- POJOs ----------------

  public static final class LargePayload {
    public String jobId;
    public String description;
    public List<DataPoint> dataPoints;
    public Map<String, String> metadata;
    public String processedBy;

    public LargePayload() {}
  }

  public static final class DataPoint {
    public String timestamp;
    public String metric;
    public double value;
    public String tags;

    public DataPoint() {}
  }

  /**
   * Builds a payload comfortably larger than {@link #DEFAULT_THRESHOLD_BYTES} so every workflow run
   * triggers an offload.
   */
  public static LargePayload createLargePayload() {
    LargePayload p = new LargePayload();
    p.jobId = "batch-job-20240115-001";
    p.description =
        repeat(
            "Large telemetry batch job containing sensor readings from the production cluster. ",
            10);

    p.dataPoints = new ArrayList<>(200);
    for (int i = 0; i < 200; i++) {
      DataPoint dp = new DataPoint();
      dp.timestamp = String.format("2024-01-15T%02d:30:00Z", i % 24);
      dp.metric = String.format("telemetry.sensor_%03d.temperature", i);
      dp.value = 20.0 + (i % 30) / 10.0;
      dp.tags = String.format("region=us-east-1,host=node-%03d,env=production", i % 10);
      p.dataPoints.add(dp);
    }

    p.metadata = new LinkedHashMap<>();
    for (int i = 0; i < 20; i++) {
      p.metadata.put(String.format("batch_key_%02d", i), repeat("value-data-", 5));
    }
    p.processedBy = "claimcheck-worker-v1";
    return p;
  }

  private static String repeat(String s, int n) {
    StringBuilder sb = new StringBuilder(s.length() * n);
    for (int i = 0; i < n; i++) {
      sb.append(s);
    }
    return sb.toString();
  }

  // ---------------- Workflow + activity ----------------

  public interface WorkflowIface {

    @WorkflowMethod(
      name = WORKFLOW_TYPE,
      executionStartToCloseTimeoutSeconds = 60,
      taskList = TASK_LIST
    )
    LargePayload run();
  }

  public interface Activities {

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 60)
    LargePayload processPayload(LargePayload payload);
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
    public LargePayload run() {
      LargePayload payload = createLargePayload();

      Workflow.getLogger(ClaimCheckDataConverterWorkflow.class)
          .info(
              "Claim-check workflow started: job_id={}, data_points={}. Payload will be offloaded; only a reference travels through Cadence history.",
              payload.jobId,
              payload.dataPoints.size());

      LargePayload result = activities.processPayload(payload);

      Workflow.getLogger(ClaimCheckDataConverterWorkflow.class)
          .info(
              "Claim-check workflow completed: job_id={}. Payload was transparently offloaded and retrieved via the BlobStore.",
              result.jobId);
      return result;
    }
  }

  public static final class ActivitiesImpl implements Activities {

    @Override
    public LargePayload processPayload(LargePayload payload) {
      payload.processedBy = payload.processedBy + " (Processed)";
      return payload;
    }
  }
}
