# NightMaster prototype v0.5 baseline

This document records the last user-tested prototype before the application
architecture is replaced.

## Source baseline

- Repository: `blackaspido-max/654`
- Application commit: `c1f372c9390fdaa6803e6887df5725cacae93637`
- Immutable backup branch: `archive/prototype-v0.5`
- Package: `ru.aspid.nightmaster`
- Version code: `5`
- Version name: `0.5-prototype`
- Minimum Android version: API 33
- Target Android version: API 36
- Native ABI: `arm64-v8a`
- Build type: debug prototype
- Internet permission: absent

## Native engine baseline

- Upstream: `ggml-org/llama.cpp`
- Pinned commit: `8ee54c8b32a1b0cf13c03fc5723142bc62c775f6`
- Engine patch: `patches/llama-android-nightmaster-v05.patch`
- Context size: 4096 tokens
- Qwen3 non-thinking test parameters are preserved by the patch.

## Verified build

The previous v0.5 verification build completed successfully in GitHub Actions:

- Pull request: `#6`
- Workflow run: `29487794340`
- Run number: `37`
- APK artifact ID: `8371243630`
- Artifact digest: `sha256:a3f9b3f65095fd51b006f2656c599bfb9295610a10118b63b301195e2344d9cc`
- APK SHA-256 inside the artifact:
  `7d818ded0de43217e50c431a5d95208a1a144105bd004135f8ba4844e2ca940f`

The APK was installed and tested by the user on a real Android phone. Local
GGUF loading, streaming generation and the built-in benchmark worked. This
does not imply that future builds have been phone-tested until the user tests
them separately.

## First real model benchmark

Model: `Huihui-Qwen3-4B-abliterated-v2.Q4_K_M.gguf`

- Detected model: `qwen3 4B Q4_K - Medium`
- Size: `2.32 GiB`
- Parameters: `4.02B`
- Backend: CPU
- First visible text: `20.12 s`
- Generation: `2.21 tokens/s`
- Text speed: `4.48 characters/s`
- Prompt processing: `2.95 tokens/s`
- Real text: `299 characters in 86.91 s`
- Estimated 128 tokens: `57.92 s`

Energy-saving mode was disabled during the measurement. This result is kept as
the first historical benchmark and must not be replaced by an estimate.

## Preservation rule

The legacy screen may be wrapped or migrated, but the known working inference
path must remain buildable until the replacement controller passes the same
build and real-device checks.
