# AI Settings V1.4

Минимальный экран пользовательских настроек AI внутри user-facing модуля
`:ai-user-shell`. Карточка «Настройки AI» на экране «AI-функции» стала активной и
открывает отдельный экран настроек.

## Что сделано

- Карточка «Настройки AI» в `AiUserShellActivity` активирована (раньше была disabled
  заглушка «Будет добавлено позже»). Открывается явным внутренним Activity intent.
- Добавлен экран `AiSettingsActivity`:
  - ввод OpenRouter API key;
  - сохранение ключа;
  - очистка ключа;
  - состояние «Ключ сохранён …» / «Ключ не задан»;
  - после сохранения полный ключ больше не показывается — выводится маска вида
    `sk-or-v1-••••1234`;
  - выбор модели из фиксированного локального списка (Spinner), без сетевого запроса;
  - кнопки «Сохранить», «Очистить ключ», «Назад»;
  - подсказка о локальном хранении ключа.
- Подтверждения (Toast):
  - после сохранения: **«Настройки AI сохранены»**;
  - после очистки: **«Ключ OpenRouter удалён»**.
- Хранилище `AiSettingsStore` (SharedPreferences, собственный файл модуля).
- «История запросов» осталась disabled — не трогалась.

Фиксированный список моделей (первая — по умолчанию):

- `google/gemini-2.0-flash-001`
- `google/gemini-2.5-flash-preview`
- `openai/gpt-4.1-mini`
- `openai/gpt-4o-mini`
- `qwen/qwen2.5-vl-72b-instruct`

## Изменённые / добавленные файлы

Все изменения — внутри модуля `:ai-user-shell` (зона `:app` не затронута).

Новые файлы:

- `ai-user-shell/src/main/java/com/fersaiyan/cyanbridge/ai_user_shell/AiSettingsStore.kt`
- `ai-user-shell/src/main/java/com/fersaiyan/cyanbridge/ai_user_shell/AiSettingsActivity.kt`
- `AI_SETTINGS_V1_4.md`

Изменённые файлы:

- `ai-user-shell/src/main/java/com/fersaiyan/cyanbridge/ai_user_shell/FeatureActions.kt`
  — добавлен внутренний маркер `AI_SETTINGS` для карточки настроек.
- `ai-user-shell/src/main/java/com/fersaiyan/cyanbridge/ai_user_shell/AiUserShellActivity.kt`
  — карточка «Настройки AI» получила action.
- `ai-user-shell/src/main/AndroidManifest.xml`
  — объявлена `AiSettingsActivity` (`exported="false"`, без intent-filter).

## Как открыть экран

1. Запустить debug-сборку с включённым модулем (по умолчанию `includeAiUserShell=true`).
2. Открыть экран «AI-функции» (`AiUserShellActivity`, action
   `com.fersaiyan.cyanbridge.feature.AI_USER_SHELL`).
3. Карточка «Настройки AI» теперь активна — нажать «Открыть».
4. Экран открывается явным внутренним Activity intent, проверенным `resolveActivity`.

`AiSettingsActivity` объявлена `android:exported="false"` — внешние приложения и `adb`
открыть её не могут.

## Где хранятся настройки

- SharedPreferences, файл `ai_user_shell_settings` (приватный для пакета приложения).
- Ключи: `openrouter_api_key`, `openrouter_model_id`.
- Хранилище — **собственное** для `:ai-user-shell`; оно намеренно НЕ переиспользует
  store модуля `:photo-question-tools`, чтобы не вводить compile-time зависимость и
  сохранить loose coupling.
- Ключ хранится в обычном (не шифрованном) SharedPreferences — так же, как в остальном
  проекте; компромисс описан пользователю прямо на экране.

## Что НЕ сделано (намеренно, вне scope V1.4)

- Нет реального обращения/тест-запроса к OpenRouter.
- Сохранённый ключ/модель пока НЕ прокидываются в поток «Фото и вопрос» — его логика не
  тронута; подключение настроек к запросам оставлено на будущий шаг.
- Нет загрузки списка моделей по сети — список фиксированный, локальный.
- Не добавлялись Room / DataStore / Hilt / EncryptedSharedPreferences / новая
  архитектура.
- Не затрагивались BLE, P2P, HeyCyan SDK, DeviceSyncManager,
  NativeBleGlassesController, MainActivity core flow, media/photo/video/audio sync,
  ConversationTranslationActivity, OpenRouter request logic, diagnostics tools.

## Риски

- Ключ хранится в открытом виде в SharedPreferences: доступен при root/backup на
  устройстве. Это осознанный компромисс (как в `:photo-question-tools`), о нём
  предупреждает подсказка на экране.
- `FeatureActions.AI_SETTINGS` теперь служит только внутренним маркером карточки;
  `AiSettingsActivity` не имеет intent-filter, поэтому риск рассинхрона action с manifest
  для этого экрана снят.
- Так как store отдельный, при будущем подключении к «Фото и вопрос» потребуется явно
  согласовать источник ключа/модели (сейчас у photo-question своё хранилище).
- Маска ключа: для нестандартного префикса показывается `••••` + последние 4 символа.
  Полный ключ не выводится никогда.

## Проверки

- `git status --short --branch`
- `git diff --check`
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs`
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs`
- `./gradlew :app:assembleDebug -PincludeAiUserShell=false --no-daemon --no-watch-fs`
  (модуль выключен — сборка не должна зависеть от него)

Результаты прогона см. в summary к задаче.
