# CLAUDE_RUSSIAN_UI_V1_REVIEW.md
## Sprint: ai/claude-russian-ui-v1-navigation
**Date:** 2026-05-24

---

## 1. Найденные экраны

| Экран | Activity | Файл layout |
|---|---|---|
| Главная (очки, медиа, агент) | `MainActivity` | `acitivyt_main.xml` |
| Подключение очков (BLE-скан) | `DeviceBindActivity` | `activity_device_bind.xml` |
| Список чатов | `ChatListActivity` | `activity_chat_list.xml` |
| Чат-тред | `ChatThreadActivity` | `activity_chat_thread.xml` |
| Записи и транскрипции | `RecordingsListActivity` | `activity_recordings_list.xml` |
| Медиа с очков (галерея) | `SyncedMediaGalleryActivity` | `activity_synced_media_gallery.xml` |
| Настройки / конфиденциальность | `SettingsActivity` | `activity_settings.xml` |
| Приветствие (онбординг) | `WelcomeActivity` | `activity_welcome.xml` |
| Слайды онбординга | `OnboardingFeatureActivity` | `activity_onboarding.xml` |
| Настройка локальных моделей | `LocalModelsConfigureActivity` | `activity_local_models_configure.xml` |
| Pro-подписка | `ProSubscriptionActivity` | `activity_pro_subscription.xml` |
| Настройки Pro | `ProSubscriptionSettingsActivity` | `activity_pro_subscription_settings.xml` |
| Ежедневные факты | `DailyFactsActivity` | `activity_daily_facts.xml` |
| Ежедневная сводка/дневник | `DailySummaryActivity` | `activity_daily_summary.xml` |
| Ожидающие действия | `PendingActionsActivity` | `activity_pending_actions.xml` |
| Черный список приложений | `AppBlacklistActivity` | `activity_app_blacklist.xml` |
| Снимки экрана | `ScreenCapturesActivity` | `activity_screen_captures.xml` |
| Заметки (список) | `NotesListActivity` | `activity_notes_list.xml` |
| Заметка (детали) | `NoteDetailActivity` | `activity_note_detail.xml` |
| Сообщество / плагины | `CommunityPluginsActivity` | `activity_community_plugins.xml` |
| Оптимизация батареи | `BatteryOptimizationGuideActivity` | `activity_battery_optimization_guide.xml` |
| Отладка транскрипций | `TranscriptionDebugActivity` | `activity_transcription_debug.xml` |
| **[optional]** Лог отладки | `DebugLogActivity` | — (kotlin only) |
| **[optional]** Диагностика телефона | `PhoneTestDiagnosticsActivity` | — (kotlin only) |
| **[optional]** Runtime-диагностика | `RuntimeDiagnosticsActivity` | — (kotlin only) |

**Нижняя навигация (BottomNavigationView):**
- Чаты | Очки | Записи и транскрипции | Настройки | Плагины

---

## 2. Карта навигации V1

```
[ Чаты ]                   [ Очки ]                  [ Записи ]
    │                           │                          │
Список чатов              Главный экран             Список записей
    │                       │       │                      │
Чат-тред              Статус очков  Медиа с очков    Просмотр записи
                       │       │       │
                  Подключить  Фото  Альбом/Галерея
                  Отключить   Видео
                             Аудио

[ Настройки ]              [ Плагины ]
    │                           │
Конфиденциальность        Сообщество
    │
 ИИ / Автоматизация
 Локальный агент
 Транскрипции
 Данные (экспорт/импорт)

[ Онбординг (при первом запуске) ]
    WelcomeActivity → OnboardingFeatureActivity (слайды) → MainActivity

[ Диагностика (опциональные модули) ]
    DebugLogActivity | PhoneTestDiagnosticsActivity | RuntimeDiagnosticsActivity
```

### Будущие экраны (НЕ в V1)
- Фото-вопрос (VisionQuestion) — фото с очков + вопрос по фото
- Медиа-браузер (MediaBrowser) — отдельное окно медиа
- Транскрибация (AudioTranscription)
- Tasker-замена (TaskerReplacementPlugins)
- **Hermes/OpenRouter** — см. раздел 8

---

## 3. Что переведено на русский

### 3.1 Нижняя навигация
| Английский | Русский |
|---|---|
| Chats | Чаты |
| Glasses | Очки |
| Transcriptions & recordings | Записи и транскрипции |
| Settings | Настройки |
| Plugins | Плагины |

