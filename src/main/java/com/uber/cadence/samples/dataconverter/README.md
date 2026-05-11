# DataConverter Samples

Three practical patterns for custom `DataConverter` implementations in the Cadence Java client: **compression**, **encryption**, and **BlobStore / S3 claim-check offload**. A `DataConverter` controls how every workflow input, output, and activity parameter is serialized before it is written to Cadence history — making it the right place to add compression, encryption, or external offloading without changing any workflow or activity code.

## What is a DataConverter?

`com.uber.cadence.converter.DataConverter` defines three methods:

- `byte[] toData(Object... values)` — called before data is written to Cadence history.
- `<T> T fromData(byte[] content, Class<T> valueClass, Type valueType)` — called for single-value payloads (workflow/activity results, internal payloads).
- `Object[] fromDataArray(byte[] content, Type... valueTypes)` — called to decode workflow/activity argument lists on the worker side.

The same `DataConverter` must be used by **both the worker and any client that sends or receives non-trivial workflow data**. In these samples the workflows generate their payloads internally and take no inputs, so they can be started from the Cadence CLI without bundling a custom converter into the CLI itself.

Each sample uses its own task list so it can have its own `DataConverter`. `DataConverterWorker` starts one worker per task list in a single process.

## Prerequisites

1. Cadence server running (e.g. Docker Compose from the [Cadence repo](https://github.com/uber/cadence)).
2. From the repo root, build: `./gradlew build`.

### Register the domain (required once per cluster)

Starters use domain **`samples-domain`**. If you see `Domain samples-domain does not exist`, register it **before** starting workflows:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.common.RegisterDomain
```

Or with the Cadence CLI:

```bash
cadence --domain samples-domain domain register
```

See also the root [README.md](../../../../../../../../README.md).

## Run the worker (terminal 1)

Leave this process running. It starts three workers — one per `DataConverter` — and prints a stats banner per sample:

```bash
cd /path/to/cadence-java-samples
./gradlew -q execute -PmainClass=com.uber.cadence.samples.dataconverter.DataConverterWorker
```

## Start a workflow (terminal 2)

Run **one** of the starters per sample run. Each starts a new workflow execution and exits.

**Compression** — gzip-over-JSON:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.dataconverter.CompressionStarter
```

**Encryption** — AES-256-GCM:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.dataconverter.EncryptionStarter
```

**S3 offload** — claim-check pattern with a zero-config local `BlobStore`:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.dataconverter.S3OffloadStarter
```

You can also start any of the three from the Cadence CLI; the commands are printed in the worker's stats banner on startup.

---

## Compression Sample

`CompressedDataConverterWorkflow` demonstrates gzip-over-JSON compression. For repetitive JSON data this typically achieves 60–80% size reduction, lowering storage cost and bandwidth for large workflow payloads. The converter is implemented in [`CompressedJsonDataConverter.java`](CompressedJsonDataConverter.java) — it wraps `JsonDataConverter.getInstance()`, post-processes the resulting bytes through `java.util.zip.GZIP*Stream`, and caps decompressed output to avoid unbounded memory growth on malformed input.

- **Task list:** `data-compression`
- **Workflow type:** `CompressedDataConverterWorkflow`

---

## Encryption Sample

`EncryptedDataConverterWorkflow` demonstrates AES-256-GCM encryption. Every workflow input, output, and activity parameter is encrypted before being written to Cadence history. Without the key, payloads stored by the Cadence server are unreadable to operators browsing workflow history. Logs, metrics, search attributes, and application output are separate disclosure surfaces.

The sample uses a `SensitiveCustomerRecord` containing realistic PII and PHI fields (name, email, SSN, credit card, medical notes) to make the use case concrete.

- **Task list:** `data-encryption`
- **Workflow type:** `EncryptedDataConverterWorkflow`

### Encryption key

By default, the worker uses a hardcoded demo key and prints a prominent warning. To use your own key:

```bash
export CADENCE_ENCRYPTION_KEY=$(openssl rand -hex 32)
./gradlew -q execute -PmainClass=com.uber.cadence.samples.dataconverter.DataConverterWorker
```

> **WARNING:** The hardcoded demo key (`cadence-demo-key-NOT-FOR-PROD!!!`) is public. Never use it in production. In production, load your key from a secrets manager (AWS Secrets Manager, HashiCorp Vault, GCP Secret Manager, etc.).

### How AES-256-GCM works

- `toData`: JSON-encode arguments → generate a 12-byte random nonce → `Cipher.doFinal` with `AES/GCM/NoPadding` → return `nonce || ciphertext || tag`.
- `fromData` / `fromDataArray`: split nonce from input → `Cipher.doFinal` (decrypt) → JSON-decode.

The GCM authentication tag (16 bytes) ensures any ciphertext tampering is detected. The random nonce means the same plaintext produces different ciphertext on every call, which preserves semantic security for repeated payloads.

---

## S3 Offload Sample (claim-check pattern)

`S3OffloadDataConverterWorkflow` demonstrates the *claim-check* pattern: payloads larger than a configurable threshold are stored in an external [`BlobStore`](BlobStore.java) and only a small reference (a few dozen bytes) travels through Cadence workflow history. The runnable sample uses [`LocalFsBlobStore`](LocalFsBlobStore.java) so it works without cloud credentials; the same abstraction can be backed by S3 in production. This solves Cadence's per-payload size limits (~2 MB) for workflows that pass very large datasets between the workflow and its activities.

- **Task list:** `data-s3`
- **Workflow type:** `S3OffloadDataConverterWorkflow`

### How it works

- `toData`: JSON-encode → if `len(json) > thresholdBytes`, upload to `BlobStore` under a SHA-256 key and return `0x01 || {"s3Ref":"<bucket>/<sha256hex>"}`. Otherwise return `0x00 || json` inline.
- `fromData` / `fromDataArray`: read prefix byte → if `0x01`, fetch from `BlobStore` and decode; if `0x00`, decode inline.

SHA-256-of-payload is used as the key so `toData` is idempotent across Cadence workflow replays. Using a fresh UUID per call would write a new orphaned blob on every replay.

### Default store (zero-config)

Out of the box, [`LocalFsBlobStore`](LocalFsBlobStore.java) writes blobs to `${java.io.tmpdir}/cadence-java-samples-data-s3/`. No cloud credentials or additional dependencies are needed.

### Swapping in real AWS S3

The top of [`S3OffloadDataConverter.java`](S3OffloadDataConverter.java) contains a commented `S3BlobStore` skeleton showing the AWS SDK v2 calls needed. To enable it:

1. Add AWS SDK v2 to `build.gradle`:
   ```groovy
   implementation group: 'software.amazon.awssdk', name: 's3', version: '2.25.0'
   ```
2. Implement `BlobStore` against `software.amazon.awssdk.services.s3.S3Client` (the commented stub shows the exact calls).
3. Replace `new LocalFsBlobStore()` with `new S3BlobStore("my-bucket", "us-east-1")` in `DataConverterWorker`.
4. Set standard AWS environment variables (`AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) or use an IAM instance role.

You can also point the SDK at [LocalStack](https://localstack.cloud/) or [MinIO](https://min.io/) for local testing without a real AWS account.

> **Note on cleanup:** `S3OffloadDataConverter` does not delete blobs after the workflow completes. In production, use S3 object lifecycle policies to automatically expire old blobs.

---

## When to use which pattern

| Pattern | Best for |
|---------|----------|
| **Compression** | Large repetitive JSON payloads; reducing storage cost without confidentiality requirements |
| **Encryption** | PII, PHI, secrets, or any data that must be unreadable in Cadence history |
| **BlobStore / S3 Offload** | Payloads approaching Cadence's size limits; binary or non-JSON data; cost-conscious archival |

Patterns can be composed, but order matters. Compress before encrypting when size reduction is a goal; encrypt before offloading when the external store should only receive ciphertext.

## Source layout

| File | Purpose |
|------|---------|
| [`DataConverterConstants.java`](DataConverterConstants.java) | Task list and workflow type names plus the shared Cadence domain |
| [`DataConverterSupport.java`](DataConverterSupport.java) | Shared `WorkflowClient` factory + friendly "domain missing" hint |
| [`DataConverterWorker.java`](DataConverterWorker.java) | Hosts all three workers; prints stats banners on startup |
| [`CompressedJsonDataConverter.java`](CompressedJsonDataConverter.java) | gzip-over-JSON `DataConverter` |
| [`EncryptedJsonDataConverter.java`](EncryptedJsonDataConverter.java) | AES-256-GCM `DataConverter` |
| [`EncryptionKeyLoader.java`](EncryptionKeyLoader.java) | Reads `CADENCE_ENCRYPTION_KEY` with demo-key fallback |
| [`BlobStore.java`](BlobStore.java) / [`LocalFsBlobStore.java`](LocalFsBlobStore.java) | `BlobStore` abstraction + local-FS default |
| [`S3OffloadDataConverter.java`](S3OffloadDataConverter.java) | Claim-check `DataConverter` with commented AWS S3 stub |
| `*DataConverterWorkflow.java` | One workflow + activity per sample (each takes no inputs) |
| `*Starter.java` | Thin async starters mirroring the existing `query/` samples |
