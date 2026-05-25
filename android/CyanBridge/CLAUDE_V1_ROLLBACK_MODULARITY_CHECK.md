# CyanBridge V1 Rollback & Modularity Check

**Branch:** `ai/claude-v1-rollback-modularity-check`
**Date:** 2026-05-25
**Engineer:** Claude Code (senior Android / release engineering review)

---

## 1. V1 Module Map

### Core (always compiled, never toggleable)

| Module | Role | Constraints |
|---|---|---|
| `:app` | Application entry point, all core UI | Do not touch: `MainActivity`, `ChatThreadActivity`, `DeviceBindActivity` |
| `:LIB_GLASSES_SDK` | Glasses SDK AAR | Do not touch glasses SDK flow |
| `:moonshine-voice` | Offline transcription (vendored native) | Present only when `third_party/moonshine/core/CMakeLists.txt` exists |

### Optional Debug Modules (debugImplementation only, toggle via Gradle property)

| Module | Gradle Property | Default | Key Files |
|---|---|---|---|
| `:phone-test-tools` | `includePhoneTestTools` | `true` | `PhoneTestDiagnosticsActivity.kt` |
| `:debug-log-tools` | `includeDebugLogTools` | `true` | `DebugLogStore.kt`, `DebugLogActivity.kt` |
| `:runtime-diagnostics-tools` | `includeRuntimeDiagnosticsTools` | `true` | `RuntimeDiagnostics.kt`, `RuntimeDiagnosticsInitProvider.kt`, `RuntimeDiagnosticsStore.kt`, `RuntimeDiagnosticsActivity.kt` |

### Manifest entries per optional module

**`:phone-test-tools`**
- `<activity>` `PhoneTestDiagnosticsActivity` (exported=true, adb-only)

**`:debug-log-tools`**
- `<activity>` `DebugLogActivity` (exported=true, adb-only)

**`:runtime-diagnostics-tools`**
- `<provider>` `RuntimeDiagnosticsInitProvider` (exported=false, auto-init via ContentProvider)
- `<activity>` `RuntimeDiagnosticsActivity` (exported=true, adb-only)

---

## 2. Gradle Flags

Все три flags читаются через `providers.gradleProperty(...).orElse("true")` в `app/build.gradle`:

```groovy
def includePhoneTestTools = providers.gradleProperty("includePhoneTestTools").orElse("true").get().toBoolean()
def includeDebugLogTools = providers.gradleProperty("includeDebugLogTools").orElse("true").get().toBoolean()
def includeRuntimeDiagnosticsTools = providers.gradleProperty("includeRuntimeDiagnosticsTools").orElse("true").get().toBoolean()
```

**Ключевые гарантии:**
- Все три подключены исключительно через `debugImplementation project(...)` — в `releaseImplementation` не попадают.
- Ни один optional module не содержит `project(":app")` зависимости (проверено статическим grep).
- `releaseImplementation` для optional modules нигде не используется.

---

## 3. Сборка APK со всеми modules (default)

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs
```

Включает: `:phone-test-tools`, `:debug-log-tools`, `:runtime-diagnostics-tools`.

---

## 4. Сборка APK без отдельных modules

### Без phone-test-tools

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs \
  -PincludePhoneTestTools=false
```

Лог подтверждает: `includePhoneTestTools=false; skipping :phone-test-tools dependency`
Manifest: `PhoneTestDiagnosticsActivity` отсутствует.

### Без debug-log-tools

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs \
  -PincludeDebugLogTools=false
```

Лог подтверждает: `includeDebugLogTools=false; skipping :debug-log-tools dependency`
Manifest: `DebugLogActivity` отсутствует.

### Без runtime-diagnostics-tools

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs \
  -PincludeRuntimeDiagnosticsTools=false
```

Лог подтверждает: `includeRuntimeDiagnosticsTools=false; skipping :runtime-diagnostics-tools dependency`
Manifest: `RuntimeDiagnosticsInitProvider` и `RuntimeDiagnosticsActivity` отсутствуют.

---

## 5. Минимальный core APK (все debug modules отключены)

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs \
  -PincludePhoneTestTools=false \
  -PincludeDebugLogTools=false \
  -PincludeRuntimeDiagnosticsTools=false
