# Claim-Check DataConverter Sample

A custom Cadence [`DataConverter`](../../../../../../../../README.md) that implements the **[claim-check pattern](https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html)**: payloads larger than a configurable threshold are stored in an external `BlobStore` (S3, GCS, Azure Blob, MinIO, local disk, etc.) and only a small reference travels through Cadence workflow history.

This solves Cadence's per-payload size limits (~2 MB) for workflows that pass very large datasets, and lowers history storage cost for long-running workflows that pass large repeatable data.

- **Task list:** `data-claimcheck`
- **Workflow type:** `ClaimCheckDataConverterWorkflow`
- **Default threshold:** 4 KB (deliberately low so the demo always offloads)
- **Default backing store:** [`LocalFsBlobStore`](LocalFsBlobStore.java) writing to `${java.io.tmpdir}/cadence-java-samples-claimcheck/`

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

The worker prints a claim-check statistics banner showing how much was offloaded to the blob store vs how little ends up in Cadence history, then begins polling the `data-claimcheck` task list:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.claimcheck.ClaimCheckWorker
```

## Start a workflow (terminal 2)

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.claimcheck.ClaimCheckStarter
```

Or from the Cadence CLI:

```bash
cadence --domain samples-domain \
  workflow start \
  --workflow_type ClaimCheckDataConverterWorkflow \
  --tl data-claimcheck \
  --et 60
```

## How it works

- `toData`: JSON-encode the arguments with the standard `JsonDataConverter`. If the resulting bytes are at or below the threshold, write `0x00 || json` and return inline. Otherwise compute a SHA-256 of the bytes, `PUT` to the blob store under `<bucket>/<sha256hex>`, and return `0x01 || json({"blobRef":"<bucket>/<sha256hex>"})`. Using the content hash as the key makes `toData` idempotent across Cadence workflow replays.
- `fromData` / `fromDataArray`: read the 1-byte prefix; inline payloads pass straight to `JsonDataConverter`, offloaded payloads first fetch the blob via `BlobStore.get`.
- Cleanup: this sample does not delete blobs after the workflow completes. In production, use the backing object store's lifecycle policies (S3 / GCS / Azure Blob lifecycle management) to expire old blobs automatically.

> Note on the wire format: the `blobRef` field name is persisted in Cadence workflow history. In a real deployment, treat the envelope JSON as a versioned wire format — renaming the field later would break replay of in-flight workflows. Either pin the name forever or include a `version` field from day one.

## Swapping `LocalFsBlobStore` for a real object store

The DataConverter is storage-agnostic: any class that implements `BlobStore` (two methods, `put` and `get`) will work. Swap `new LocalFsBlobStore()` in [`ClaimCheckWorker`](ClaimCheckWorker.java) for your own impl and the workflow/activity code stays the same. The header comment in [`ClaimCheckDataConverter.java`](ClaimCheckDataConverter.java) sketches an `S3BlobStore` using AWS SDK v2; brief pointers for other backends:

| Backend | Dependency | Notes |
|---------|------------|-------|
| AWS S3 | `software.amazon.awssdk:s3:2.25.0` | Reference sketch in the converter's header comment. Credentials via standard AWS env vars or IAM role. |
| Google Cloud Storage | `com.google.cloud:google-cloud-storage` | `Storage.create(BlobInfo, byte[])` / `Storage.readAllBytes(BlobId)`. ADC for auth. |
| Azure Blob Storage | `com.azure:azure-storage-blob` | `BlobContainerClient.getBlobClient(key).upload(...)`. Connection string or `DefaultAzureCredential`. |
| MinIO / LocalStack / Cloudflare R2 | same as S3 (`awssdk:s3`) | Set `S3Client.builder().endpointOverride(URI.create("http://localhost:9000"))`. |

## Source layout

| File | Purpose |
|------|---------|
| [`BlobStore.java`](BlobStore.java) | Two-method abstraction over any object store |
| [`LocalFsBlobStore.java`](LocalFsBlobStore.java) | Zero-config implementation writing to the temp dir |
| [`ClaimCheckDataConverter.java`](ClaimCheckDataConverter.java) | The custom `DataConverter`; also contains backend pointers |
| [`ClaimCheckDataConverterWorkflow.java`](ClaimCheckDataConverterWorkflow.java) | Workflow + activity + sample `LargePayload` POJOs and generator |
| [`ClaimCheckWorker.java`](ClaimCheckWorker.java) | Worker main; wires the converter into `WorkflowClientOptions` and prints the stats banner |
| [`ClaimCheckStarter.java`](ClaimCheckStarter.java) | Thin async starter |
