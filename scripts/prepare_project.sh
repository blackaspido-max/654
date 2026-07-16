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
sed -i 's/listOf("arm64-v8a", "x86_64")/listOf("arm64-v8a")/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_BACKEND_DL=ON/-DGGML_BACKEND_DL=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_CPU_ALL_VARIANTS=ON/-DGGML_CPU_ALL_VARIANTS=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/set(GGML_CPU_KLEIDIAI ON)/set(GGML_CPU_KLEIDIAI OFF)/' "$OUT/lib/src/main/cpp/CMakeLists.txt"

# Android-пример llama.cpp ошибочно считает шаблон из GGUF «неявным» и посылает
# Qwen сырой текст. Для нашей известной Qwen-модели всегда используем её chat template.
AI_CHAT="$OUT/lib/src/main/cpp/ai_chat.cpp"
sed -i 's/const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());/const bool has_chat_template = true;/g' "$AI_CHAT"

# Исправляем двойной учёт длины пользовательского сообщения в лимите генерации.
sed -i 's/stop_generation_position = current_position + user_prompt_size + n_predict;/stop_generation_position = current_position + n_predict;/' "$AI_CHAT"

chmod +x "$OUT/gradlew"
echo "Prepared ARM64 Android project at $OUT with Qwen chat template enabled"