### 3.2 Главный экран (acitivyt_main.xml)
| Английский | Русский |
|---|---|
| GLASSES STATUS | СТАТУС ОЧКОВ |
| Battery: | Батарея: |
| Storage: | Память: |
| CONNECTION | ПОДКЛЮЧЕНИЕ |
| Scan | Поиск |
| Reconnect | Переподключить |
| Disconnect | Отключить |
| MEETING CAPTURE | ЗАПИСЬ ВСТРЕЧИ |
| Start | Начать |
| Stop | Стоп |
| Timer: | Таймер: |
| SYNC PROGRESS | СИНХРОНИЗАЦИЯ |
| Stop sync | Остановить синхр. |
| AI ASSISTANT | ИИ-АССИСТЕНТ |
| MEDIA CONTROLS | МЕДИА С ОЧКОВ |
| Photo | Фото |
| Video | Видео |
| Audio | Аудио |
| Recording active | Запись активна |

### 3.3 Экран настроек (activity_settings.xml — верхний блок)
| Английский | Русский |
|---|---|
| Privacy Settings | Настройки конфиденциальности |
| Privacy-first defaults... | Приватные настройки по умолчанию... |
| Recording active | Запись активна |
| Stop | Стоп |

### 3.4 Список чатов (activity_chat_list.xml)
| Английский | Русский |
|---|---|
| Chats (заголовок) | Чаты |
| No chats yet. Tap + to start. | Нет чатов. Нажмите + для создания. |
| Long press a chat to delete it | Долгое нажатие на чат — удалить |

### 3.5 Записи (activity_recordings_list.xml)
| Английский | Русский |
|---|---|
| No recordings yet... | Нет записей... |

### 3.6 Баннер записи (view_meeting_recording_banner.xml)
| Английский | Русский |
|---|---|
| Recording active | Запись активна |
| Stop | Стоп |

### 3.7 Элемент устройства (recycleview_item_device.xml)
| Английский | Русский |
|---|---|
| Choose type | Выбрать тип |

### 3.8 Существующие strings.xml (values-ru)
Переведены все ранее существовавшие строки:
- Онбординг (5 слайдов, полный текст)
- Оптимизация батареи (все строки)
- Приветственный экран
- Галерея медиа с очков
- Удаление чата
- Синхронизированные медиа

---

## 4. Что оставлено на потом (Sprint 2+)

### Технические/внутренние строки (не переведены намеренно)
- Секции настроек: `LOCAL AGENT`, `AI / AUTOMATION`, `MEMORY PRIVACY`, `TRANSCRIPTS`, `REDACTION`, `DATA`, `FAQ` — технические настройки, ориентированы на продвинутого пользователя
- Имена провайдеров: `Tasker (AutoInput)`, `Local Models (on-device llama.cpp)`, `Pro Subscription` — торговые марки / технические термины
- Кнопки debug-раздела: `View last injected context (debug)`, `Demo`, `Configure Local Models` — внутренние
- Строки в Kotlin-коде (Toast, AlertDialog) — часть из них на английском, требуют отдельного спринта для корректного использования `getString()`
- Строки опциональных модулей (`DebugLogActivity`, `PhoneTestDiagnosticsActivity`, `RuntimeDiagnosticsActivity`) — диагностика для разработчиков

### Layout-строки ниже приоритетом V1
- `activity_settings.xml` — тело настроек (300+ строк контента)
- `activity_welcome.xml`, `activity_onboarding.xml` — уже в strings.xml, переведены через values-ru
- `activity_battery_optimization_guide.xml` — уже в strings.xml
- `activity_pro_subscription.xml` — маркетинговые тексты

### Динамические строки в Kotlin (статус, состояния)
Строки, которые устанавливаются программно и требуют `getString(R.string.*)`:
- `"Connected - $deviceName"` / `"Connected"` / `"Disconnected"` — MainActivity:2175–2180
- `"Class: $classLabel"` — MainActivity:2192
- `"Status: Unknown"` / `"Last error: (none)"` — динамически
- `"Advanced ▼"` / `"Advanced ▲"` — кнопка сворачивания
- Тексты баннера записи (`"Starting recording…"`, `"Recording active · $src"`) — MainActivity:2337/2402
- Toast-сообщения в SettingsActivity, ChatListActivity, ChatThreadActivity

---

## 5. Изменённые файлы

