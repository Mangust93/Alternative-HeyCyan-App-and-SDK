# Codex Compile Blockers Review

## Branch

ai/codex-fix-compile-blockers

## Goal

Build a debug APK for testing the native automation MVP and glasses/media flow.

## Fixed compile blockers

1. Added compile-safe `AutoPairManager` fallback in `BluetoothReceiver.kt`.
   - Preserves auto reconnect suppression state.
   - Does not implement new BLE scan/connect behavior.
   - Keeps existing callers compiling.

2. Added Moonshine unavailable fallback.
   - `MoonshineModelManager` reports runtime unavailable when vendored Moonshine runtime is absent.
   - `MoonshineTranscriptionProvider` uses local fallback classes for missing Moonshine Java/JNI symbols.
   - All Moonshine runtime paths fail explicitly instead of pretending to work.

3. Made Moonshine native dependency optional in `app/build.gradle`.
   - If `../third_party/moonshine/core/CMakeLists.txt` is absent, app skips `:moonshine-voice`.
   - This allows app APK assembly for glasses/native automation testing.

## Validation

- `git diff --check`: passed.
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace`: passed.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace`: passed.

## APK

Generated APK:

`app/build/outputs/apk/debug/app-debug.apk`

Observed size:

`103M`

## Known limitation

Root-level `./gradlew assembleDebug` still attempts to build `:moonshine-voice` and fails when the vendored Moonshine native runtime is absent.

For current testing, use:

`./gradlew :app:assembleDebug`

## Phone test checklist

1. Install `app-debug.apk`.
2. Open app.
3. Confirm app starts without crash.
4. Test glasses scan/connect flow.
5. Test photo/media flow.
6. Test Tasker disabled/absent native automation fallback.
7. Confirm Moonshine transcription is unavailable gracefully and does not block glasses/media testing.
