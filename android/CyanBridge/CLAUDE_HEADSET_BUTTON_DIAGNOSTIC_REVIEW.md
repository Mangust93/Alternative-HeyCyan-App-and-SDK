# CLAUDE Headset Button Diagnostic Review

## Goal

Answer one question for a tester: **do button presses on the glasses / headset
actually reach Android as key or media-button events?**

This is delivered as a new, optional, standalone diagnostic module
`:headset-button-tools`. It binds nothing to translation, and intentionally does
**not** touch `:conversation-translation`, the core app, the glasses SDK or the
media flow.

## What was created

```
headset-button-tools/
  build.gradle
  src/main/AndroidManifest.xml
  src/main/java/com/fersaiyan/cyanbridge/headset_button_tools/
    HeadsetButtonDiagnosticsActivity.kt
```

- `build.gradle` — `com.android.library`, namespace
  `com.fersaiyan.cyanbridge.headset_button_tools`, `compileSdk 35`, `minSdk 24`,
  Java/Kotlin 17. Only dependency is `androidx.appcompat:appcompat`. No `:app`
  dependency, no glasses SDK, no media libs (the `MediaSession` used is part of the
  Android framework).
- `AndroidManifest.xml` — declares `HeadsetButtonDiagnosticsActivity` with
  `exported="true"` strictly for `adb` launch during testing.
- `HeadsetButtonDiagnosticsActivity.kt` — the diagnostic screen (details below).

## Wiring

`settings.gradle.kts`
- Added `include(":headset-button-tools")` (always included so the module resolves;
  whether it ends up in the APK is controlled in `app/build.gradle`).

`app/build.gradle`
- New property-gated block, mirroring the other optional diagnostic modules:

  ```gradle
  def includeHeadsetButtonTools = providers.gradleProperty("includeHeadsetButtonTools").orElse("true").get().toBoolean()
  if (includeHeadsetButtonTools) {
      debugImplementation project(":headset-button-tools")
  } else {
      logger.lifecycle("includeHeadsetButtonTools=false; skipping :headset-button-tools dependency")
  }
  ```

- Default: **enabled** (`includeHeadsetButtonTools=true`).
- Wired as `debugImplementation` only → never in release builds.
- Disable explicitly with `-PincludeHeadsetButtonTools=false`.

## What the screen does

UI (all Russian, built programmatically — no resource files):
- Title: **"Диагностика кнопки гарнитуры"**.
- Instruction: **"Нажмите кнопку на очках/наушниках."**
- Status line: **"Статус: Ожидание события"** until the first event, then a running
  count.
- Scrollable list of the most recent events (newest first, capped at 200).
- **"Очистить"** button to clear the list.

## Events captured

Two capture paths run simultaneously:

1. **Focused key events** via `dispatchKeyEvent` (while the screen is foreground).
2. **`ACTION_MEDIA_BUTTON`** via a framework `android.media.session.MediaSession`
   that is set active with a non-`NONE` playback state, so the system routes
   media-button intents to its callback. The `KeyEvent` is extracted from
   `Intent.EXTRA_KEY_EVENT`.

Target keycodes are highlighted in the log with `<== ЦЕЛЕВОЕ`:

- `KEYCODE_HEADSETHOOK`
- `KEYCODE_MEDIA_PLAY_PAUSE`
- `KEYCODE_MEDIA_PLAY`
- `KEYCODE_MEDIA_PAUSE`
- `KEYCODE_VOICE_ASSIST`

Any other key event is still logged (with its keycode name) so the tester can see
whatever the device actually emits; only `ACTION_DOWN` is recorded to avoid doubling
each press with its key-up.

## How to open

```
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.headset_button_tools.HeadsetButtonDiagnosticsActivity
```

## Builds

- `git diff --check` — clean.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace` — module
  included.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludeHeadsetButtonTools=false`
  — module excluded.

(See the session summary for the actual build results.)

## Constraints / limitations

- **Diagnostic only.** No button is bound to translation or any app action; the
  screen only observes and displays events. `dispatchKeyEvent` calls `super` so
  default system handling is preserved.
- **Foreground key capture** only works while this Activity is on top. Buttons
  delivered as media-button intents can still be caught by the active `MediaSession`,
  but global background capture is intentionally out of scope.
- **`ACTION_MEDIA_BUTTON` is best-effort.** Routing depends on which app currently
  owns the active media session and how the OEM/firmware dispatches the button. If
  another app holds an active session, events may go there instead. `MediaSession`
  setup failures are caught and shown in the log rather than crashing.
- **In-memory log.** Events are not persisted; they reset when the Activity is
  destroyed. (This module deliberately does not reuse `:debug-log-tools` storage to
  stay fully standalone.)
- **Debug builds only.** Wired as `debugImplementation`; `exported="true"` exists
  solely for `adb` launch during testing.
```
