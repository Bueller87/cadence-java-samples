# S3 Offload (Claim-Check) DataConverter Sample

A custom Cadence [`DataConverter`](../../../../../../../../README.md) that implements the **claim-check pattern**: payloads larger than a configurable threshold are stored in an external `BlobStore` (S3 / GCS / local disk) and only a small reference travels through Cadence workflow history.

This solves Cadence's per-payload size limits (~2 MB) for workflows that pass very large datasets, and lowers history storage cost for long-running workflows that pass large repeatable data.

- **Task list:** `data-s3`
- **Workflow type:** `S3OffloadDataConverterWorkflow`
- **Default threshold:** 4 KB (deliberately low so the demo always offloads)
- **Default backing store:** [`LocalFsBlobStore`](LocalFsBlobStore.java) writing to `${java.io.tmpdir}/cadence-java-samples-data-s3/`

## Prerequisites

1. Cadence server running (e.g. Docker Compose from the [Cadence repo](https://github.com/uber/cadence)).
2. From the repo root, build: `./gradlew build`.

### Register the domain (required once per cluster)

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.common.RegisterDomain
```

Or with the Cadence CLI:

```bash
cadence --domain samples-domain domain register
```

## Run the worker (terminal 1)

The worker prints an S3-offload statistics banner showing how much was offloaded to the blob store vs how little ends up in Cadence history, then begins polling the `data-s3` task list:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.s3offload.S3OffloadWorker
```

## Start a workflow (terminal 2)

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.s3offload.S3OffloadStarter
```

Or from the Cadence CLI:

```bash
cadence --domain samples-domain \
  workflow start \
  --workflow_type S3OffloadDataConverterWorkflow \
  --tl data-s3 \
  --et 60
```

## How it works

- `toData`: JSON-encode the arguments with the standard `JsonDataConverter`. If the resulting bytes are at or below the threshold, write `0x00 || json` and return inline. Otherwise compute a SHA-256 of the bytes, `PUT` to the blob store under `<bucket>/<sha256hex>`, and return `0x01 || json({"s3Ref":"<bucket>/<sha256hex>"})`. Using the content hash as the key makes `toData` idempotent across Cadence workflow replays.
- `fromData` / `fromDataArray`: read the 1-byte prefix; inline payloads pass straight to `JsonDataConverter`, offloaded payloads first fetch the blob via `BlobStore.get`.
- Cleanup: this sample does not delete blobs after the workflow completes. In production, use S3 object lifecycle policies to expire old blobs automatically.

## Swapping `LocalFsBlobStore` for real S3

The header comment in [`S3OffloadDataConverter.java`](S3OffloadDataConverter.java) sketches an `S3BlobStore` implementation using AWS SDK v2:

1. Add `software.amazon.awssdk:s3:2.25.0` to `build.gradle`.
2. Implement `BlobStore` against `software.amazon.awssdk.services.s3.S3Client`.
3. Replace `new LocalFsBlobStore()` with `new S3BlobStore("my-bucket", "us-east-1")` in [`S3OffloadWorker`](S3OffloadWorker.java).
4. Provide credentials via standard AWS env vars or an IAM instance role.

Point the SDK at LocalStack or MinIO for local testing without a real AWS account.

## Source layout

| File | Purpose |
|------|---------|
| [`BlobStore.java`](BlobStore.java) | Two-method abstraction over any object store |
| [`LocalFsBlobStore.java`](LocalFsBlobStore.java) | Zero-config implementation writing to the temp dir |
| [`S3OffloadDataConverter.java`](S3OffloadDataConverter.java) | The custom `DataConverter`; also contains the S3 stub |
| [`S3OffloadDataConverterWorkflow.java`](S3OffloadDataConverterWorkflow.java) | Workflow + activity + sample `S3LargePayload` POJOs and generator |
| [`S3OffloadWorker.java`](S3OffloadWorker.java) | Worker main; wires the converter into `WorkflowClientOptions` and prints the stats banner |
| [`S3OffloadStarter.java`](S3OffloadStarter.java) | Thin async starter |
