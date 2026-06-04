# Technical Audit: Tasker/AutoInput Replacement

**Date:** 2026-05-24  
**Scope:** First-iteration audit only. No code is changed in this document.  
**Branch:** `ai/claude-tasker-replacement-audit`

---

## 1. Where the app sends broadcasts to Tasker

### 1.1 Central send method

**File:** `app/src/main/java/com/fersaiyan/cyanbridge/MainActivity.kt` (line ~1219)

```kotlin
private fun sendAiBroadcast(
    type: String,
    path: String? = null,
    assistantMode: String = resolveEffectiveAiAssistantMode()
)
```

- **Action:** `"$packageName.AI_EVENT"` — resolved by `aiEventAction(packageName)` companion method (line ~224)
- **Flag:** `Intent.FLAG_INCLUDE_STOPPED_PACKAGES` (allows Tasker to receive even if stopped)
- **Extras:**

| Extra key    | Type   | Example values              | Notes                                     |
|--------------|--------|-----------------------------|-------------------------------------------|
| `"type"`     | String | `"image"`, `"voice"`        | Drives Tasker profile condition           |
| `"path"`     | String | `/sdcard/DCIM/Camera/Glasses_AI_<ts>.jpg` | Only for type=image; is the MediaStore-scanned path |
| `"assistant"` | String | `"Gemini"`, `"ChatGPT"`, `"Tasker"` | Mode that was active at send time |

### 1.2 Call sites

**A) Image path — `triggerAssistantImageQuery(imagePath, userQuestion)` (line ~2036)**  
Called when: BLE photo button (0x02 notify), test button, or BLE thumbnail ready.  
When `aiAssistantMode` is NOT ChosenProvider/PRO/LOCAL_AGENT and NOT CLI_RELAY:
1. Copies file to `DCIM/Camera/Glasses_AI_<ts>.jpg`
2. Calls `MediaScannerConnection.scanFile()` on the public copy
3. **Inside the scan callback** (when MediaStore has indexed it):
   ```kotlin
   runOnUiThread { sendAiBroadcast("image", path) }
   ```
   where `path` is the MediaStore-scanned absolute path.

**B) Voice path — `triggerAssistantVoiceQuery()` (line ~1976)**  
When `resolveEffectiveAiAssistantMode()` returns `AI_MODE_TASKER`:
```kotlin
sendAiBroadcast(type = "voice", assistantMode = AI_MODE_TASKER)
```
No `path` extra for voice.

### 1.3 Mode routing guard

`sendAiBroadcast` is only reached after all other providers are ruled out:
- `AgentProviderType.PRO_SUBSCRIPTION` → CliRelayClient (no broadcast)
- `AgentProviderType.LOCAL_AGENT` → LocalModelsProvider (no broadcast)
- `AiProviderPrefs.getProvider() == CLI_RELAY` → direct HTTP (no broadcast)
- Everything else falls through to the Tasker broadcast path

---

## 2. Where the app receives commands from Tasker

### 2.1 Action name and extra

**Companion object (line ~221):**
```kotlin
fun actionTaskerCommand(appPackageName: String): String =
    "$appPackageName.ACTION_TASKER_COMMAND"

const val EXTRA_TASKER_COMMAND = "tasker_command"
```

### 2.2 Handler method

**File:** `MainActivity.kt`, method `handleTaskerCommand(startIntent: Intent?)` (line ~2485)  
Called from: `onCreate(savedInstanceState)` (initial launch) and `onNewIntent(intent)` (re-launch via SINGLE_TOP).

Entry condition:
```kotlin
val isFromTaskerAction = startIntent.action == actionTaskerCommand(packageName)
val command = startIntent.getStringExtra(EXTRA_TASKER_COMMAND)
if (!isFromTaskerAction && command.isNullOrBlank()) return
```

**Supported commands (lowercase string in extra):**

| Command        | Effect                                              |
|----------------|-----------------------------------------------------|
| `scan`         | `binding.btnScan.performClick()`                   |
| `connect`      | `binding.btnConnect.performClick()`                |
| `disconnect`   | `binding.btnDisconnect.performClick()`             |
| `add_listener` | `binding.btnAddListener.performClick()`            |
| `set_time`     | `binding.btnSetTime.performClick()`                |
| `version`      | `binding.btnVersion.performClick()`                |
| `camera`       | `binding.btnCamera.performClick()`                 |
| `video`        | toggle video recording                              |
| `video_start`  | `controlVideoRecording(true)`                      |
| `video_stop`   | `controlVideoRecording(false)`                     |
| `record`       | `binding.btnRecord.performClick()`                 |
| `record_start` | `controlAudioRecording(true)`                      |
| `record_stop`  | `controlAudioRecording(false)`                     |
| `bt_scan`      | classic BT scan                                    |
| `battery`      | request battery level                              |
| `volume`       | read volume info                                   |
| `media_count`  | query media count on glasses                       |
| `data_download`| `binding.btnDataDownload.performClick()` → `startDataDownload()` |

