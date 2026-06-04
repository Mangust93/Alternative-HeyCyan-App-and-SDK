# CLAUDE — AI User Shell (V1.3) Review

Ветка: `ai/claude-ai-user-shell-v1-3` · База: `ai/stable-v1-2-translation-photo-openrouter`

Первый шаг выноса пользовательских AI-функций из debug‑раздела
«Инструменты / Диагностика» в нормальный пользовательский раздел приложения.

---

## Что добавлено

Новый **optional** модуль `:ai-user-shell` — standalone Android library, по тому же
слабосвязанному паттерну, что и остальные optional‑модули
(`:photo-question-tools`, `:conversation-translation`, диагностические модули).

Файлы модуля:

- `ai-user-shell/build.gradle`
  Android library, namespace `com.fersaiyan.cyanbridge.ai_user_shell`, единственная
  зависимость — `androidx.appcompat:appcompat` (для `AppCompatActivity`). Нет
  `project(":app")`, нет glasses SDK / BLE / media / networking, **нет** зависимости на
  `:conversation-translation` и `:photo-question-tools`.
- `ai-user-shell/src/main/AndroidManifest.xml`
  `AiUserShellActivity`, `android:exported="false"`, `intent-filter` с action
  `com.fersaiyan.cyanbridge.feature.AI_USER_SHELL`. Никаких permissions.
- `ai-user-shell/src/main/java/.../AiUserShellActivity.kt`
  Экран «AI-функции» с карточками. UI — programmatic Android views (как в существующих
  модулях), без новых UI‑зависимостей и без Material‑redesign. Интерфейс на русском.
- `ai-user-shell/src/main/java/.../FeatureActions.kt`
  Локальные константы action для запускаемых фич (намеренное дублирование строк, чтобы
  не тянуть compile‑зависимость на `:app` или feature‑модули).

Карточки экрана:

1. **Переводчик** — «Перевод речи с озвучиванием в очки».
   Открывает существующий conversation translation через action
   `com.fersaiyan.cyanbridge.feature.CONVERSATION_TRANSLATION`.
   Если модуль недоступен — карточка disabled, статус «Модуль не включён».
2. **Фото и вопрос** — «Выберите фото и задайте вопрос AI».
   Открывает существующий photo question через action
   `com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION`.
   Если модуль недоступен — карточка disabled, статус «Модуль не включён».
3. **История запросов** — disabled, «Будет добавлено позже». Хранение истории НЕ
   реализовано в этой итерации.
4. **Настройки AI** — disabled, «Будет добавлено позже». OpenRouter settings НЕ
   переносились; они остаются внутри «Фото и вопрос».

Изменения в host app (`:app`):

- `settings.gradle.kts` — `include(":ai-user-shell")` под флагом `includeAiUserShell`
  (default `true`).
- `app/build.gradle` — `debugImplementation project(":ai-user-shell")` под тем же флагом
  (только debug, никогда не в release).
- `app/src/main/java/.../ui/tools/FeatureIntents.kt` — добавлена константа
  `AI_USER_SHELL`.
- `app/src/main/java/.../ui/SettingsActivity.kt` — `bindAiAssistantEntry()`: запускает
  shell через package‑scoped `Intent(action)`, прячет карточку, если модуль отсутствует.
- `app/src/main/res/layout/activity_settings.xml` — отдельная карточка «AI-функции»
  (`card_ai_assistant_entry` / `btn_open_ai_assistant`) над карточкой
  «Инструменты / Диагностика».
- `app/src/main/res/values/strings.xml` — строки `settings_ai_assistant_*`.

---

## Зачем нужен AI User Shell

«Инструменты / Диагностика» — это debug/test shell: он собирает в одном месте
optional feature‑ и диагностические модули для тестировщика. AI User Shell — это первая
**пользовательская** точка входа в AI‑функции: отдельный понятный экран «AI-функции»,
который показывает только полезные конечному пользователю фичи и одинаково открывает их
через те же Intent‑actions.

## Чем отличается от «Инструменты / Диагностика»

| | Инструменты / Диагностика | AI-функции (новый shell) |
|---|---|---|
| Аудитория | тестировщик / debug | конечный пользователь |
| Содержимое | все optional + диагностика | только AI‑фичи (переводчик, фото+вопрос) |
| Точка входа | кнопка «Open tools» в настройках | отдельная карточка «AI-функции» в настройках |
| Класс | `:app` `ToolsActivity` | `:ai-user-shell` `AiUserShellActivity` |
| Статус | **оставлен без изменений** | новый |

