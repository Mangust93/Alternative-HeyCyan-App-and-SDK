# CyanBridge — Debug Log Tools module

**Branch:** `ai/claude-debug-log-tools-module`
**Date:** 2026-05-24
**Module:** `:debug-log-tools`

A standalone, optional Android library module that captures **real** diagnostic events
to app-specific storage and renders them in a small debug screen. It produces a compact
text "diagnostic bundle" a tester can copy and paste into ChatGPT.

This is the same modular-monolith pattern as `:phone-test-tools`: one APK, but the
feature lives in an independent Gradle module that is wired into `:app` as a
`debugImplementation` only and can be toggled off without breaking the app.

---

## What was created

```
debug-log-tools/
├── build.gradle                                  # android library, minimal deps
└── src/main/
    ├── AndroidManifest.xml                        # declares DebugLogActivity (exported for adb)
    └── java/com/fersaiyan/cyanbridge/debug_log_tools/
        ├── DebugLogStore.kt                       # real file-backed logger
        └── DebugLogActivity.kt                    # debug UI (no XML, built in code)
```

- **namespace:** `com.fersaiyan.cyanbridge.debug_log_tools`
- **compileSdk:** 35, **minSdk:** 24, Java/Kotlin **17** (matches `:app`)
- **dependencies:** `androidx.appcompat:appcompat` only — no `:app` dependency, no
  glasses SDK, no Moonshine runtime, no heavy logging library.

### `DebugLogStore` (the real logger)

| API | Behavior |
|-----|----------|
| `append(context, tag, message, throwable?)` | Writes one real event line. `throwable` (if any) is recorded compactly as `class: message @ topStackFrame`. Thread-safe. |
| `readRecent(context, maxLines = 300)` | Reads the last N event lines from disk (oldest first). |
| `clear(context)` | Deletes the on-disk log file. |
| `buildDiagnosticBundle(context)` | Builds a compact text bundle: app + device facts plus the most recent ~200 events. |

Storage and limits:

- File: `filesDir/debug-log-tools/debug-events.log` (app-specific internal storage).
- Line format: `<timestamp> | <level>/<tag> | <message>` (level `E` when a throwable is
  attached, otherwise `I`). Newlines in a message are flattened so one event = one line.
- Hard cap: **512 KB**. When an append would exceed the cap the oldest half of the file
  is dropped, so the log never grows without bound.

### `DebugLogActivity` (the debug UI)

Built in code (no XML). Every button performs a real action:

- **Add test log event** — really writes an event to the log file.
- **Refresh** — really re-reads the log file from disk.
- **Copy diagnostic bundle** — really copies the bundle to the system clipboard.
- **Clear logs** — really deletes the on-disk log file.

It also shows the log file path and the current shown-event count, and a scrollable,
selectable view of the recent events.

It has **no** launcher icon and is opened manually via adb (see below).

---

## How to enable (default)

The module is on by default. The `includeDebugLogTools` Gradle property defaults to
`true`, so a normal debug build includes it:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
```

You can also be explicit:

```bash
./gradlew :app:assembleDebug -PincludeDebugLogTools=true
```

## How to disable

Build the app without the debug-log module:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludeDebugLogTools=false
```

When disabled, `DebugLogActivity` is **not** present and the adb launch below returns an
error (activity not found). Everything else in the app is unaffected.

> Release builds never include the module (it is `debugImplementation` only). The
> activity is `exported="true"` purely for manual adb launching in debug; before any
> release either keep the module disabled or set `android:exported="false"` in
> `debug-log-tools/src/main/AndroidManifest.xml`.

## How to open the screen (adb)

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.debug_log_tools.DebugLogActivity
```

The `applicationId` stays `com.fersaiyan.cyanbridge`; only the activity class lives in
the `com.fersaiyan.cyanbridge.debug_log_tools` package.

## Where the log file lives

```
/data/data/com.fersaiyan.cyanbridge/files/debug-log-tools/debug-events.log
```

(App-specific internal storage; the path is also printed on the diagnostics screen and
in the diagnostic bundle.) On a debuggable build you can pull it with:

```bash
adb exec-out run-as com.fersaiyan.cyanbridge cat files/debug-log-tools/debug-events.log
```

## How to copy the diagnostic bundle

Open `DebugLogActivity`, tap **Copy diagnostic bundle**, then paste (long-press →
Paste) into ChatGPT. The bundle is plain text and contains:

- App package / versionName / versionCode / debuggable flag.
- Device manufacturer / model / Android version + API level.
- Log file path, size, shown-event count.
- The most recent events (timestamps, tags, messages, brief throwables).

All of this is safe to paste into ChatGPT for help interpreting a failure.

## Limitations

- The module only records the events callers explicitly hand to `DebugLogStore.append`.
  It does **not** auto-hook BLE / media / P2P / SDK internals, and on its own (no app
  call sites added) the only events are the ones written from `DebugLogActivity` itself
  (open / test / copy / clear). To capture real subsystem events, call
  `DebugLogStore.append(...)` from those code paths (a follow-up, not done here so the
  core flow stays untouched).
- The bundle does **not** include a full logcat. For crash/ANR analysis still capture
  logcat as described in `CLAUDE_PHONE_TEST_PLAN.md` §11.
- The log is capped at 512 KB; older events are dropped past that.
- Internal app-specific storage only; no runtime storage permission is requested.

## Guarantees / what was NOT touched

`:debug-log-tools` has no dependency on `:app` and does not import or reference:
`DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`/P2P,
`NativeAutomationEngine`, `MainActivity`, `ChatThreadActivity`, the glasses SDK flow,
the Moonshine fallback, or `:phone-test-tools`. The real BLE / media / P2P flow is
unchanged.
