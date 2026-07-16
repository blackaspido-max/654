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

# Первый прототип собирается только для обычных ARM64-телефонов.
# Убираем x86_64 и полный набор тяжёлых CPU-вариантов: они не нужны для проверки чата.
sed -i 's/listOf("arm64-v8a", "x86_64")/listOf("arm64-v8a")/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_BACKEND_DL=ON/-DGGML_BACKEND_DL=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_CPU_ALL_VARIANTS=ON/-DGGML_CPU_ALL_VARIANTS=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/set(GGML_CPU_KLEIDIAI ON)/set(GGML_CPU_KLEIDIAI OFF)/' "$OUT/lib/src/main/cpp/CMakeLists.txt"

chmod +x "$OUT/gradlew"
echo "Prepared ARM64 Android project at $OUT"
