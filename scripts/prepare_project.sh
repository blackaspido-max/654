#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/build/llama.cpp"
OUT="$UPSTREAM/examples/llama.android"
PATCH_FILE="$ROOT/patches/llama-android-nightmaster-v05.patch"
LLAMA_CPP_COMMIT="${LLAMA_CPP_COMMIT:-8ee54c8b32a1b0cf13c03fc5723142bc62c775f6}"
LLAMA_CPP_REPOSITORY="https://github.com/ggml-org/llama.cpp.git"

rm -rf "$UPSTREAM"
mkdir -p "$ROOT/build"

git init -q "$UPSTREAM"
git -C "$UPSTREAM" remote add origin "$LLAMA_CPP_REPOSITORY"
git -C "$UPSTREAM" fetch --depth 1 origin "$LLAMA_CPP_COMMIT"
git -C "$UPSTREAM" checkout -q --detach FETCH_HEAD

actual_commit="$(git -C "$UPSTREAM" rev-parse HEAD)"
if [[ "$actual_commit" != "$LLAMA_CPP_COMMIT" ]]; then
    echo "Unexpected llama.cpp commit: $actual_commit" >&2
    exit 1
fi

# The prototype engine modifications are kept as one reviewable patch. The
# explicit check makes upstream incompatibilities fail before Gradle starts.
git -C "$UPSTREAM" apply --check "$PATCH_FILE"
git -C "$UPSTREAM" apply "$PATCH_FILE"

rm -rf "$OUT/app"
cp -R "$ROOT/overlay/app" "$OUT/app"

chmod +x "$OUT/gradlew"
printf 'Prepared NightMaster v0.5 from llama.cpp %s\n' "$actual_commit"
