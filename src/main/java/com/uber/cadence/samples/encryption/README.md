# Encryption DataConverter Sample

A custom Cadence [`DataConverter`](../../../../../../../../README.md) that JSON-encodes workflow data and then encrypts it with AES-256-GCM. Every workflow input, output, and activity parameter is encrypted before being written to Cadence history. Without the key, payloads stored by the Cadence server are unreadable to operators browsing workflow history.

Note that application logs, metrics, and search attributes are separate disclosure surfaces — a `DataConverter` does not protect them. Treat them accordingly.

- **Task list:** `data-encryption`
- **Workflow type:** `EncryptedDataConverterWorkflow`

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

### Encryption key

The worker loads its AES-256 key from the `CADENCE_ENCRYPTION_KEY` environment variable (64 hex characters = 32 bytes). If the env var is unset, the worker falls back to a hardcoded demo key and prints a warning — **never use the demo key in production**. If the env var is set but invalid, the worker fails fast instead of silently using the demo key.

Generate a key:

```bash
export CADENCE_ENCRYPTION_KEY=$(openssl rand -hex 32)
```

## Run the worker (terminal 1)

The worker prints an encryption statistics banner showing plaintext vs ciphertext size and a hex preview, then begins polling the `data-encryption` task list:

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.encryption.EncryptionWorker
```

## Start a workflow (terminal 2)

```bash
./gradlew -q execute -PmainClass=com.uber.cadence.samples.encryption.EncryptionStarter
```

Or from the Cadence CLI:

```bash
cadence --domain samples-domain \
  workflow start \
  --workflow_type EncryptedDataConverterWorkflow \
  --tl data-encryption \
  --et 60
```

## How it works

- `toData`: JSON-encode the arguments with the standard `JsonDataConverter`, then encrypt with `AES/GCM/NoPadding` using a fresh 12-byte random nonce. The output layout is `nonce(12 bytes) || ciphertext || tag(16 bytes)`. A new nonce per call preserves semantic security for repeated payloads.
- `fromData` / `fromDataArray`: split nonce + ciphertext, run AES-GCM decrypt (which authenticates the tag and fails on any tampering), then delegate to `JsonDataConverter`.

## Source layout

| File | Purpose |
|------|---------|
| [`EncryptedJsonDataConverter.java`](EncryptedJsonDataConverter.java) | The custom `DataConverter` |
| [`EncryptionKeyLoader.java`](EncryptionKeyLoader.java) | Reads the 32-byte key from `CADENCE_ENCRYPTION_KEY` or the demo fallback |
| [`EncryptedDataConverterWorkflow.java`](EncryptedDataConverterWorkflow.java) | Workflow + activity + sample `SensitiveCustomerRecord` POJO and generator |
| [`EncryptionWorker.java`](EncryptionWorker.java) | Worker main; wires the converter into `WorkflowClientOptions` and prints the stats banner |
| [`EncryptionStarter.java`](EncryptionStarter.java) | Thin async starter |
