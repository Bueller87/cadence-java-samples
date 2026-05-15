# Compression DataConverter Sample

A custom Cadence [`DataConverter`](../../../../../../../../README.md) that JSON-encodes workflow data and then gzip-compresses the bytes. For repetitive JSON payloads this typically achieves 60-80% size reduction, lowering storage cost and bandwidth without changing any workflow or activity code. The decode path caps decompressed payloads (default 10 MB) so a malformed input cannot drive unbounded memory growth.

- **Task list:** `data-compression`
- **Workflow type:** `CompressedDataConverterWorkflow`

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

The worker prints a compression statistics banner showing the before/after sizes of the sample payload, then begins polling the `data-compression` task list:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.compression.CompressionWorker
```

## Start a workflow (terminal 2)

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.compression.CompressionStarter
```

Or from the Cadence CLI:

```bash
cadence --domain samples-domain \
  workflow start \
  --workflow_type CompressedDataConverterWorkflow \
  --tl data-compression \
  --et 60
```

## How it works

- `toData`: JSON-encode the arguments with the standard `JsonDataConverter`, then write the bytes through `java.util.zip.GZIPOutputStream`.
- `fromData` / `fromDataArray`: decompress through `GZIPInputStream` with a configurable max output cap, then delegate to the standard `JsonDataConverter`.

## Source layout

| File | Purpose |
|------|---------|
| [`CompressedJsonDataConverter.java`](CompressedJsonDataConverter.java) | The custom `DataConverter` |
| [`CompressedDataConverterWorkflow.java`](CompressedDataConverterWorkflow.java) | Workflow + activity + sample `LargePayload` POJOs and generator |
| [`CompressionWorker.java`](CompressionWorker.java) | Worker main; wires the converter into `WorkflowClientOptions` and prints the stats banner |
| [`CompressionStarter.java`](CompressionStarter.java) | Thin async starter |
