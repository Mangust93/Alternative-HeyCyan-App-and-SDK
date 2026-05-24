# CyanBridge — Phone Test Plan (no Tasker, no Moonshine)

**Branch:** `ai/codex-fix-compile-blockers`
**Date:** 2026-05-24
**Goal:** Validate the real basic glasses functionality on a physical phone using the
debug APK — without Tasker/AutoInput and without the Moonshine voice runtime.

This plan covers: build → install → permissions → screens → glasses → media flow →
native automation fallback → PASS/FAIL criteria → logs to capture on failure.

---

## 1. Build the APK

From the module directory `android/CyanBridge`:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
```

> Do **not** run the root-level `./gradlew assembleDebug`. It can fail on
> `:moonshine-voice` because `third_party/moonshine/core/CMakeLists.txt` is absent.
> This is a known limitation; for this test build only the `:app` module.

If you only want to verify compilation first:

```bash
./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace
```

## 2. Where the APK lives

```
app/build/outputs/apk/debug/app-debug.apk
```

(~103 MB.) Confirm it exists and is freshly built:

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

## 3. Install on the phone

Enable **Developer options → USB debugging** on the phone, connect it, then:

```bash
adb devices                       # confirm the phone is listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing build, keeping app data. For a clean run use
`adb install -r -d` or uninstall first with `adb uninstall com.fersaiyan.cyanbridge`.

App id: **`com.fersaiyan.cyanbridge`**.

## 4. Permissions to grant

Grant these when prompted (or pre-grant via Settings → Apps → CyanBridge → Permissions):

- **Nearby devices / Bluetooth** (scan + connect) — required for glasses.
- **Location** (some Android versions require it for BLE scanning).
- **Camera / Microphone** — only if you exercise capture features.
- **Notifications** (Android 13+) — needed for foreground capture services.
- **Storage / Photos & media** — for saving/reading synced media.

You can pre-grant the common ones via adb:

```bash
adb shell pm grant com.fersaiyan.cyanbridge android.permission.BLUETOOTH_SCAN
adb shell pm grant com.fersaiyan.cyanbridge android.permission.BLUETOOTH_CONNECT
adb shell pm grant com.fersaiyan.cyanbridge android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.fersaiyan.cyanbridge android.permission.POST_NOTIFICATIONS
```

## 5. Screens to open

1. **Welcome / Onboarding** → continue into the app.
2. **Device bind / scan screen** (`DeviceBindActivity`) — scan & connect to glasses.
3. **Main screen** (`MainActivity`) — capture controls / image-AI trigger.
4. **Chat** (`ChatThreadActivity`) — where the native automation fallback lands.
5. **Settings** (`SettingsActivity`) — provider type, auto-audio, privacy.
6. **Phone Test / Debug diagnostics** (debug-only, see §8) — fallback status check.
7. **Debug Log Tools** (debug-only, see §13) — capture/copy a diagnostic bundle.

---

## 6. What to check — Glasses (scan / connect)

| Step | Expected |
|------|----------|
| Open scan screen | Scan starts, nearby glasses appear in the device list. |
| Tap your glasses | App pairs/connects; connection state turns to connected. |
| Connection persists | Stays connected without immediate drop. |

**PASS:** glasses are found **and** connect and hold a connection.
**FAIL:** glasses never appear, or connect then immediately drop.

## 7. What to check — Media flow (photo / video / audio)

| Step | Expected |
|------|----------|
| Trigger a photo from the glasses | Photo transfers into the app and is viewable. |
| Trigger a video | Video transfers and is listed/playable. |
| Trigger / sync audio | Audio arrives and appears in recordings. |

Use the **existing** in-app flow (scan → connect → capture/sync). This plan does
**not** change that path.

**PASS:** at least photo transfer works end-to-end (find → connect → media in app).
**FAIL:** capture triggers but nothing arrives in the app.

## 8. What to check — Native automation fallback (Tasker-free)

The fallback replaces the old Tasker/AutoInput path for image questions. It engages
automatically when Tasker is **not** the active path (Tasker not installed **or** the
Gemini/ChatGPT image plugin disabled). When an image is ready it opens
`ChatThreadActivity` with the photo pre-attached and a "Tell me about this image" prompt.

### Quick status + isolated test (debug diagnostics screen)

