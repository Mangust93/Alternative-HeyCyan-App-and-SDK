# CyanBridge V1 — Project Map

> Documentation only. This file does not change any app behaviour, flags, or modules.
> Date: 2026-05-31 · Branch: `ai/claude-v1-project-map-test-matrix`

## 1. Purpose of CyanBridge

CyanBridge is an Android companion app for the HeyCyan smart glasses. It binds to the
glasses over the HeyCyan SDK / BLE path and exposes media, automation, and assistant
features on the phone. V1 reorganises the codebase from separate ADB-only modules into
**one CyanBridge app built from loosely-coupled components**: a stable core plus a set of
optional feature/diagnostic modules that can be toggled in or out of the build without
breaking the core.

## 2. Current Architecture

```
CyanBridgeManagerApp (Gradle root)
├── :app                       ← core application (always built)
├── :LIB_GLASSES_SDK           ← HeyCyan glasses SDK (core dependency)
├── :moonshine-voice           ← vendored native voice wrapper (core dependency)
│
├── :conversation-translation  ← optional feature  (gated implementation)
├── :headset-button-tools      ← optional diagnostic (debugImplementation)
├── :glasses-button-event-tools← optional diagnostic (debugImplementation)
├── :debug-log-tools           ← optional diagnostic (debugImplementation)
├── :runtime-diagnostics-tools ← optional diagnostic (debugImplementation)
└── :phone-test-tools          ← optional diagnostic (debugImplementation)
```

Optional modules are reached only through the **Tools / Diagnostics shell**
(`Settings → Инструменты / Диагностика`), which launches each module by **implicit Intent
action scoped to the app package** — never by importing the module's Activity class.

### Core app (`:app`)
The stable surface the user always gets: device binding, glasses media/photo/video/audio
flow, chat thread, native automation engine, local agent, and the Settings screen. Core
code does **not** directly import Activity classes from optional modules.

Key entry points for the V1 shell:
- `app/.../ui/SettingsActivity.kt` — Settings card "Инструменты / Диагностика" →
  `startActivity(Intent(this, ToolsActivity::class.java))`.
- `app/.../ui/tools/ToolsActivity.kt` — renders the 6 feature cards, resolves each action,
  disables cards whose module is not in the build, and catches launch failures with a Toast.
- `app/.../ui/tools/FeatureIntents.kt` — the implicit Intent action constants (the only
  contract between core and modules).

### Optional modules
Each optional module ships its own Activity declared with `exported=false` and an
`<intent-filter>` matching the corresponding `FeatureIntents` action. None of them is
imported by core code at compile time.

| Module | Feature action | Card |
| --- | --- | --- |
| `:conversation-translation` | `…feature.CONVERSATION_TRANSLATION` | Перевод диалога |
| `:headset-button-tools` | `…feature.HEADSET_BUTTON_DIAGNOSTIC` | Диагностика кнопки гарнитуры |
| `:glasses-button-event-tools` | `…feature.GLASSES_BUTTON_EVENT_DIAGNOSTIC` | Диагностика кнопок очков |
| `:debug-log-tools` | `…feature.DEBUG_LOGS` | Debug-логи |
| `:runtime-diagnostics-tools` | `…feature.RUNTIME_DIAGNOSTICS` | Runtime-диагностика |
| `:phone-test-tools` | `…feature.PHONE_TEST_TOOLS` | Тесты телефона |

(Action prefix: `com.fersaiyan.cyanbridge`.)

## 3. Weak Coupling Model

The architecture rule for V1:

1. **One app, many weakly-coupled components.** Optional modules can be disabled or broken
   without crashing the core app.
2. **No direct class imports.** Core must not import Activity classes from optional modules.
3. **Intent-action contract.** Feature opening goes through
   `Intent(action).setPackage(packageName)` → `packageManager.resolveActivity(...)` →
   safe launch wrapped in `runCatching { startActivity(...) }`.
4. **Graceful absence.** If a module is toggled out of the build, its action does not
   resolve; the card shows "Модуль не включён" and is disabled. The app keeps working.
5. **Exported=false.** Optional feature/diagnostic activities are not exported; they are
   only reachable from inside the app's own package.

## 4. Branch / Checkpoint Map

| # | Checkpoint | Branch | Commit | Module | Status |
| --- | --- | --- | --- | --- | --- |
| 1 | Translation V1.1 | `ai/claude-conversation-translation-mlkit` | `2618f2b` | `:conversation-translation` | phone-tested, works |
| 2 | Headset/media button diagnostic | `ai/claude-headset-button-diagnostic` | `6afad27` | `:headset-button-tools` | implemented / reviewed |
| 3 | Glasses button event diagnostic | `ai/claude-glasses-button-event-diagnostic` | `b8a5a02` | `:glasses-button-event-tools` | implemented / reviewed |
| 4 | Feature integration shell | `ai/claude-feature-integration-shell` | `2cab6df` | `:app` shell + 6 modules | built, **phone-test postponed** |

