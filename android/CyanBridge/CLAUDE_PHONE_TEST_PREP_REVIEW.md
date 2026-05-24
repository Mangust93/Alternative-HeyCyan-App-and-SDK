# Phone Test Prep — Review

**Branch:** `ai/codex-fix-compile-blockers`
**Date:** 2026-05-24
**Sprint goal:** Get a testable debug build + a clear manual test scenario for a real
phone-with-glasses run, without Tasker/AutoInput and without the Moonshine runtime.

---

## What changed

Minimal, additive, isolated changes only. No existing screen, view model, or
glasses/media/BLE/P2P flow was modified.

1. **New documentation:** `CLAUDE_PHONE_TEST_PLAN.md` — full manual test plan
   (build → install → permissions → screens → glasses → media → fallback → PASS/FAIL → logs).
2. **New in-app diagnostics screen:** `PhoneTestDiagnosticsActivity` — a read-only,
   fully programmatic (no XML/binding) debug activity that reports fallback state and
   offers one safe self-contained test action.
3. **Manifest registration** for that activity (debug entry point, launchable via adb).
4. **This review:** `CLAUDE_PHONE_TEST_PREP_REVIEW.md`.

### Diagnostics screen contents

- **APK build status** — package, versionName, versionCode, debuggable (read from
  `packageManager` / `applicationInfo`, static at runtime).
- **Tasker status** — installed? (`packageManager` lookup of `net.dinglisch.android.taskerm`)
  and plugin enabled? (existing `CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled`).
- **Native automation fallback status** — derived: ACTIVE when the Tasker path is not
  active (Tasker missing or plugin off).
- **Moonshine runtime status** — `MoonshineModelManager.isRuntimeAvailable()` →
  "UNAVAILABLE (expected for this build)".
- **"Open chat image fallback test"** — generates a small JPEG in the app's **own cache
  dir** (no runtime permission) and calls the existing `NativeAutomationEngine.handle(...)`
  with an `ImageReadyEvent`. This reuses the already-built fallback path; it does not
  modify or touch the real glasses/media flow.
- A note clarifying that the **real glasses/media test still runs through the existing
  scan → connect → capture flow**.

## Files changed

| File | Type | Note |
|------|------|------|
| `CLAUDE_PHONE_TEST_PLAN.md` | added | Manual phone test plan |
| `CLAUDE_PHONE_TEST_PREP_REVIEW.md` | added | This review |
| `app/src/main/java/com/fersaiyan/cyanbridge/ui/debug/PhoneTestDiagnosticsActivity.kt` | added | Isolated read-only diagnostics + safe fallback test |
| `app/src/main/AndroidManifest.xml` | modified | One `<activity>` entry for the diagnostics screen |

### Files explicitly NOT touched

`DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`,
`WifiP2pManagerSingleton`/P2P, `ChatThreadActivity`, `MainActivity`,
`NativeAutomationEngine`, and all SDK / media / glasses sources.

## Checks that passed

- `git diff --check` → clean (no whitespace/conflict markers).
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` → **BUILD SUCCESSFUL**.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace` → **BUILD SUCCESSFUL**.
- APK rebuilt: `app/build/outputs/apk/debug/app-debug.apk` (~103 MB, timestamp 14:50).

## Where the APK is

```
app/build/outputs/apk/debug/app-debug.apk
```

Build it with `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace`
(do **not** use the root-level `assembleDebug`, which can fail on `:moonshine-voice`).

## What to test on the phone first

1. **Install + launch** — `adb install -r app/build/outputs/apk/debug/app-debug.apk`,
   open the app, confirm it does not crash (Moonshine unavailable must be a no-op).
2. **Diagnostics screen** — launch
   `adb shell am start -n com.fersaiyan.cyanbridge/.ui.debug.PhoneTestDiagnosticsActivity`,
   confirm Moonshine = UNAVAILABLE, native fallback = ACTIVE (with Tasker off), then tap
   **"Open chat image fallback test"** → chat must open with the test image attached and
   the prompt prefilled.
3. **Glasses** — scan → connect → hold a connection.
4. **Media** — trigger a photo and confirm it reaches the app.
5. **Real fallback** — with Tasker off, trigger an image query from the glasses and
   confirm chat opens with the captured image attached.

(Full PASS/FAIL criteria and log-capture commands are in `CLAUDE_PHONE_TEST_PLAN.md`.)

## Remaining limitations

- **Moonshine voice runtime is intentionally unavailable** in this build
  (`isRuntimeAvailable()` returns false). Voice/transcription via Moonshine is not testable
  here; this was not restored (out of scope).
- **Root-level `./gradlew assembleDebug` can still fail** on `:moonshine-voice` because
  `third_party/moonshine/core/CMakeLists.txt` is missing. Use `:app:assembleDebug`.
- The diagnostics screen's fallback button proves the **engine path** (image → chat),
  not the **glasses capture** path. End-to-end photo/video/audio still requires real
  glasses via the existing flow.
- The diagnostics activity is `exported="true"` so it can be started via adb during
  testing. It is read-only and writes only to the app's private cache; consider flipping
  it to `exported="false"` (and launching from a connected debugger) before any release.
- Tasker regression path was not exercised here (no Tasker installed in this environment).

## Not done (per constraints)

No commit, no push, no broad refactor, no new architecture layers, no Moonshine
restoration, no new BLE stack, and no change to the real scan/connect/media flow.
