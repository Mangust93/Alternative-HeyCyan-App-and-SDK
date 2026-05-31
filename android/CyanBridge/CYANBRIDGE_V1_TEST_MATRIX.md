# CyanBridge V1 — Test Matrix

> Documentation only. No app behaviour, flags, or modules are changed by this file.
> Date: 2026-05-31 · Branch: `ai/claude-v1-project-map-test-matrix`

## How to read this

- **Build-tested** = compiles and is reviewed in the build.
- **Phone-tested** = installed and exercised on a real phone with glasses.
- All "How to open" paths go through `Settings → Инструменты / Диагностика` (the Tools shell),
  which launches each module by implicit Intent action; a module not in the build shows
  "Модуль не включён" and is disabled.
- Action prefix below is `com.fersaiyan.cyanbridge.feature.`.

## Matrix

| Feature / module | Branch / commit | Build-tested | Phone-tested | Expected behaviour | How to open | Gradle flag | Risk | Next test action |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **Translation V1.1** (`:conversation-translation`) | `ai/claude-conversation-translation-mlkit` / `2618f2b` | Yes | **Yes — works** | On-device dialog translation: speech → ML Kit Language ID → ML Kit Translation → TTS. RU→EN, EN→RU, TTS, UX-fix all confirmed. No Hermes, no OpenRouter. | Tools shell → **Перевод диалога** (`…CONVERSATION_TRANSLATION`) | `includeConversationTranslation` (gated `implementation`) | Low | Regression-check inside the integrated shell during the postponed phone-test. |
| **Feature integration shell** (`:app` shell + 6 cards) | `ai/claude-feature-integration-shell` / `2cab6df` | Yes — APK at `/root/cyanbridge-feature-integration-shell.apk` | **No — postponed** | Settings shows "Инструменты / Диагностика" card; shell lists 6 cards; unavailable modules disabled; no crash on missing module; launch failure → Toast. | `Settings → Инструменты / Диагностика` | (shell itself always built; per-card flags below) | Medium | **Install the APK and phone-test: open Settings → Инструменты / Диагностика and verify all 6 cards** (see checklist). |
| **Headset/media button diagnostic** (`:headset-button-tools`) | `ai/claude-headset-button-diagnostic` / `6afad27` | Yes | Tested — **no glasses media events** | Captures real key/media-button events (HEADSETHOOK / MEDIA_PLAY_PAUSE / MEDIA_PLAY / MEDIA_PAUSE / VOICE_ASSIST / ACTION_MEDIA_BUTTON). | Tools shell → **Диагностика кнопки гарнитуры** (`…HEADSET_BUTTON_DIAGNOSTIC`) | `includeHeadsetButtonTools` (`debugImplementation`) | Low (read-only) | Re-confirm from inside the shell; result expected negative — informs SDK/BLE bridge decision. |
| **Glasses button event diagnostic** (`:glasses-button-event-tools`) | `ai/claude-glasses-button-event-diagnostic` / `b8a5a02` | Yes | Tested — **no useful events** | Read-only: tails the app's own logcat for the `DeviceNotify` tag and decodes the `loadData[6]` opcode. Registers no SDK listener. | Tools shell → **Диагностика кнопок очков** (`…GLASSES_BUTTON_EVENT_DIAGNOSTIC`) | `includeGlassesButtonEventTools` (`debugImplementation`) | Low (read-only) | Re-confirm in shell; if button control is still needed, escalate to read-only direct SDK/BLE notify investigation. |
| **Debug-логи** (`:debug-log-tools`) | `ai/claude-debug-log-tools-module` | Yes | Not re-verified in shell | View recent diagnostic events and copy them. | Tools shell → **Debug-логи** (`…DEBUG_LOGS`) | `includeDebugLogTools` (`debugImplementation`) | Low | Smoke-open during the shell phone-test. |
| **Runtime-диагностика** (`:runtime-diagnostics-tools`) | `ai/claude-runtime-diagnostics-tools-module` | Yes | Not re-verified in shell | App start, build/device facts, crashes, activity lifecycle (auto-installed via ContentProvider). | Tools shell → **Runtime-диагностика** (`…RUNTIME_DIAGNOSTICS`) | `includeRuntimeDiagnosticsTools` (`debugImplementation`) | Low | Smoke-open during the shell phone-test. |
| **Тесты телефона** (`:phone-test-tools`) | `ai/claude-modular-phone-test-tools` | Yes | Not re-verified in shell | Manual phone testing/debug utilities. | Tools shell → **Тесты телефона** (`…PHONE_TEST_TOOLS`) | `includePhoneTestTools` (`debugImplementation`) | Low | Smoke-open during the shell phone-test. |

## Explicit V1 facts

- **Translation V1.1 is phone-tested and works** (RU→EN, EN→RU, TTS, UX-fix, on-device ML Kit).
- **Feature integration shell APK is built but the phone-test is postponed** —
  `/root/cyanbridge-feature-integration-shell.apk` is on the server, **not yet installed**.
  Do not mark it phone-tested.
- **Headset/media button diagnostic found no glasses media events** — standard Android
  media/headset events did not appear from the glasses during phone-test.
- **Glasses DeviceNotify/logcat diagnostic found no useful events** during phone-test.
- **The next phone-test must include `Settings → Инструменты / Диагностика` and all 6 cards.**

## Next phone-test checklist (feature integration shell)

1. Install `/root/cyanbridge-feature-integration-shell.apk` on the phone.
2. Open **Settings → Инструменты / Диагностика**.
3. Confirm all **6 cards** render: Перевод диалога, Диагностика кнопки гарнитуры,
   Диагностика кнопок очков, Debug-логи, Runtime-диагностика, Тесты телефона.
4. Confirm each available card opens its module; confirm a disabled card shows
   "Модуль не включён" and does not crash.
5. Re-run **Перевод диалога** end-to-end (RU→EN, EN→RU, TTS) from inside the shell to confirm
   no regression after integration.
6. Open each diagnostic card once (smoke test); note any crash or empty state.

See also: [CYANBRIDGE_V1_PROJECT_MAP.md](CYANBRIDGE_V1_PROJECT_MAP.md) ·
[CYANBRIDGE_V1_ROADMAP.md](CYANBRIDGE_V1_ROADMAP.md)