| Файл | Тип изменения |
|---|---|
| `app/src/main/res/values/strings.xml` | Добавлены 30 новых string-записей |
| `app/src/main/res/values-ru/strings.xml` | **НОВЫЙ ФАЙЛ** — 100+ переводов |
| `app/src/main/res/menu/bottom_nav_menu.xml` | 4 hardcoded title → @string/nav_* |
| `app/src/main/res/layout/acitivyt_main.xml` | 18 hardcoded string → @string/* |
| `app/src/main/res/layout/activity_settings.xml` | 4 hardcoded string → @string/* |
| `app/src/main/res/layout/activity_chat_list.xml` | 3 hardcoded string → @string/* |
| `app/src/main/res/layout/activity_recordings_list.xml` | 1 hardcoded string → @string/* |
| `app/src/main/res/layout/view_meeting_recording_banner.xml` | 2 hardcoded string → @string/* |
| `app/src/main/res/layout/recycleview_item_device.xml` | 1 hardcoded string → @string/* |

---

## 6. Как проверить русский интерфейс на телефоне

1. **Переключить язык устройства:**
   - Настройки → Управление приложениями и устройством → Язык и ввод → Язык → Русский (первым в списке)
   - ИЛИ: Настройки → Общие настройки → Язык → Добавить язык → Русский
   
2. **Установить APK:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   
3. **Проверить ключевые элементы:**
   - [ ] Нижняя навигация: «Чаты» / «Очки» / «Записи и транскрипции» / «Настройки» / «Плагины»
   - [ ] Главный экран: секция «СТАТУС ОЧКОВ», кнопки «Поиск», «Переподключить», «Отключить»
   - [ ] Главный экран: секция «МЕДИА С ОЧКОВ», кнопки «Фото», «Видео», «Аудио»
   - [ ] Главный экран: секция «ЗАПИСЬ ВСТРЕЧИ», кнопки «Начать», «Стоп»
   - [ ] Экран чатов: заголовок «Чаты», пустое состояние
   - [ ] Настройки: заголовок «Настройки конфиденциальности»
   - [ ] Галерея медиа: заголовок «Медиа с очков»
   - [ ] Онбординг / приветствие — проверить русские тексты слайдов

4. **Вернуть язык:**
   - Переключить обратно на нужный язык после проверки

---

## 7. Известные ограничения

1. **Динамические строки в Kotlin не переведены** — статус подключения (`Connected`, `Disconnected`), тексты баннера записи, toast-сообщения устанавливаются прямо в коде как строковые литералы. Для полного перевода нужен отдельный PR с `getString(R.string.*)`.

2. **activity_settings.xml тело** — большой объём контента (настройки LOCAL AGENT, TRANSCRIPTS и т.д.) остался на английском. Эти строки — технические, для продвинутого пользователя.

3. **Advanced-секция** главного экрана (скрытый блок) — кнопки `Start`/`Stop`/`Demo` агента, `Battery`/`Version`/`Sync Time` в DEV TOOLS — техническое управление, оставлены на английском.

4. **Опциональные модули** не имеют собственных res-ресурсов — все строки в Kotlin-коде, переводить отдельным PR.

5. **Строка `"Advanced ▼"/"▲"`** — устанавливается динамически в MainActivity, не может быть переведена только через XML.

---

## 8. Следующий спринт: Hermes / OpenRouter

### Архитектура потока V1

```
Android App (CyanBridge)
        │
        │  HTTP / WebSocket
        ▼
  Hermes Gateway
  (self-hosted или облачный)
        │
        │  OpenRouter API
        ▼
  OpenRouter
  (маршрутизатор к LLM-провайдерам)
        │
        ├─ GPT-4o (OpenAI)
        ├─ Claude Sonnet (Anthropic)
        ├─ Gemini Flash (Google)
        └─ ... (другие модели)
        │
        ▼
  Hermes Gateway
        │
        │  HTTP / WebSocket
        ▼
Android App — ответ в ChatThreadActivity
```

### Что нужно сделать в следующем спринте

1. **Новый модуль `:hermes-client`:**
   - `HermesGatewayClient.kt` — HTTP/WS клиент к Hermes
   - `OpenRouterRoutingPolicy.kt` — выбор модели через OpenRouter
   - `HermesPrefs.kt` — endpoint URL, API key (зашифрованный в Keystore)

2. **Интеграция с AiAssistantRouter:**
   - Добавить `AiProviderType.HERMES` в `AiProviderType.kt`
   - В `SettingsActivity` добавить опцию «Hermes / OpenRouter» рядом с Tasker/Local/Pro
   - В ChatThreadActivity подключить новый роутер

3. **UI:**
   - Настройки → новая секция «HERMES / OPENROUTER»
   - Поле ввода endpoint URL
   - Поле ввода OpenRouter API key (masked)
   - Выбор модели (dropdown или radio)
   - Кнопка «Проверить подключение»

4. **Безопасность:**
   - API key хранить через Android Keystore / EncryptedSharedPreferences
   - НЕ логировать ключ
   - НЕ включать в экспорт данных

5. **Строгие ограничения остаются:**
   - Не трогать BLE/P2P/SDK/media flow
   - Не трогать PictureVm, AlbumDownloader
   - Не трогать NativeAutomationEngine
   - Hermes-клиент — изолированный optional-модуль