A read-only diagnostics screen ships in the optional `:phone-test-tools` module
(debug build only — see §12). When the module is included (the default), launch it
directly:

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.phone_test_tools.PhoneTestDiagnosticsActivity
```

It shows, with no effect on the real glasses/media flow:

- **APK build status** — package, versionName, versionCode, debuggable.
- **Tasker** — installed? plugin enabled? Tasker path active/inactive.
- **Native automation fallback** — ACTIVE (Tasker-free) vs standby.
- **Moonshine runtime** — should read **UNAVAILABLE (expected for this build)**.
- **"Open chat image fallback test"** button — generates a small JPEG in the app
  cache and opens `ChatThreadActivity` via an explicit intent with the image
  attached and the prompt prefilled. This does **not** touch glasses/BLE/P2P/media.

### Real fallback path (through glasses)

1. Ensure Tasker is uninstalled/disabled (or the community plugin is off).
2. Connect glasses and trigger an image query (BLE photo button or the in-app
   "Test Image AI description" action).
3. Expected: `ChatThreadActivity` opens with the captured image attached and the
   prompt prefilled. Tapping Send dispatches to the configured AI provider.

**PASS:** the diagnostics button opens chat with an attached test image, **and** a real
image query (Tasker off) opens chat with the captured image attached.
**FAIL:** image query stops at a "setup required" warning or chat opens with no image.

## 9. What to check — Moonshine unavailable (must not crash)

- Diagnostics screen reports Moonshine runtime **UNAVAILABLE (expected)**.
- The app launches, navigates, and runs the glasses/media/fallback flows above
  **without** crashing due to the missing Moonshine runtime.

**PASS:** app is fully usable; Moonshine simply reports unavailable.
**FAIL:** any crash/ANR attributable to the missing Moonshine runtime.

---

## 10. PASS / FAIL summary

**Overall PASS** requires all of:

1. Glasses are **found**.
2. Glasses **connect** and hold.
3. At least **photo** media reaches the app (video/audio a bonus).
4. **Native automation fallback** opens chat with an attached image (Tasker off).
5. App runs with **Moonshine unavailable** and does not crash.

**Overall FAIL** if any of the above does not hold; record which step and capture logs (§11).

## 11. Logs to capture on failure

Capture a focused logcat right after reproducing the failure:

```bash
# Focused on the relevant tags
adb logcat -d -t 2000 \
  -s NativeAutomation:* AIHijack:* DataDownload:* DeviceNotify:* \
     WifiP2pManagerSingleton:* WifiP2pBroadcastReceiver:* BleIpBridge:* \
     CliRelayRouter:* LocalAgent:* ChatThreadActivity:* MainActivity:* \
     MoonshineModel:* AndroidRuntime:* > cyanbridge_failure.log
```

For a crash/ANR also grab the full buffer:

```bash
adb logcat -d > cyanbridge_full.log
adb bugreport cyanbridge_bugreport.zip   # optional, heavy
```

Note in the report: which step failed, the on-screen message, glasses model, Android
version, and whether Tasker was installed/enabled at the time.

> The in-app **Settings → Send Debug Logs** dialog also collects a focused logcat and
> uploads it to the relay if one is configured.

> You can also open the **Debug Log Tools** screen (§13) and tap **Copy diagnostic
> bundle** to grab a compact app + device + recent-events summary that pastes straight
> into ChatGPT. This complements (does not replace) the full logcat above.

---

## 12. Optional `:phone-test-tools` module

The diagnostics screen lives in a standalone Android library module,
`:phone-test-tools`, instead of in `:app`. The module has **no** dependency on `:app`,
never touches the real glasses/media/BLE/P2P flow, and does not link the Moonshine
runtime. It is wired into `:app` as a **`debugImplementation` only** (never in release).

### Enable (default)

The module is on by default. The `includePhoneTestTools` Gradle property defaults to
`true`, so a normal debug build includes it:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
```

You can also be explicit:

```bash
./gradlew :app:assembleDebug -PincludePhoneTestTools=true
```

### Disable

Build the app without the diagnostics module:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhoneTestTools=false
```

When disabled, the `PhoneTestDiagnosticsActivity` is **not** present and the adb launch
below returns an error (activity not found). Everything else in the app is unaffected.

> Release builds never include the module (it is `debugImplementation` only). The
> activity is `exported="true"` purely for manual adb launching in debug; before any
> release either keep the module disabled or set `android:exported="false"` in
> `phone-test-tools/src/main/AndroidManifest.xml`.

### Open the diagnostics screen (module included)

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.phone_test_tools.PhoneTestDiagnosticsActivity
```

The `applicationId` stays `com.fersaiyan.cyanbridge`; only the activity's class moved
to the `com.fersaiyan.cyanbridge.phone_test_tools` package.

---

## 13. Debug log tools module

A second standalone, optional Android library module, `:debug-log-tools`, captures
**real** diagnostic events to app-specific storage and renders them in a small debug
screen. Its **Copy diagnostic bundle** button produces a compact text summary (app +
device facts plus recent events) that pastes straight into ChatGPT. Like
`:phone-test-tools`, it has **no** dependency on `:app`, never touches the real
glasses/media/BLE/P2P flow, and is wired into `:app` as a **`debugImplementation` only**
(never in release). Full details: `CLAUDE_DEBUG_LOG_TOOLS_REVIEW.md`.

### Enable (default) / disable

```bash
# included by default
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace

# build without the module
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludeDebugLogTools=false
```

### Open the screen (module included)

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.debug_log_tools.DebugLogActivity
```

### Log file location

```
/data/data/com.fersaiyan.cyanbridge/files/debug-log-tools/debug-events.log
```

### Use during testing

1. **Before the test:** open `DebugLogActivity` and tap **Add test log event**. Tap
   **Refresh** and confirm the new event appears in the list — this verifies the log is
   actually being written to disk.
2. **Run the test** (glasses scan/connect, media, native automation fallback, §6–§9).
3. **On a failure / error:** open `DebugLogActivity`, tap **Copy diagnostic bundle**,
   and paste the bundle into ChatGPT (alongside the focused logcat from §11). Use
   **Clear logs** between runs if you want a clean capture.

> The module only records events explicitly written via `DebugLogStore.append(...)`. Out
> of the box that means the events created on this screen (open / test / copy / clear);
> wiring real subsystem call sites is a deliberate follow-up so the core flow stays
> untouched for this test build.
