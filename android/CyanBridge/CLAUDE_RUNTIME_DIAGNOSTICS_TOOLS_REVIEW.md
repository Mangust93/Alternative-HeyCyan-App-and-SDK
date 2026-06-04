# CyanBridge — Runtime Diagnostics Tools module

**Branch:** `ai/claude-runtime-diagnostics-tools-module`
**Date:** 2026-05-24
**Module:** `:runtime-diagnostics-tools`

A standalone, optional Android library module that **auto-installs** lightweight runtime
instrumentation and captures **real** app runtime events to app-specific storage, then
renders them in a small debug screen. It produces a compact text "diagnostic bundle" a
tester can copy and paste into ChatGPT.

Unlike `:debug-log-tools` (which only records events callers explicitly hand it), this
module hooks framework-level signals automatically via a `ContentProvider`, with **no
change to `:app` code**:

- **app start** + build / device facts,
- **uncaught crashes** (records the crash, then delegates to the previous handler so the
  normal crash path is preserved),
- **activity lifecycle** transitions (created / resumed / paused / destroyed).

It does **not** simulate the SDK and never touches the real glasses / media / BLE / P2P
flow or the Moonshine runtime. Same modular-monolith pattern as `:phone-test-tools` and
`:debug-log-tools`: one APK, but the feature lives in an independent Gradle module wired
into `:app` as a `debugImplementation` only, and it can be toggled off without breaking
the app.

---

## What was created

```
runtime-diagnostics-tools/
├── build.gradle                                       # android library, minimal deps
└── src/main/
    ├── AndroidManifest.xml                            # declares init provider + activity
    └── java/com/fersaiyan/cyanbridge/runtime_diagnostics_tools/
        ├── RuntimeDiagnosticsStore.kt                 # real file-backed event store
        ├── RuntimeDiagnostics.kt                      # install(): start/crash/lifecycle hooks
        ├── RuntimeDiagnosticsInitProvider.kt          # auto-init ContentProvider (no-op data)
        └── RuntimeDiagnosticsActivity.kt              # debug UI (no XML, built in code)
```

- **namespace:** `com.fersaiyan.cyanbridge.runtime_diagnostics_tools`
- **compileSdk:** 35, **minSdk:** 24, Java/Kotlin **17** (matches `:app`)
- **dependencies:** `androidx.appcompat:appcompat` only — no `:app` dependency, no
  glasses SDK, no Moonshine runtime, no heavy logging library.

### `RuntimeDiagnosticsStore` (the real store)

| API | Behavior |
|-----|----------|
| `append(context, tag, message, throwable?)` | Writes one real event line. `throwable` (if any) is recorded compactly as `class: message @ topStackFrame`. Thread-safe. |
| `readRecent(context, maxLines = 300)` | Reads the last N event lines from disk (oldest first). |
| `clear(context)` | Deletes the on-disk log file. |
| `buildDiagnosticBundle(context)` | Builds a compact text bundle: app + device facts plus the most recent ~200 events. |

Storage and limits:

- File: `filesDir/runtime-diagnostics/runtime-events.log` (app-specific internal storage).
- Line format: `<timestamp> | <tag> | <message>` (one event per line; newlines in a
  message are flattened). Tags used by the module: `APP`, `CRASH`, `LIFECYCLE`,
  `HEARTBEAT`, `RuntimeUI`.
- Hard cap: **1 MB**. When an append would exceed the cap the oldest half of the file is
  dropped, so the log never grows without bound.

### `RuntimeDiagnostics.install(context)` (the instrumentation)

- Guarded by an `AtomicBoolean` — a second call is a no-op (safe double-install).
- Writes an **app start** event and a **build/device** event.
- Registers `Thread.setDefaultUncaughtExceptionHandler`: on an uncaught exception it
  records a **CRASH** event and then **always delegates to the previous handler** (or
  re-raises if there was none), so the app's normal crash behavior is preserved.
- If `applicationContext` is an `Application`, registers
  `ActivityLifecycleCallbacks` and records created / resumed / paused / destroyed by the
  activity's simple class name.
- Never throws: every step is wrapped so it cannot affect app startup.

### `RuntimeDiagnosticsInitProvider` (auto-init)

A `ContentProvider` whose `onCreate()` calls `RuntimeDiagnostics.install(context)`. Android
creates providers during process startup (before the first activity), so the module
self-installs with **no `:app` code change**. All data methods (`query`/`insert`/`update`/
`delete`/`getType`) are safe no-ops; the provider is `exported="false"` and serves no
content. Its authority is `${applicationId}.runtime-diagnostics-init`.