```

**Что включено в APK:** только core app + glasses SDK + Room + OkHttp + billing + coroutines + llama.cpp/LiteRT.
**Что исключено:** все три debug activities и `RuntimeDiagnosticsInitProvider`. Manifest чист от любых optional entries.

---

## 6. Git Rollback

### Rollback последнего commit

```bash
git revert HEAD --no-edit
```

Создаёт revert-commit без изменения истории. Безопаснее, чем `reset --hard`.

Если нужно только убрать изменения локально (не пуша):
```bash
git reset --soft HEAD~1   # сохранить изменения в staging
# или
git reset --hard HEAD~1   # НЕОБРАТИМО, только для незапущенной ветки
```

### Переключение на предыдущую ветку

```bash
git checkout ai/claude-russian-ui-v1-navigation
```

Или на main:
```bash
git checkout main
```

### Cherry-pick нужного модуля

Найти нужный commit:
```bash
git log --oneline ai/claude-v1-rollback-modularity-check
```

Взять только один commit (например, добавление `:debug-log-tools`):
```bash
git cherry-pick 7caa6e2   # "Add optional debug log tools module"
```

Взять диапазон (runtime-diagnostics + review fixes):
```bash
git cherry-pick b09cc78^..58eab41
```

### История optional modules на текущей ветке

```
51811dd  Add Russian UI localization and V1 navigation map
e1215b8  Merge runtime diagnostics review fixes
58eab41  Review and harden runtime diagnostics tools
b09cc78  Add optional runtime diagnostics tools module
7caa6e2  Add optional debug log tools module
f9b0396  Move phone test diagnostics to optional module
6eb3c0c  Add phone test diagnostics for native automation
f60ccad  Fix compile blockers for native automation APK
```

---

## 7. Проверки, которые прошли

| # | Проверка | Результат |
|---|---|---|
| 1 | `git diff --check` (пробелы/trailing whitespace) | PASS — нет нарушений |
| 2 | `:app:compileDebugKotlin` | PASS — BUILD SUCCESSFUL (71 tasks) |
| 3 | Build 1: default assembleDebug (все modules) | PASS — BUILD SUCCESSFUL |
| 4 | Build 2: `includePhoneTestTools=false` | PASS — BUILD SUCCESSFUL, activity absent in manifest |
| 5 | Build 3: `includeDebugLogTools=false` | PASS — BUILD SUCCESSFUL, activity absent in manifest |
| 6 | Build 4: `includeRuntimeDiagnosticsTools=false` | PASS — BUILD SUCCESSFUL, provider+activity absent |
| 7 | Build 5: все три modules отключены | PASS — BUILD SUCCESSFUL, manifest чист |
| 8 | Статика: `:phone-test-tools` не зависит от `:app` | PASS — только `appcompat` |
| 9 | Статика: `:debug-log-tools` не зависит от `:app` | PASS — только `appcompat` |
| 10 | Статика: `:runtime-diagnostics-tools` не зависит от `:app` | PASS — только `appcompat` |
| 11 | Статика: `releaseImplementation` не используется для optional modules | PASS — только `debugImplementation` |
| 12 | Manifest check (default): все три activities/provider присутствуют | PASS |
| 13 | Manifest check (all disabled): ни одного optional entry | PASS |

---

## 8. Остаточные риски

### Средний риск

- **`android:exported="true"` на debug activities.** `PhoneTestDiagnosticsActivity`, `DebugLogActivity`, `RuntimeDiagnosticsActivity` запускаются извне (adb), что необходимо для QA, но расширяет attack surface на debug builds. Для production не критично — все три не попадают в release APK. Если потребуется ужесточить: поставить `exported="false"` и запускать через explicit intent.

- **ContentProvider auto-init.** `RuntimeDiagnosticsInitProvider` инициализируется до `Application.onCreate()`. Это штатная Android-практика (WorkManager, Firebase используют то же), но при ошибке в `RuntimeDiagnostics.install()` провайдер упадёт до любого UI. В `58eab41` (harden review) этот риск уже минимизирован.

### Низкий риск

- **`settings.gradle.kts` всегда включает все три modules.** `include(":phone-test-tools")` и т.д. присутствуют независимо от Gradle property. Это правильно — Gradle configure-time отделяется от dependency-time. Но означает, что `./gradlew projects` всегда покажет все три модуля, даже при `includeXxx=false`. Это не баг.

- **Нет CI матрицы для всех 5 build variants.** Проверки проведены вручную. При добавлении нового optional module легко забыть добавить его в CI-матрицу.

---

## 9. Рекомендации для следующего sprint: Hermes / OpenRouter

### Архитектурный паттерн (повторить из optional modules)

Hermes или OpenRouter inference engine нужно добавить по той же схеме:

```groovy
// app/build.gradle
def includeHermesRuntime = providers.gradleProperty("includeHermesRuntime").orElse("false").get().toBoolean()
if (includeHermesRuntime) {
    debugImplementation project(":hermes-runtime")
}
```

**Правила модуля:**
- Новый модуль `:hermes-runtime` (или `:openrouter-client`) — standalone library.
- Никакого `project(":app")` внутри.
- Только `debugImplementation` в `:app` (или `implementation` если нужен в release, но тогда отдельный productFlavor).
- ContentProvider для auto-init если нужна инициализация без изменения `Application.onCreate()`.

### Интеграционные точки (не трогать)

- `NativeAutomationEngine` — существующий inference pipeline, не ломать.
- `Moonshine fallback` — уже вынесен в отдельный optional compile.
- `BleIpBridge/P2P` — независимый транспортный слой.
- `PictureVm` / `AlbumDownloader` — media flow, не трогать.

### Предварительные шаги перед Hermes sprint

1. Убедиться, что `heycyan-core` composite build стабилен (`includeBuild`).
2. Определить: Hermes нужен только в debug или в release тоже?
   - Debug only → `debugImplementation` + Gradle property.
   - Release ready → отдельный `productFlavor` (hermesEnabled / hermesDisabled).
3. Проверить лицензию Hermes runtime (Meta/Facebook) на совместимость с проектом.
4. OpenRouter: только HTTP клиент (OkHttp уже есть) → добавить как implementation в :app напрямую или в отдельный `:openrouter-client` модуль.

### CI рекомендация

Добавить матрицу в CI:

```yaml
matrix:
  flags:
    - ""
    - "-PincludePhoneTestTools=false"
    - "-PincludeDebugLogTools=false"
    - "-PincludeRuntimeDiagnosticsTools=false"
    - "-PincludePhoneTestTools=false -PincludeDebugLogTools=false -PincludeRuntimeDiagnosticsTools=false"
```

---

*Документ создан Claude Code на ветке `ai/claude-v1-rollback-modularity-check`. Не коммитить без review.*