### 2.3 Non-Tasker caller: AutoAudioCaptureService

**File:** `media/autocapture/AutoAudioCaptureService.kt`, method `triggerP2pSyncViaMainActivity()` (line ~364)

This service reuses the **same** `actionTaskerCommand` mechanism to trigger a P2P sync from a background service:
```kotlin
val intent = Intent(this, MainActivity::class.java).apply {
    action = MainActivity.actionTaskerCommand(packageName)
    putExtra(MainActivity.EXTRA_TASKER_COMMAND, "data_download")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
startActivity(intent)
```

This is **not** a Tasker broadcast — it is an in-process `startActivity()` that routes through `onNewIntent` → `handleTaskerCommand`.

---

## 3. Where the image/media flow is formed

### 3.1 BLE photo button (hardware trigger)

**File:** `MainActivity.kt`, `MyDeviceNotifyListener.parseData()` (line ~4340)

```
BLE notify cmdType=100, loadData[6] == 0x02
  → "AI Photo Button Pressed"
  → handleGlassesImageButtonPressed(triggerCapture = false, sourceTag = "glasses_signal")
```

### 3.2 handleGlassesImageButtonPressed → thumbnail download

**File:** `MainActivity.kt` (line ~1519)

1. Creates output file: `getExternalFilesDir("DCIM")/AI_Thumb_<sourceTag>_<ts>.jpg`
2. Calls `LargeDataHandler.getPictureThumbnails(callback)` — BLE transfer
3. Callback writes JPEG chunks to file; on `isComplete` → `onImageThumbnailReadyForQuestion(path)`
4. Fallback after 13 s timeout: `useLatestImageFallback()` → searches `DCIM/Camera/Glasses_AI_*.jpg` by most recent modification time

### 3.3 onImageThumbnailReadyForQuestion

**File:** `MainActivity.kt` (line ~1626)

1. Validates file: must exist, size ≥ 1000 bytes, age ≤ 3 min
2. `copyImageToPublicCamera(imagePath)` → copies to `DCIM/Camera/Glasses_AI_<ts>.jpg`
3. `MediaScannerConnection.scanFile()` on the public copy (so Gallery sees it)
4. Calls `triggerAssistantImageQuery(imagePath, userQuestion = null)`
5. After TTS reply finishes: `captureOptionalImageQuestionFromBluetoothMic()` → optional follow-up

### 3.4 triggerAssistantImageQuery routing

**File:** `MainActivity.kt` (line ~2036)

```
imageQueryInProgress guard (AtomicBoolean)
  → ChosenProvider + PRO or LOCAL_AGENT → triggerMemoryAwareImageQuery()
  → AiProviderPrefs == CLI_RELAY → CliRelayClient.imageQuery() → speak(reply)
  → else (Tasker path):
      1. copy to DCIM/Camera/Glasses_AI_<ts>.jpg
      2. MediaScannerConnection.scanFile()
      3. In scan callback: sendAiBroadcast("image", scannedPath)
```

### 3.5 P2P media download flow (AlbumDownloader equivalent)

**File:** `MainActivity.kt` (inline)

```
startDataDownload()
  → WifiP2pManagerSingleton.startPeerDiscovery()
  → LargeDataHandler.glassesControl(0x02,0x01,0x04) — triggers glasses WiFi/P2P
  → BLE notify 0x08 → onDownloadBleIp(ip)
  → WifiP2pCallback.onConnected(info) → onDownloadP2pConnected(info)
  → maybeStartHttpDownload()
      → HTTP GET http://<ip>/files/media.config
      → parseMediaList() → lists .jpg / .mp4 / .opus files
      → downloadAllMediaFiles()
          → downloadSingleJpgFile(fileName, deviceIp) → saves to DCIM
          → downloadSingleMp4File(fileName, deviceIp) → saves to DCIM
          → downloadSingleOpusFile(fileName, deviceIp) →
              GlassesSyncedAudioIngestor.persistDownloadedAudio()
                → saves to recordings/glasses_sync_<name>.ogg
                → inserts CaptureSession in Room DB
                → auto-transcription pipeline (Moonshine / Gemma LiteRT)
```

