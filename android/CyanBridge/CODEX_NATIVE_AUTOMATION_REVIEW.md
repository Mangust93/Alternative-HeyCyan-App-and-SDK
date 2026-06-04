# Codex Native Automation Review

`Date:` 2026-05-24
`Review branch:` `ai/codex-review-native-automation-mvp`
`Base:` `myfork/ai/claude-native-automation-mvp`

## Checked

- Reviewed Claude's automation model classes, engine, chat integration, MainActivity routing, service TODO, and MVP documentation.
- Confirmed the legacy Tasker broadcast method and Tasker constants remain in `MainActivity`.
- Confirmed `AutoAudioCaptureService` still routes sync through `ACTION_TASKER_COMMAND`.
- Confirmed no changes were made to DeviceBindActivity, PictureVm, AlbumDownloader, BleIpBridge, WifiP2pManagerSingleton, or `Tasker_AI.xml`.
- Reviewed Android transfer notes and did not modify BLE, HTTP media transfer, or P2P behavior.

## Findings

1. **Fixed - native fallback was unreachable from existing image entrypoints.**
   `MainActivity` still stopped at its legacy Tasker setup warning when Tasker or the plugin was absent, before `triggerAssistantImageQuery()` could invoke `NativeAutomationEngine`.
2. **Fixed - automation extras were not robust across activity delivery.**
   `ChatThreadActivity` handled image/prompt extras only in `onCreate()`. The shared helper now also handles `onNewIntent()` and deduplicates an already pending image path.
3. **Fixed - engine launch flags were broader than required.**
   `NativeAutomationEngine` always set `NEW_TASK | CLEAR_TOP`; it now sets `NEW_TASK` only for a non-`Activity` context and otherwise opens the chat normally.

No changes were needed in `AutomationEvent`, `AutomationAction`, or `AutoAudioCaptureService`.
`sendAiBroadcast()` and the legacy Tasker-enabled image branch remain active when Tasker is installed and its plugin preference is enabled.

## Changes Made

- Removed the obsolete Tasker-required early return from the two existing image-query entrypoints.
- Centralized intent image/prompt handling in `ChatThreadActivity` with existence checking, prompt empty-field guarding, and image deduplication.
- Conditioned `FLAG_ACTIVITY_NEW_TASK` on non-activity callers in `NativeAutomationEngine`.
- Updated `NATIVE_AUTOMATION_MVP.md` to describe the corrected lifecycle behavior and actual test result.

## Verification

- `git diff --check`: PASS (no whitespace errors).
- `git status --short`: only scoped source/document changes plus this new review report are present on the review branch.
- `./gradlew test --no-daemon --no-watch-fs`: FAILS before tests execute.
- Failure: `:app:kaptDebugKotlin` rejects `jetified-litertlm-android-0.10.0-api.jar` classes compiled as version `65.0` while the active toolchain accepts version `61.0`.
- Classification: pre-existing environment/dependency incompatibility; the reported stubs are for `LiteRtLocalInferenceEngine` and do not reference native automation changes.
- `JAVA_HOME=/opt/android-studio/jbr ./gradlew test --no-daemon --no-watch-fs`: cannot start because `/opt/android-studio/jbr` is absent in this environment.

## Phone Test Plan

1. Tasker plugin enabled: trigger an image query and verify the legacy broadcast path still works.
2. Tasker absent or plugin disabled: trigger an image query and verify `ChatThreadActivity` opens with the attached image.
3. Verify the prompt `Tell me about this image` appears when the composer is empty.
4. Attach an image manually in chat and verify the existing manual attach flow still works.
5. Verify glasses scan/connect and BLE plus Wi-Fi media transfer behavior is unchanged.
