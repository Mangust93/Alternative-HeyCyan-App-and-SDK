# Modular Phone-Test Tools — Review

**Branch:** `ai/claude-modular-phone-test-tools`
**Date:** 2026-05-24
**Goal:** Move the phone-test / debug diagnostics out of `:app` into a separate,
easily toggled Android library module — without breaking the app or touching the
real glasses / media / automation flow.

---

## Summary

The `PhoneTestDiagnosticsActivity` previously lived directly in `:app`
(`app/src/main/java/.../ui/debug/PhoneTestDiagnosticsActivity.kt` + a manifest entry).
It is now a standalone Android library module, **`:phone-test-tools`**, included into
`:app` as a **`debugImplementation` only** and gated behind a Gradle property so it can
be turned off in one flag.

The module has **no compile dependency on `:app`**: it does not import any app classes,
does not link the Moonshine runtime, and does not reference the glasses SDK. It reaches
the rest of the app only at runtime, through stable, decoupled mechanisms.

## What was moved

- The diagnostics activity moved from
  `app/src/main/java/com/fersaiyan/cyanbridge/ui/debug/PhoneTestDiagnosticsActivity.kt`
  (package `com.fersaiyan.cyanbridge.ui.debug`)
  to
  `phone-test-tools/src/main/java/com/fersaiyan/cyanbridge/phone_test_tools/PhoneTestDiagnosticsActivity.kt`
  (package `com.fersaiyan.cyanbridge.phone_test_tools`).
- Its manifest declaration moved from `app/src/main/AndroidManifest.xml` to the module's
  own `phone-test-tools/src/main/AndroidManifest.xml` (merged into the debug APK).

## How it was decoupled (no module → app cycle)

The original activity called app-internal classes directly. Those calls were replaced
with standalone equivalents so the module needs nothing from `:app` at compile time:

| Original (app-internal) | Replacement (standalone) |
|-------------------------|--------------------------|
| `CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled(this)` | Reads the same `SharedPreferences` directly (`community_plugins` / `gemini_chatgpt_image_automation`). Same process + applicationId → same prefs file. |
| `MoonshineModelManager.isRuntimeAvailable()` | Best-effort reflection probe of that class; defaults to `false` (the expected state). The module never links the Moonshine runtime. |
| `NativeAutomationEngine.handle(..., ImageReadyEvent(...))` | Explicit `Intent` built with `setClassName(packageName, "com.fersaiyan.cyanbridge.ui.ChatThreadActivity")` and the literal extras `attached_image_path` / `initial_prompt`. Falls back to an on-screen instruction toast (`ActivityNotFoundException`) if chat can't be resolved. |

Behavior is unchanged for the tester: same read-only status report, same "Open chat
image fallback test" button that writes a JPEG to the app cache and opens chat with the
image attached and the "Tell me about this image" prompt prefilled. The real
glasses/media path is untouched.

## Files changed

| File | Type | Note |
|------|------|------|
| `phone-test-tools/build.gradle` | added | Android library module: `com.android.library` + Kotlin, namespace `com.fersaiyan.cyanbridge.phone_test_tools`, compileSdk 35, minSdk 24, Java/jvmTarget 17, single dep `androidx.appcompat`. |
| `phone-test-tools/src/main/AndroidManifest.xml` | added | Declares `PhoneTestDiagnosticsActivity` (`exported="true"` for adb, with a release warning comment). |
| `phone-test-tools/src/main/java/com/fersaiyan/cyanbridge/phone_test_tools/PhoneTestDiagnosticsActivity.kt` | added | Standalone, decoupled diagnostics activity. |
| `app/src/main/java/com/fersaiyan/cyanbridge/ui/debug/PhoneTestDiagnosticsActivity.kt` | **deleted** | Old in-app copy removed. |
| `app/src/main/AndroidManifest.xml` | modified | Removed the old `<activity>` entry; left an explanatory comment. |
| `app/build.gradle` | modified | Added gated `debugImplementation project(":phone-test-tools")` (default on; `-PincludePhoneTestTools=false` to disable). Release never includes it. |
| `settings.gradle.kts` | modified | Added `include(":phone-test-tools")`. |
| `CLAUDE_PHONE_TEST_PLAN.md` | modified | New §12 "Optional `:phone-test-tools` module" (enable/disable/adb) + updated adb command in §8. |
| `CLAUDE_PHONE_TEST_PREP_REVIEW.md` | modified | Added a note that diagnostics is now an optional module. |
| `CLAUDE_MODULAR_PHONE_TEST_TOOLS_REVIEW.md` | added | This review. |

### Explicitly NOT touched

`DeviceBindActivity`, `PictureVm`, `AlbumDownloader`, `BleIpBridge`/P2P,
`NativeAutomationEngine`, `MainActivity`, `ChatThreadActivity`, the glasses SDK flow,
the Moonshine fallback, and the optional `:moonshine-voice` dependency in
`app/build.gradle`.

## How to enable the module (default)

The module is on by default (`includePhoneTestTools` defaults to `true`):

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace
# or explicitly:
./gradlew :app:assembleDebug -PincludePhoneTestTools=true
```

## How to disable the module

```bash
./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhoneTestTools=false
```

When disabled, `PhoneTestDiagnosticsActivity` is not in the APK and the adb launch below
fails with "activity not found". Nothing else in the app changes.

## adb command to open diagnostics (module included)

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.phone_test_tools.PhoneTestDiagnosticsActivity
```

`applicationId` stays `com.fersaiyan.cyanbridge`; only the activity class moved to the
`com.fersaiyan.cyanbridge.phone_test_tools` package.

## Build results

All run from `android/CyanBridge` on branch `ai/claude-modular-phone-test-tools`:

- `git diff --check` → **clean** (no whitespace errors / conflict markers).
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` →
  **BUILD SUCCESSFUL** (`:phone-test-tools:compileDebugKotlin` ran and compiled).
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace` (module included) →
  **BUILD SUCCESSFUL**. APK at `app/build/outputs/apk/debug/app-debug.apk` (~103 MB).
  Merged debug manifest contains
  `com.fersaiyan.cyanbridge.phone_test_tools.PhoneTestDiagnosticsActivity`.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhoneTestTools=false`
  (module excluded) → **BUILD SUCCESSFUL**. Merged manifest contains **no**
  `phone_test_tools` activity (0 matches), confirming the toggle works.

## Limitations

- `exported="true"` is intentional, for manual adb launching during testing. Before any
  release, either disable the module (`-PincludePhoneTestTools=false`) or set
  `android:exported="false"` in `phone-test-tools/src/main/AndroidManifest.xml`. Release
  builds already never include it (`debugImplementation` only).
- The decoupling relies on stable string identifiers (the `community_plugins` prefs key,
  the `ChatThreadActivity` class name, and its extra keys). If those are renamed in
  `:app`, update the mirrored constants at the top of `PhoneTestDiagnosticsActivity.kt`.
  The chat button degrades gracefully (instruction toast) if the class can't be resolved.
- The Moonshine probe is reflection-based and reports `false` whenever the class/method
  is absent — which is the expected "UNAVAILABLE" state for this build.
- The diagnostics button proves the **image → chat** path, not glasses capture; full
  photo/video/audio still requires real glasses via the existing flow.
- Root-level `./gradlew assembleDebug` can still fail on `:moonshine-voice`
  (missing `third_party/moonshine/core/CMakeLists.txt`); build `:app` directly.