**BleIpBridge:** `bleIpBridge` is a singleton (referenced from `ui/bleIpBridge`) that caches the IP reported over BLE. `getDeviceIpFromBLE()` reads `bleIpBridge.ip.value`.

**MediaStore scan:** After JPG/MP4 downloads, `MediaScannerConnection.scanFile()` is called per file so the Gallery sees it (see `downloadSingleJpgFile` / `downloadSingleMp4File`).

---

## 4. Where auto-capture audio calls the Tasker path

### 4.1 AutoAudioCaptureService overview

**File:** `media/autocapture/AutoAudioCaptureService.kt`

- Foreground service (15-minute recording loops)
- Start: `glassesControl(0x02, 0x01, 0x08)`
- Stop: `glassesControl(0x02, 0x01, 0x0c)`
- After every N loops (`AutoAudioCapturePrefs.getLoopsPerSync()`): calls `triggerP2pSyncViaMainActivity()`

### 4.2 triggerP2pSyncViaMainActivity — the Tasker-path dependency

```kotlin
// AutoAudioCaptureService.kt:364
val intent = Intent(this, MainActivity::class.java).apply {
    action = MainActivity.actionTaskerCommand(packageName)
    putExtra(MainActivity.EXTRA_TASKER_COMMAND, "data_download")
    addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_CLEAR_TOP)
}
startActivity(intent)
```

**This is the only Tasker-path coupling in AutoAudioCaptureService.** It does not broadcast to the external Tasker app — it uses the `ACTION_TASKER_COMMAND` intent naming convention to route internally through `MainActivity.handleTaskerCommand()`.

**Events that should become native events (if replacing this path):**
- `AudioLoopCompletedEvent(loopIndex, audioFilePath)` — produced after every successful 15-min chunk
- `AudioSyncThresholdReachedEvent(loopCount)` — produced every N loops, triggers P2P sync
- `GlassesMediaReadyEvent` — produced when glasses WiFi P2P comes up and `media.config` is reachable

### 4.3 AutoAudioCapturePrefs pause flags

`AutoAudioCapturePrefs` manages: `enabled`, `pausedForMeeting`, `pausedForVideo`, `pauseUntilMs`, `loopsPerSync`, `visualNotesEnabled`, `speechExtendEnabled`.  
These are pure SharedPreferences — no Tasker dependency.

---

## 5. What Tasker_AI.xml actually does

**File:** `tasker/Tasker_AI.xml`

### 5.1 Profile: "Tasker AI" (prof6)

- **Trigger:** Intent Received event, action = `com.fersaiyan.cyanbridge.AI_EVENT`
- Tasker listens for this broadcast from **any** source (no package restriction)

### 5.2 Task: "Handle Glasses AI" (task15) — step by step

| Step | Code | Action |
|------|------|--------|
| act0 | 548  | Flash notification: "Glasses Triggered: %type." — `%type` is the `"type"` extra |
| act1 | 37   | **If** `%type` equals `"image"` |
| act2 | 365  | `LaunchAssistant()` — JavaScript plugin: opens Google Assistant |
| act3 | 107361459 | **AutoInput plugin** — executes UI automation sequence (see below) |
| act4 | 43   | **End If** |
| act5 | 365  | `LaunchAssistant()` — a second call (outside If, acts as else/fallback for voice?) |
| act6 | 38   | **End If** (outer) |

### 5.3 AutoInput UI automation sequence (act3)

AutoInput plugin (package `com.joaomgcd.autoinput`) runs in Google Assistant's UI:

```
waitForElement(id, assistant_robin_floaty_single_line_add_attachment_button)
click(id, assistant_robin_floaty_single_line_add_attachment_button)
  → opens attachment menu in Google Assistant

waitForElement(id, assistant_robin_add_file_attachment_button)
click(id, assistant_robin_add_file_attachment_button)
  → taps "Add file" / "Image from file"

waitForElement(id, com.google.android.documentsui:id/item_root)
click(id, com.google.android.documentsui:id/item_root)
  → clicks the first item in DocumentsUI file picker
    (relies on Glasses_AI_*.jpg being the most recent file in Gallery/Recents)

waitForElement(id, assistant_robin_floaty_single_line_query)
setText(id, assistant_robin_floaty_single_line_query, Tell me about this image)
  → types the question into Google Assistant's text field

wait(1000)
click(text, Send)
  → sends the query

waitForElement(id, assistant_robin_playback_icon)
click(id, assistant_robin_playback_icon)
  → starts audio playback of Assistant's spoken reply
```

