#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UPSTREAM="$ROOT/build/llama.cpp"
OUT="$UPSTREAM/examples/llama.android"

rm -rf "$UPSTREAM"
mkdir -p "$ROOT/build"

git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "$UPSTREAM"
rm -rf "$OUT/app"
cp -R "$ROOT/overlay/app" "$OUT/app"
chmod +x "$OUT/gradlew"

echo "Prepared Android project at $OUT"
