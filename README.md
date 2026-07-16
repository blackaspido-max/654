# NightMaster Android

Отдельный репозиторий локального Android-приложения «Ночной мастер».

## Гарантии проекта

- Репозиторий не связан с `VanillaEnhanced` и другими модами Bannerlord.
- Приложение не запрашивает разрешение `INTERNET`.
- GGUF-модель выбирается пользователем и запускается локально на устройстве.
- Нативный движок собирается из закреплённого commit `llama.cpp`, а не из
  меняющейся ветки `master`.

## Текущая контрольная версия

Рабочая база перед переходом к новой архитектуре:

- версия: `0.5-prototype`;
- package: `ru.aspid.nightmaster`;
- ABI: `arm64-v8a`;
- `llama.cpp`: `8ee54c8b32a1b0cf13c03fc5723142bc62c775f6`;
- резервная ветка: `archive/prototype-v0.5`.

Полная контрольная точка, включая проверенный Actions-run, SHA-256 APK и
реальный benchmark модели 4B, записана в
[`docs/PROTOTYPE_V05_BASELINE.md`](docs/PROTOTYPE_V05_BASELINE.md).

## Воспроизводимая сборка

`scripts/prepare_project.sh` выполняет следующие действия:

1. Получает строго закреплённый commit `llama.cpp`.
2. Проверяет точное совпадение commit.
3. Выполняет `git apply --check` для патча движка.
4. Применяет `patches/llama-android-nightmaster-v05.patch`.
5. Подменяет демонстрационный app-модуль содержимым `overlay/app`.

GitHub Actions затем собирает APK, проверяет package, версию, отсутствие
`INTERNET`, наличие только ARM64-библиотек и публикует вместе с APK:

- `SHA256.txt`;
- `BUILD_INFO.txt`;
- полный Gradle log.

## Сторонний код

Уведомления и сохранённый текст лицензии находятся в
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) и каталоге `third_party/`.