**Pre-action delay:** 200 ms. **Check interval:** 1000 ms.

### 5.4 Key fragilities of the current Tasker/AutoInput path

1. Requires `BIND_ACCESSIBILITY_SERVICE` and AutoInput accessibility service running
2. Tied to internal view IDs of `com.google.android.googlequicksearchbox` — breaks on any Google app update
3. The file picker clicks `item_root` blindly — assumes the Glasses_AI file is first (relies on sort-by-date)
4. No error recovery: if any step times out, the whole sequence silently fails
5. Requires Tasker to be installed and the profile to be active

---

## 6. Proposed replacement architecture

### 6.1 Core abstractions

```
automation/
  NativeAutomationEngine.kt      — singleton/object: receives events, dispatches actions
  AutomationEvent.kt             — sealed class hierarchy of events
  AutomationAction.kt            — sealed class hierarchy of actions
  AutomationRule.kt              — maps Event → List<AutomationAction> (configurable later)
```

### 6.2 Event hierarchy

```kotlin
sealed class AutomationEvent {
    // Glasses hardware button: photo taken
    data class GlassesCaptureEvent(
        val captureType: CaptureType,  // PHOTO, VIDEO_START, VIDEO_STOP, AUDIO_START, AUDIO_STOP
        val triggerSource: String,     // "ble_button", "ble_auto", "test_button"
    ) : AutomationEvent()

    // Image file is ready and validated on device
    data class ImageReadyEvent(
        val imagePath: String,         // absolute path to validated JPEG
        val sourceTag: String,
    ) : AutomationEvent()

    // Audio loop completed on glasses (15-min chunk)
    data class AudioLoopCompletedEvent(
        val loopIndex: Int,
        val audioFilePath: String,
    ) : AutomationEvent()

    // P2P sync threshold reached
    data class SyncThresholdReachedEvent(
        val loopCount: Int,
    ) : AutomationEvent()

    // File downloaded from glasses via P2P
    data class MediaIngestedEvent(
        val fileType: MediaFileType,   // PHOTO, VIDEO, AUDIO
        val localPath: String,
        val sourceIp: String,
    ) : AutomationEvent()
}
```

### 6.3 Action hierarchy

```kotlin
sealed class AutomationAction {
    // MVP: open internal chat with image attached
    data class OpenChatWithImageAction(
        val imagePath: String,
        val initialPrompt: String = "Tell me about this image",
    ) : AutomationAction()

    // MVP: speak result via TTS
    data class SpeakReplyAction(val text: String) : AutomationAction()

    // MVP: trigger P2P download (replaces startActivity(data_download))
    object TriggerP2pSyncAction : AutomationAction()

    // Future: query AI and speak reply
    data class AiImageQuestionAction(
        val imagePath: String,
        val prompt: String,
        val provider: AgentProviderType,
    ) : AutomationAction()

    // Future: transcribe audio
    data class AiAudioTranscriptionAction(
        val audioPath: String,
        val sessionId: Long,
    ) : AutomationAction()

    // Future: local notification
    data class LocalNotificationAction(
        val title: String,
        val body: String,
    ) : AutomationAction()

    // Future: OpenRouter/Hermes-based image description
    data class OpenRouterImageAction(
        val imagePath: String,
        val modelId: String,
    ) : AutomationAction()
}
```

### 6.4 NativeAutomationEngine

```kotlin
object NativeAutomationEngine {
    fun handle(context: Context, event: AutomationEvent)
    // Synchronously dispatches to registered handlers
    // No external broadcasts, no Tasker, no AutoInput
}
```

Initial implementation: one when-expression matching events to hard-coded MVP actions. Rules can be made configurable later.

---

## 7. Minimum viable MVP without Tasker

### Goal

When the app receives `imagePath` (from BLE photo button or test button), instead of:
```kotlin
sendBroadcast(Intent("$packageName.AI_EVENT").apply { putExtra("type", "image"); putExtra("path", path) })
```

Do:
```kotlin
NativeAutomationEngine.handle(context, ImageReadyEvent(imagePath, sourceTag))
```

Inside `NativeAutomationEngine.handle()` for `ImageReadyEvent`:
```kotlin
val intent = Intent(context, ChatThreadActivity::class.java).apply {
    putExtra(ChatThreadActivity.EXTRA_ATTACHED_IMAGE_PATH, imagePath)
    putExtra(ChatThreadActivity.EXTRA_INITIAL_PROMPT, "Tell me about this image")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}
context.startActivity(intent)
```

