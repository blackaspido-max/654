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

# Прототип собирается только для обычных ARM64-телефонов.
sed -i 's/listOf("arm64-v8a", "x86_64")/listOf("arm64-v8a")/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_BACKEND_DL=ON/-DGGML_BACKEND_DL=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/-DGGML_CPU_ALL_VARIANTS=ON/-DGGML_CPU_ALL_VARIANTS=OFF/' "$OUT/lib/build.gradle.kts"
sed -i 's/set(GGML_CPU_KLEIDIAI ON)/set(GGML_CPU_KLEIDIAI OFF)/' "$OUT/lib/src/main/cpp/CMakeLists.txt"

AI_CHAT="$OUT/lib/src/main/cpp/ai_chat.cpp"

# Всегда используем chat template, встроенный в Qwen GGUF.
sed -i 's/const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());/const bool has_chat_template = true;/g' "$AI_CHAT"

# Исправляем двойной учёт длины пользовательского сообщения в лимите генерации.
sed -i 's/stop_generation_position = current_position + user_prompt_size + n_predict;/stop_generation_position = current_position + n_predict;/' "$AI_CHAT"

# Контекст 4096 экономит память на телефоне.
sed -i 's/constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;/constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;/' "$AI_CHAT"

# Официальные настройки Qwen3 для non-thinking режима плюс мягкая защита от повторов.
sed -i 's/constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;/constexpr float DEFAULT_SAMPLER_TEMP    = 0.70f;/' "$AI_CHAT"
python3 - "$AI_CHAT" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1])
s = p.read_text(encoding="utf-8")
old = """static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    return common_sampler_init(g_model, sparams);
}"""
new = """static common_sampler *new_sampler(float temp) {
    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_k = 20;
    sparams.top_p = 0.80f;
    sparams.min_p = 0.00f;
    sparams.penalty_last_n = 128;
    sparams.penalty_repeat = 1.08f;
    sparams.penalty_present = 0.15f;
    return common_sampler_init(g_model, sparams);
}"""
if old not in s:
    raise SystemExit("new_sampler block not found")
p.write_text(s.replace(old, new), encoding="utf-8")
PY

chmod +x "$OUT/gradlew"
echo "Prepared ARM64 Android project for Qwen3 non-thinking roleplay test"
