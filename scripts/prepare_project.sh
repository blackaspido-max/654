#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/build/NightMaster"
UPSTREAM="$ROOT/build/llama.cpp"

rm -rf "$OUT" "$UPSTREAM"
mkdir -p "$ROOT/build"

git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "$UPSTREAM"
cp -R "$UPSTREAM/examples/llama.android" "$OUT"
rm -rf "$OUT/app"
cp -R "$ROOT/overlay/app" "$OUT/app"
chmod +x "$OUT/gradlew"

echo "Prepared Android project at $OUT"
