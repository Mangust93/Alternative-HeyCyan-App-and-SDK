# Native Automation MVP

**Branch:** `ai/claude-native-automation-mvp`
**Date:** 2026-05-24
**Scope:** Minimal NativeAutomationEngine for image-flow only. No Tasker removal, no wide refactoring.

---

## What was implemented

### New package: `automation/`

| File | Purpose |
|------|---------|
| `AutomationEvent.kt` | Sealed class with `ImageReadyEvent(imagePath, sourceTag)` and `SyncThresholdReachedEvent(loopCount)` |
| `AutomationAction.kt` | Sealed class with `OpenChatWithImageAction(imagePath, initialPrompt)` and `TriggerP2pSyncAction` |
| `NativeAutomationEngine.kt` | `object` with `handle(context, event)`: opens `ChatThreadActivity` for `ImageReadyEvent`; logs and no-ops for `SyncThresholdReachedEvent` |

### ChatThreadActivity changes

- Two new constants in `companion object`:
  - `EXTRA_ATTACHED_IMAGE_PATH = "attached_image_path"` — absolute path to a JPEG to pre-attach
  - `EXTRA_INITIAL_PROMPT = "initial_prompt"` — text to prefill the composer (only if input is empty)
- In `onCreate()` and `onNewIntent()`: an existing image path is added to `pendingImagePaths` once, the attachment UI is refreshed, and an initial prompt is inserted only while the composer is empty.
- Existing manual attach flow (`pickChatImageLauncher`) is untouched.
- The engine adds `FLAG_ACTIVITY_NEW_TASK` only when invoked with a non-`Activity` context.

### MainActivity changes

- Added imports: `AutomationEvent`, `NativeAutomationEngine`.
- In `triggerAssistantImageQuery()`, the Tasker path now has a guard:
  - If `isTaskerInstalled() && CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled(this)` → existing Tasker broadcast (unchanged).
  - Else → `NativeAutomationEngine.handle(this, ImageReadyEvent(imagePath, "tasker_fallback"))` → opens `ChatThreadActivity` with attached image.
- `sendAiBroadcast()` is preserved and still called for Tasker users.
- Image-query entrypoints continue into routing when Tasker/plugin is unavailable instead of stopping at the legacy setup warning.

### AutoAudioCaptureService changes

- Added a `TODO(iteration-3)` comment in `triggerP2pSyncViaMainActivity()` describing the future replacement with `NativeAutomationEngine.handle(..., SyncThresholdReachedEvent(...))`.
- The existing `ACTION_TASKER_COMMAND` route is untouched and continues to work.

---

## What remains legacy Tasker

| Area | Status |
|------|--------|
| `sendAiBroadcast()` in `MainActivity` | Kept; used when Tasker is installed + plugin enabled |
| `Tasker_AI.xml` | Not deleted |
| `handleTaskerCommand()` in `MainActivity` | Kept; used by AutoAudioCaptureService for P2P sync |
| `AutoAudioCaptureService.triggerP2pSyncViaMainActivity()` | Still uses `ACTION_TASKER_COMMAND` route |
| Voice query (`AI_MODE_TASKER` → `sendAiBroadcast("voice")`) | Unchanged |

---

## Files NOT touched

- `DeviceBindActivity.kt` — scan/connect logic
- `PictureVm.kt` / `AlbumDownloader.kt`
- `BleIpBridge` / `WifiP2pManagerSingleton`
- `GlassesSyncedAudioIngestor.kt`
- SDK sources (`BleOperateManager`, `LargeDataHandler`, `DeviceManager`)
- `tasker/Tasker_AI.xml`

---

## How to test (manual)

1. Build and install the app.
2. **Disable Tasker** (uninstall or kill) or **disable the community plugin** via Settings → Community Plugins.
3. Make sure you have any JPEG available on device (e.g. take a photo with the camera).
4. In `MainActivity`, trigger an image query — via:
   - BLE photo button (0x02 notify), or
   - "Test Image AI description" button in the UI.
5. Expected: `ChatThreadActivity` opens with:
   - The image shown in the attachments bar ("1 image(s) attached").
   - The prompt "Tell me about this image" pre-filled in the composer.
6. Tap Send — the message is dispatched to the configured AI provider.

### Tasker users (regression check)

1. Install Tasker and enable the community plugin.
2. Trigger an image query.
3. Expected: existing Tasker broadcast is sent; Google Assistant flow runs as before.

---

## Known issues / test results

`./gradlew test --no-daemon --no-watch-fs` was run on 2026-05-24 and fails with a **pre-existing** build environment issue, not related to this change:

```
Task :app:kaptDebugKotlin FAILED
bad class file: jetified-litertlm-android-0.10.0-api.jar (.../Backend.class)
  class file has wrong version 65.0, should be 61.0
```

**Root cause:** `litertlm-android-0.10.0-api.jar` was compiled with Java 21 (class file version 65.0).
The available Gradle/Javac execution is Java 17 compatible (class file version 61.0).
An attempted run using the documented `JAVA_HOME=/opt/android-studio/jbr` cannot start because that directory is absent in this environment.

**Impact on this change:** errors originate from generated stubs for `LiteRtLocalInferenceEngine` and do not reference the automation files or edited activities. A successful Java 21 test run was not available to verify here.

**Workaround:** build with Android Studio or set `org.gradle.java.home` to a valid JDK 21 path.