### `RuntimeDiagnosticsActivity` (the debug UI)

Built in code (no XML). Shows module-installed status, the log file path, the shown-event
count, and a scrollable, selectable view of recent events. Every button performs a real
action:

- **Add heartbeat event** — really writes a real module heartbeat event (used to verify
  the log pipeline writes to disk; it is a real event from this module, **not** a
  simulated SDK/glasses event).
- **Refresh** — really re-reads the log file from disk.
- **Copy runtime diagnostic bundle** — really copies the bundle to the system clipboard.
- **Clear logs** — really deletes the on-disk log file.

It has **no** launcher icon and is opened manually via adb (see below).

---

## How to enable (default)

The module is on by default. The `includeRuntimeDiagnosticsTools` Gradle property defaults
to `true`, so a normal debug build includes it (and the provider auto-installs at startup):

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
```

You can also be explicit:

```bash
./gradlew :app:assembleDebug -PincludeRuntimeDiagnosticsTools=true
```

## How to disable

Build the app without the runtime-diagnostics module:

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludeRuntimeDiagnosticsTools=false
```

When disabled, neither `RuntimeDiagnosticsActivity` nor `RuntimeDiagnosticsInitProvider`
is present in the merged manifest, nothing auto-installs, and the adb launch below returns
an error (activity not found). Everything else in the app is unaffected.

> Release builds never include the module (it is `debugImplementation` only). The activity
> is `exported="true"` purely for manual adb launching in debug; before any release either
> keep the module disabled or set `android:exported="false"` in
> `runtime-diagnostics-tools/src/main/AndroidManifest.xml`.

## How to open the screen (adb)

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.runtime_diagnostics_tools.RuntimeDiagnosticsActivity
```

The `applicationId` stays `com.fersaiyan.cyanbridge`; only the activity class lives in the
`com.fersaiyan.cyanbridge.runtime_diagnostics_tools` package.

## Where the log file lives

```
/data/data/com.fersaiyan.cyanbridge/files/runtime-diagnostics/runtime-events.log
```

(App-specific internal storage; the path is also printed on the diagnostics screen and in
the diagnostic bundle.) On a debuggable build you can pull it with:

```bash
adb exec-out run-as com.fersaiyan.cyanbridge cat files/runtime-diagnostics/runtime-events.log
```

## How to copy the diagnostic bundle

Open `RuntimeDiagnosticsActivity`, tap **Copy runtime diagnostic bundle**, then paste
(long-press → Paste) into ChatGPT. The bundle is plain text and contains:

- App package / versionName / versionCode / debuggable flag.
- Device manufacturer / model / Android version + API level.
- Log file path, size, shown-event count.
- The most recent runtime events (app start, build/device, crashes, lifecycle, heartbeat).

## What to send to ChatGPT

Paste the **runtime diagnostic bundle** (above). For a crash/ANR, also include the focused
logcat from `CLAUDE_PHONE_TEST_PLAN.md` §11. The bundle's `CRASH` lines give the exception
class, message and top stack frame, which is usually enough to point at the failing area;
the full logcat fills in the rest.

## Limitations

- Captures framework-level signals only: app start, build/device facts, uncaught crashes,
  and activity lifecycle. It does **not** auto-hook BLE / media / P2P / SDK internals (that
  would require `:app` call sites and is intentionally out of scope so the core flow stays
  untouched).
- An **ANR** (main thread hang, no exception) is not captured directly — the last events
  before the hang still help locate it. After a hang, reopen the screen and copy the bundle
  if the app restarts.
- The crash handler records the crash and then delegates to the previous handler; it does
  not suppress or alter the normal crash flow.
- The log is capped at 1 MB; older events are dropped past that.
- Internal app-specific storage only; no runtime storage permission is requested.

## Guarantees / what was NOT touched

`:runtime-diagnostics-tools` has no dependency on `:app` and does not import or reference:
`DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`/P2P,
`NativeAutomationEngine`, `MainActivity`, `ChatThreadActivity`, the glasses SDK flow, the
Moonshine fallback, `:phone-test-tools`, or `:debug-log-tools`. The real BLE / media / P2P
flow is unchanged; the module only observes lifecycle/crash signals the Android framework
already exposes.