Feature integration shell commit trail:
- `71ef5c2` Add loosely coupled feature integration shell
- `94f1403` Add feature integration shell source files
- `c9326e7` Merge feature integration shell review fixes
- `2cab6df` Make conversation translation a gated app feature

Current docs branch: `ai/claude-v1-project-map-test-matrix` (this file).

## 5. Gradle Flags

All six flags default to **`true`**. Each gates whether the matching module is included
in the build. Toggle with `-P<flag>=false` on the Gradle command line.

| Gradle property | Module | Wiring | Default |
| --- | --- | --- | --- |
| `includeConversationTranslation` | `:conversation-translation` | gated `implementation` (in debug **and** release when flag is on) | `true` |
| `includeHeadsetButtonTools` | `:headset-button-tools` | `debugImplementation` (debug only) | `true` |
| `includeGlassesButtonEventTools` | `:glasses-button-event-tools` | `debugImplementation` (debug only) | `true` |
| `includeDebugLogTools` | `:debug-log-tools` | `debugImplementation` (debug only) | `true` |
| `includeRuntimeDiagnosticsTools` | `:runtime-diagnostics-tools` | `debugImplementation` (debug only) | `true` |
| `includePhoneTestTools` | `:phone-test-tools` | `debugImplementation` (debug only) | `true` |

Notes:
- The flags are read in both `settings.gradle.kts` (whether the module is `include`d) and
  `app/build.gradle` (whether `:app` depends on it).
- **Conversation translation is a gated app feature**, not `debugImplementation`: when
  `includeConversationTranslation=true` it is wired as `implementation`, so it can appear in
  release builds. The five diagnostic modules remain `debugImplementation` (debug only).

Examples:
```bash
# Default build (all six on)
./gradlew :app:assembleDebug

# Drop a single diagnostic
./gradlew :app:assembleDebug -PincludeGlassesButtonEventTools=false

# Release without the gated translation feature
./gradlew :app:assembleRelease -PincludeConversationTranslation=false
```

## 6. Test / Maturity Status

### Phone-tested (works on a real phone + glasses)
- **Translation V1.1** (`:conversation-translation`, `2618f2b`): RU→EN, EN→RU, on-device
  ML Kit translation, TTS, and the UX fix all confirmed on phone. No Hermes, no OpenRouter.

### Build-tested only (compiled, reviewed, not yet phone-verified)
- **Feature integration shell** (`2cab6df`): built successfully; APK copied to
  `/root/cyanbridge-feature-integration-shell.apk`. **Not installed / phone-tested yet.**
- **Headset/media button diagnostic** (`:headset-button-tools`, `6afad27`): implemented and
  reviewed. During phone-test, standard Android media/headset events **did not** appear from
  the glasses.
- **Glasses button event diagnostic** (`:glasses-button-event-tools`, `b8a5a02`): implemented
  and reviewed. Passive logcat / DeviceNotify diagnostic **did not** show useful events during
  phone-test.
- `:debug-log-tools`, `:runtime-diagnostics-tools`, `:phone-test-tools`: build-tested
  diagnostics, reachable through the shell; not re-verified on phone in the V1 shell.

### Experimental / investigative
- **Direct SDK/BLE notify bridge** for glasses button control. Glasses buttons do not arrive
  as Android key/media events; they arrive over the HeyCyan SDK BLE notify path (logged under
  the `DeviceNotify` tag). Any future bridge here must be **read-only** investigation.

### Postponed
- **Phone-test of the feature integration shell APK.** The APK exists on the server but has
  not been installed/run on the phone. Do **not** mark it as phone-tested. The next phone-test
  must open `Settings → Инструменты / Диагностика` and verify all 6 cards.

## 7. Out of Scope (do not modify in V1 doc/shell work)

DeviceBindActivity · PictureVm · AlbumDownloader · BleIpBridge/P2P · NativeAutomationEngine ·
MainActivity core flow · ChatThreadActivity · glasses SDK flow · media/photo/video/audio flow ·
Moonshine fallback · ConversationTranslationActivity logic · diagnostics logic.

See also: [CYANBRIDGE_V1_TEST_MATRIX.md](CYANBRIDGE_V1_TEST_MATRIX.md) ·
[CYANBRIDGE_V1_ROADMAP.md](CYANBRIDGE_V1_ROADMAP.md)