Это **не замена**: «Инструменты / Диагностика» полностью сохранён и продолжает открывать
те же фичи для тестировщика.

## Какие actions используются

- `com.fersaiyan.cyanbridge.feature.AI_USER_SHELL` — открыть сам shell (host app → модуль).
- `com.fersaiyan.cyanbridge.feature.CONVERSATION_TRANSLATION` — открыть переводчик.
- `com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION` — открыть «Фото и вопрос».

## Как сохраняется слабая связанность

- `:app` НЕ импортирует классы `:ai-user-shell` — открывает его через
  `Intent(FeatureIntents.AI_USER_SHELL).setPackage(packageName)` + `resolveActivity`.
- `:ai-user-shell` НЕ импортирует классы `:conversation-translation` и
  `:photo-question-tools` — открывает их через `Intent(action)` + `resolveActivity`.
- `:ai-user-shell` НЕ зависит от `:app`.
- Все activity `exported="false"`, запуск только package‑scoped intent внутри host app.
- Если фича отключена Gradle‑флагом — `resolveActivity` возвращает `null`, карточка
  показывает «Модуль не включён», а не падает. Сам shell открывается в любом случае.

## Как отключить

```
./gradlew :app:assembleDebug -PincludeAiUserShell=false
```

При отключении: модуль не включается в сборку, action `AI_USER_SHELL` и
`AiUserShellActivity` отсутствуют в merged manifest, карточка «AI-функции» в настройках
скрывается (`resolveActivity == null`), core app собирается и работает.

---

## Что проверить на телефоне

1. Настройки → карточка **«AI-функции»** → кнопка «Открыть AI-функции» → открывается экран.
2. Карточка **«Переводчик»** «Доступно» → «Открыть» открывает существующий переводчик,
   TTS выводится в очки.
3. Карточка **«Фото и вопрос»** «Доступно» → «Открыть» открывает существующий экран
   фото+вопрос (Mock / OpenRouter Direct работают как раньше).
4. Карточки **«История запросов»** и **«Настройки AI»** — disabled, «Будет добавлено позже».
5. «Инструменты / Диагностика» (кнопка «Open tools») по‑прежнему работает и не изменился.
6. Сборка с `-PincludeConversationTranslation=false` / `-PincludePhotoQuestionTools=false`:
   shell открывается, соответствующие карточки показывают «Модуль не включён», без падений.
7. Сборка с `-PincludeAiUserShell=false`: карточка «AI-функции» в настройках отсутствует.

## Проверки (выполнены)

- `git diff --check` — чисто.
- `./gradlew :app:compileDebugKotlin` — **BUILD SUCCESSFUL** (только pre‑existing warnings).
- `./gradlew :app:assembleDebug` — **BUILD SUCCESSFUL**.
- `./gradlew :app:assembleDebug -PincludeAiUserShell=false` — **BUILD SUCCESSFUL**.
- Default merged manifest: action `AI_USER_SHELL` присутствует, `AiUserShellActivity`
  присутствует, `exported="false"`.
- Disabled merged manifest: action `AI_USER_SHELL` отсутствует, `AiUserShellActivity`
  отсутствует, `ToolsActivity` и `PhotoQuestionActivity` на месте.
- `app/src/main/java` не импортирует классы `ai_user_shell`.
- `:ai-user-shell` не импортирует классы `photo_question_tools` / `conversation_translation`
  и не зависит от `:app`.
- Не изменены: `DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`/P2P,
  `NativeAutomationEngine`, `ConversationTranslationActivity`, PhotoQuestion OpenRouter
  logic, diagnostics modules.

## Риски / ограничения

- Модуль подключён как `debugImplementation` (как и остальные optional‑модули): в release
  его нет. Для пользовательской поставки потребуется отдельное решение по wiring в release.
- Shell не проверяет внутреннее состояние фич (например, заданы ли OpenRouter‑настройки) —
  он только проверяет наличие самой activity через `resolveActivity`.
- UI — programmatic views, намеренно простой; цвета подобраны под тёмную тему вручную.

## Отложено на следующие итерации

- История фото+вопрос+ответ (хранение и экран «История запросов»).
- Отдельный экран «Настройки AI».
- Список моделей OpenRouter.
- Улучшение UX сохранения настроек.
- Bluetooth mic input.
