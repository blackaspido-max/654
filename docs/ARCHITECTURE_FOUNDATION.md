# NightMaster 0.6 foundation architecture

## Purpose

Version `0.6-foundation` introduces the application shell and persistence layer
without replacing the user-tested v0.5 inference screen.

## Runtime boundaries

### Compose shell

`ShellActivity` is the launcher and owns only application navigation and the
first lightweight screens:

- Home;
- Chats;
- Models;
- Settings.

The shell does not load a GGUF model on startup. This keeps the home screen
cheap and prevents RAM use before the user explicitly enters a model workflow.

### Legacy chat

`MainActivity` remains the original XML/JNI screen from v0.5. It is reachable
from the home screen and continues to use the already verified inference path.
It is intentionally not rewritten in this batch.

### Persistence

`NightMasterDatabase` is Room schema version 1 and contains:

- `chats`;
- `messages`;
- `models`;
- `benchmark_results`.

The generated JSON schema is committed under `overlay/app/schemas`. Every
future database change must increment the Room version, add a migration and
commit the newly generated schema.

Small user preferences live in DataStore through `SettingsRepository`.
Database content must not be duplicated into DataStore.

### Inference

`InferenceController` is the application-facing contract. The initial
`LlamaInferenceController` wraps the existing `AiChat` engine and serializes
model-changing operations with a mutex.

The legacy screen does not use this controller yet. Migration will happen only
after the new chat screen can pass the same model-load, streaming and benchmark
checks as v0.5.

## Dependency container

`NightMasterApplication` owns lazy single instances of:

- Room database;
- settings repository;
- inference controller.

Lazy construction is required: opening the shell must not initialize the LLM
engine or allocate model memory.

## Safety rules for following batches

1. Keep `ru.aspid.nightmaster` as the package name.
2. Do not add the Android `INTERNET` permission.
3. Keep the v0.5 legacy screen available until replacement validation passes.
4. Do not auto-load models by default.
5. Do not silently copy multi-gigabyte GGUF files when a persistent document
   URI can be used safely.
6. Treat Room schema files as migration contracts, not disposable build output.
7. Build and inspect an APK after each small architecture batch.
