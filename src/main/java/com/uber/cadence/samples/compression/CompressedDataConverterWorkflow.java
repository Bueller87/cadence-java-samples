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
 * Demonstrates gzip-over-JSON compression as a Cadence {@code DataConverter}. The workflow itself
 * is unchanged from a plain Cadence workflow — the compression is applied transparently to every
 * input, output, and activity parameter by {@link CompressedJsonDataConverter}, which is wired in
 * at the worker by {@link CompressionWorker}.
 *
 * <p>The workflow takes no inputs and builds its own large payload internally so it can be started
 * from the Cadence CLI without bundling a custom converter into the caller.
 */
public final class CompressedDataConverterWorkflow {

  private CompressedDataConverterWorkflow() {}

  /** Task list polled by {@link CompressionWorker}. */
  public static final String TASK_LIST = "data-compression";

  /**
   * Registered workflow type, used for both {@code @WorkflowMethod} and CLI {@code workflow start}.
   */
  public static final String WORKFLOW_TYPE = "CompressedDataConverterWorkflow";

  // ---------------- POJOs ----------------

  /**
   * A complex data structure with nested objects and arrays designed to demonstrate compression
   * benefits. Fields are public + have no-arg constructors so the JSON data converter can serialize
   * and deserialize them.
   */
  public static final class LargePayload {
    public String id;
    public String name;
    public String description;
    public Map<String, String> metadata;
    public List<Item> items;
    public Config config;
    public List<HistoryEntry> history;
    public List<String> tags;
    public Statistics statistics;

    public LargePayload() {}
  }

  public static final class Item {
    public String itemId;
    public String title;
    public String description;
    public double price;
    public List<String> categories;
    public Map<String, String> attributes;
    public List<Review> reviews;
    public Inventory inventory;

    public Item() {}
  }

  public static final class Review {
    public String reviewId;
    public String userId;
    public int rating;
    public String comment;
    public int helpfulVotes;
    public int notHelpfulVotes;
    public String date;
    public boolean verifiedPurchase;
    public double score;

    public Review() {}
  }

  public static final class Inventory {
    public int quantity;
    public String location;
    public String lastUpdated;
    public String status;

    public Inventory() {}
  }

  public static final class Config {
    public String version;
    public String environment;
    public Map<String, String> settings;
    public List<String> features;
    public Limits limits;

    public Config() {}
  }

  public static final class Limits {
    public int maxItems;
    public int maxRequestsPerMinute;
    public int maxFileSizeMb;
    public int maxConcurrentUsers;
    public int timeoutSeconds;

    public Limits() {}
  }

  public static final class HistoryEntry {
    public String eventId;
    public String timestamp;
    public String eventType;
    public String userId;
    public Map<String, String> details;
    public String severity;

    public HistoryEntry() {}
  }

  public static final class Statistics {
    public int totalItems;
    public int totalUsers;
    public double averageRating;
    public double totalRevenue;
    public int activeOrders;
    public double completionRate;

    public Statistics() {}
  }

  // ---------------- Sample payload generator ----------------

