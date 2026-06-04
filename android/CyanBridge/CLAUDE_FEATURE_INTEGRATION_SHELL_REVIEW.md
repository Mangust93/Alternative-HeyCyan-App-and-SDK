# Feature Integration Shell — Review

Branch: `ai/claude-feature-integration-shell`

Goal of this sprint: give the already-built optional feature modules a real, loosely-coupled
in-app entry point — a single "Tools / Diagnostics" screen — instead of being reachable only
through `adb`. The core app must keep working if a module is disabled, broken, or absent from
the build, and must not gain a compile-time dependency on any module's Activity classes.

---

## 1. Where the "Tools" screen lives

- New screen: **`com.fersaiyan.cyanbridge.ui.tools.ToolsActivity`** (`:app`, always present, even in
  release where the modules are not bundled).
- Entry point: **Settings → "Инструменты / Диагностика"** card with an **"Открыть инструменты"**
  button (`btn_open_tools`) at the top of the Settings scroll content.
  - Settings was chosen over a 6th bottom-nav tab: the bottom nav already has 5 items
    (the recommended max), and diagnostics is a natural fit under Settings. No redesign.
- The screen itself (`activity_tools.xml`) is a title + subtitle + a vertical list of feature
  cards inflated from `item_tool_card.xml`.

## 2. Cards added

Each card shows a title, a short description, a status line, and an "Открыть" button:

| Card (RU)                          | Module                          |
|------------------------------------|---------------------------------|
| Перевод диалога                    | `:conversation-translation`     |
| Диагностика кнопки гарнитуры       | `:headset-button-tools`         |
| Диагностика кнопок очков           | `:glasses-button-event-tools`   |
| Debug-логи                         | `:debug-log-tools`              |
| Runtime-диагностика                | `:runtime-diagnostics-tools`    |
| Тесты телефона                     | `:phone-test-tools`             |

Status is one of **"Доступно"** (module resolvable) or **"Модуль не включён"** (not in this build,
button disabled).

## 3. Intent actions used

The core app opens each module only through an implicit `Intent(action)` scoped to its own
package (`intent.setPackage(packageName)`). Actions are centralized in
`com.fersaiyan.cyanbridge.ui.tools.FeatureIntents`:

```
com.fersaiyan.cyanbridge.feature.CONVERSATION_TRANSLATION
com.fersaiyan.cyanbridge.feature.HEADSET_BUTTON_DIAGNOSTIC
com.fersaiyan.cyanbridge.feature.GLASSES_BUTTON_EVENT_DIAGNOSTIC
com.fersaiyan.cyanbridge.feature.DEBUG_LOGS
com.fersaiyan.cyanbridge.feature.RUNTIME_DIAGNOSTICS
com.fersaiyan.cyanbridge.feature.PHONE_TEST_TOOLS
```

Each module's Activity now declares a matching `<intent-filter>` with the action and
`<category android:name="android.intent.category.DEFAULT" />` (required so the implicit intent
resolves via `startActivity`). The activities stay `exported="true"` (unchanged) so `adb`
launch still works too.

## 4. How loose coupling is preserved

- **No compile-time dependency:** `:app` never imports a module Activity class. `ToolsActivity`
  references only `R.string.*` and the action strings in `FeatureIntents`. (`grep` for the module
  package names in `app/src/main` returns only `R.string.feature_*` resource names, no imports.)
- **Modules remain `debugImplementation` only** in `app/build.gradle` and gated behind their
  `include*` Gradle properties in `settings.gradle.kts` — this sprint did not change that wiring.
- **Resolve-before-show / resolve-before-launch:** every card calls
  `packageManager.resolveActivity(Intent(action).setPackage(packageName), 0)`. If it returns
  null, the card shows "Модуль не включён" and the button is disabled. Launch is also re-checked
  and wrapped in `runCatching`, surfacing a Toast (`tools_launch_failed`) on any failure.

## 5. Behaviour when an optional module is disabled/absent

- Module toggled off (e.g. `-PincludeConversationTranslation=false`) or stripped from a release
  build → its `<intent-filter>` is not present → `resolveActivity` is null → card renders as
  "Модуль не включён", button disabled. **Core app keeps working; no crash.**
- Module present but launch throws → caught, Toast shown, app stays alive.

## 6. Builds run

(Each: `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace [flags]`)

| # | Flags | Result |
|---|-------|--------|
| 1 | (none, all modules on) | BUILD SUCCESSFUL |
| 2 | `-PincludeConversationTranslation=false` | BUILD SUCCESSFUL |
| 3 | `-PincludeHeadsetButtonTools=false` | BUILD SUCCESSFUL |
| 4 | `-PincludeGlassesButtonEventTools=false` | BUILD SUCCESSFUL |
| 5 | `-PincludeConversationTranslation=false -PincludeHeadsetButtonTools=false -PincludeGlassesButtonEventTools=false` | BUILD SUCCESSFUL |

`git diff --check`: clean.

> Note on other flags: `:debug-log-tools`, `:runtime-diagnostics-tools` and `:phone-test-tools`
> are gated by `includeDebugLogTools` / `includeRuntimeDiagnosticsTools` / `includePhoneTestTools`
> in `app/build.gradle` (always `include`d in `settings.gradle.kts`, so disabling them is a
> dependency-only change). The Tools screen handles their absence the same way (card shows
> "Модуль не включён").

## 7. What remains (out of scope here)

- Nicer UI for the Tools screen (icons, grouping, Material 3 styling) — deferred.
- Wiring the glasses button to translation — only after button events are confirmed via the
  diagnostic module; intentionally not done this sprint.
- A shared navigation / feature registry so cards are data-driven across the app instead of a
  hard-coded list in `ToolsActivity`.

## 8. Files changed

New:
- `app/src/main/java/com/fersaiyan/cyanbridge/ui/tools/ToolsActivity.kt`
- `app/src/main/java/com/fersaiyan/cyanbridge/ui/tools/FeatureIntents.kt`
- `app/src/main/res/layout/activity_tools.xml`
- `app/src/main/res/layout/item_tool_card.xml`

Edited:
- `app/src/main/AndroidManifest.xml` (register `ToolsActivity`)
- `app/src/main/res/layout/activity_settings.xml` (Tools entry card)
- `app/src/main/java/com/fersaiyan/cyanbridge/ui/SettingsActivity.kt` (`bindToolsEntry`)
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-ru/strings.xml`
- 6 module manifests: `conversation-translation`, `headset-button-tools`,
  `glasses-button-event-tools`, `debug-log-tools`, `runtime-diagnostics-tools`,
  `phone-test-tools` (added `<intent-filter>` only — no logic touched).

Not touched (per constraints): glasses SDK flow, media flow, BLE/P2P, DeviceBindActivity,
PictureVm, AlbumDownloader, NativeAutomationEngine, ConversationTranslationActivity logic,
diagnostics logic.