**ChatThreadActivity already exists** (`ui/ChatThreadActivity.kt`). It needs one new extra (`EXTRA_ATTACHED_IMAGE_PATH`) to load an image into its composer on launch.

### What is NOT touched in MVP

- Google Assistant / AutoInput — removed entirely in this path
- `DeviceBindActivity` scan/connect logic — untouched
- `PictureVm` / `AlbumDownloader` — untouched (documented only)
- `GlassesSyncedAudioIngestor` — untouched
- P2P / BLE download flow — untouched
- SDK (`BleOperateManager`, `LargeDataHandler`) — untouched
- Tasker constants in `MainActivity.kt` — kept for backward compat (remove in iteration 3)

### Backward compat during transition

`sendAiBroadcast()` still exists. The mode guard in `triggerAssistantImageQuery()` becomes:

```kotlin
AgentProviderType.TASKER → {
    // Legacy: send broadcast to external Tasker if installed
    // Native: if Tasker not installed or plugin disabled, use NativeAutomationEngine
    if (isTaskerInstalled() && CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled(this)) {
        // existing broadcast path
    } else {
        NativeAutomationEngine.handle(this, ImageReadyEvent(imagePath, "tasker_fallback"))
    }
}
```

This lets users with Tasker continue working unchanged.

---

## 8. Files to change in iteration 2

### New files to create

| File | Purpose |
|------|---------|
| `automation/AutomationEvent.kt` | Sealed class hierarchy |
| `automation/AutomationAction.kt` | Sealed class hierarchy |
| `automation/NativeAutomationEngine.kt` | Engine object, initial when-dispatch |

### Existing files to modify

| File | Change |
|------|--------|
| `MainActivity.kt` | In `triggerAssistantImageQuery()`: add `NativeAutomationEngine.handle()` branch for Tasker fallback (~line 2106–2156) |
| `MainActivity.kt` | In `triggerAssistantVoiceQuery()`: add native voice branch (~line 2003–2006) |
| `AutoAudioCaptureService.kt` | Replace `triggerP2pSyncViaMainActivity()` body: emit `SyncThresholdReachedEvent` → `NativeAutomationEngine.handle()` instead of `startActivity(ACTION_TASKER_COMMAND)` (~line 364–391) |
| `ui/ChatThreadActivity.kt` | Add `EXTRA_ATTACHED_IMAGE_PATH` and `EXTRA_INITIAL_PROMPT` extras; load image into composer on launch |

### Files NOT to touch in iteration 2

- `DeviceBindActivity.kt` — scan/connect logic
- `PictureVm.kt` — P2P group + media download
- `AlbumDownloader.kt` — HTTP download
- `BleIpBridge` / `WifiP2pManagerSingleton` — networking
- `GlassesSyncedAudioIngestor.kt` — audio ingest pipeline
- `tasker/Tasker_AI.xml` — keep for documentation / Tasker users
- SDK sources (`BleOperateManager`, `LargeDataHandler`, `DeviceManager`)

---

## Appendix A: Broadcast action names summary

| Constant | Value | Direction |
|----------|-------|-----------|
| `aiEventAction(pkg)` | `com.fersaiyan.cyanbridge.AI_EVENT` | App → Tasker (outbound broadcast) |
| `actionTaskerCommand(pkg)` | `com.fersaiyan.cyanbridge.ACTION_TASKER_COMMAND` | Tasker/Service → App (inbound Activity intent) |
| `AutoAudioCaptureService.ACTION_START` | `com.fersaiyan.cyanbridge.action.AUTO_AUDIO_CAPTURE_START` | Internal service control |
| `AutoAudioCaptureService.ACTION_STOP` | `com.fersaiyan.cyanbridge.action.AUTO_AUDIO_CAPTURE_STOP` | Internal service control |

## Appendix B: AgentProviderType routing table

| `AgentProviderType` | Image query path | Voice query path |
|---------------------|-----------------|-----------------|
| `PRO_SUBSCRIPTION`  | `CliRelayClient.imageQuery()` | `CliRelayClient.voiceQuery()` |
| `LOCAL_AGENT`       | `LocalModelsProvider.streamChat()` with `imagePaths` | `LocalModelsProvider.streamChat()` |
| `TASKER`            | `sendBroadcast(AI_EVENT, type=image)` | `sendBroadcast(AI_EVENT, type=voice)` |

When `AiProviderPrefs.getProvider() == CLI_RELAY` (legacy override), both image and voice bypass the AgentProviderType table entirely and go direct to the HTTP relay.