  /**
   * Builds a sample large payload with realistic-looking, repetitive data so gzip has plenty to
   * compress.
   */
  public static LargePayload createLargePayload() {
    LargePayload p = new LargePayload();
    p.id = "large_payload_001";
    p.name = "Comprehensive Product Catalog";
    p.description =
        repeat(
            "This is a comprehensive product catalog containing thousands of items with detailed descriptions, specifications, and user reviews. Each item includes pricing information, inventory status, and customer feedback. The catalog is designed to provide complete information for customers making purchasing decisions. ",
            50);

    p.metadata = new LinkedHashMap<>();
    for (int i = 0; i < 30; i++) {
      p.metadata.put(
          "meta_key_" + i,
          repeat(
              "This is comprehensive metadata information with detailed descriptions and specifications. ",
              5));
    }

    p.items = new ArrayList<>(100);
    for (int i = 0; i < 100; i++) {
      Item it = new Item();
      it.itemId = "item_" + i;
      it.title = "High-Quality Product " + i + " with Advanced Features";
      it.description =
          repeat(
              "This is a premium product with exceptional quality and advanced features designed for professional use. It includes comprehensive documentation and support. ",
              10);
      it.price = 100.0 + i * 10 + (i % 100) / 100.0;
      it.categories = new ArrayList<>();
      it.categories.add("Electronics");
      it.categories.add("Professional");
      it.categories.add("Premium");
      it.categories.add("Advanced");

      it.attributes = new LinkedHashMap<>();
      for (int k = 0; k < 20; k++) {
        it.attributes.put(
            "attr_" + k,
            repeat(
                "This is a detailed attribute description with comprehensive information about the product specification. ",
                2));
      }

      it.reviews = new ArrayList<>(25);
      for (int j = 0; j < 25; j++) {
        Review r = new Review();
        r.reviewId = "review_" + i + "_" + j;
        r.userId = "user_" + j;
        r.rating = 1 + (j % 5);
        r.comment =
            repeat(
                "This is a detailed customer review with comprehensive feedback about the product quality, delivery experience, and overall satisfaction. The customer provides specific details about their experience. ",
                3);
        r.helpfulVotes = j * 2;
        r.notHelpfulVotes = j;
        r.date = "2024-01-15T10:30:00Z";
        r.verifiedPurchase = j % 2 == 0;
        r.score = (1 + (j % 5)) + (j % 10) / 10.0;
        it.reviews.add(r);
      }

      Inventory inv = new Inventory();
      inv.quantity = 100 + i;
      inv.location = "Warehouse " + (i % 5);
      inv.lastUpdated = "2024-01-15T10:30:00Z";
      inv.status = "In Stock";
      it.inventory = inv;
      p.items.add(it);
    }

    Config cfg = new Config();
    cfg.version = "2.1.0";
    cfg.environment = "production";
    cfg.settings = new LinkedHashMap<>();
    cfg.settings.put("cache_enabled", "true");
    cfg.settings.put("compression_level", "high");
    cfg.settings.put("timeout", "30s");
    cfg.settings.put("max_connections", "1000");
    cfg.settings.put("retry_attempts", "3");
    cfg.features = new ArrayList<>();
    cfg.features.add("advanced_search");
    cfg.features.add("real_time_updates");
    cfg.features.add("analytics");
    cfg.features.add("reporting");
    cfg.features.add("integration");
    Limits lim = new Limits();
    lim.maxItems = 10000;
    lim.maxRequestsPerMinute = 1000;
    lim.maxFileSizeMb = 100;
    lim.maxConcurrentUsers = 5000;
    lim.timeoutSeconds = 30;
    cfg.limits = lim;
    p.config = cfg;

    p.history = new ArrayList<>(50);
    for (int i = 0; i < 50; i++) {
      HistoryEntry h = new HistoryEntry();
      h.eventId = "event_" + i;
      h.timestamp = "2024-01-15T10:30:00Z";
      h.eventType = "system_update";
      h.userId = "admin_" + (i % 5);
      h.details = new LinkedHashMap<>();
      for (int j = 0; j < 10; j++) {
        h.details.put(
            "detail_" + j,
            repeat(
                "This is a detailed event description with comprehensive information about the system event and its impact. ",
                2));
      }
      h.severity = "medium";
      p.history.add(h);
    }

    p.tags = new ArrayList<>();
    p.tags.add("catalog");
    p.tags.add("products");
    p.tags.add("inventory");
    p.tags.add("analytics");
    p.tags.add("reporting");
    p.tags.add("integration");
    p.tags.add("api");
    p.tags.add("dashboard");

    Statistics stats = new Statistics();
    stats.totalItems = 10000;
    stats.totalUsers = 5000;
    stats.averageRating = 4.2;
    stats.totalRevenue = 1250000.50;
    stats.activeOrders = 250;
    stats.completionRate = 98.5;
    p.statistics = stats;

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
    LargePayload processLargePayload(LargePayload input);
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
      LargePayload input = createLargePayload();

      Workflow.getLogger(CompressedDataConverterWorkflow.class)
          .info("Large payload workflow started: id={}, items={}", input.id, input.items.size());

      LargePayload result = activities.processLargePayload(input);

      Workflow.getLogger(CompressedDataConverterWorkflow.class)
          .info(
              "Large payload workflow completed: id={}. All data was automatically gzip-compressed in Cadence history.",
              result.id);
      return result;
    }
  }

  public static final class ActivitiesImpl implements Activities {

    @Override
    public LargePayload processLargePayload(LargePayload input) {
      input.name = input.name + " (Processed)";
      if (input.statistics != null) {
        input.statistics.totalItems = input.items != null ? input.items.size() : 0;
      }
      return input;
    }
  }
}
