# CODEX Photo Question OpenRouter Direct Review

Date: `2026-05-26`
Branch: `ai/codex-review-photo-question-openrouter-direct`
Reviewed feature commit: `2df48a5 Add OpenRouter direct mode for photo question feature`

## Result
Found and corrected two real unbounded-memory surfaces and defensively hardened key error display in optional module `:photo-question-tools`.
Scope remains restricted to that optional module plus this review document; no core/BLE/media/translation/diagnostics source was changed.

## Checked
- Diff from parent `40a28dd`, module source, manifest and conditional Gradle wiring.
- Dependency direction: the module declares no `project(":app")`; `:app` imports no `photo_question_tools` class.
- Key entry, masking, raw-input clearing, persistence and absence of hardcoded OpenRouter keys or key logging.
- HTTP endpoint, headers, UI model id, multimodal JSON data URL request and response parsing.
- Missing-key/model/photo/question, size, connectivity, timeout, HTTP and malformed-response behavior.
- Background work, main-thread callback, in-flight gating and destruction invalidation.
- ContentResolver URI read, MIME selection and bounded memory behavior.
- Enabled/disabled builds, manifest contribution and module exclusion.
- Sensitive outside scope: DeviceBindActivity, PictureVm, AlbumDownloader, BleIpBridge/P2P, NativeAutomationEngine, ConversationTranslationActivity and diagnostics modules.

## Fixes Applied
1. Removed `ImageView.setImageURI(uri)` decoding on the UI thread; it decoded arbitrary selected content before the 4 MB checked reader and could freeze or exhaust memory.
2. Limited OpenRouter response bodies to 1 MB instead of unbounded `readBytes()`.
3. Redacted an exact saved API key from surfaced network/server details and stopped exposing raw messages from unexpected request exceptions.

## Key Security
- The key is user-supplied at runtime and is not embedded by this feature in source or APK configuration.
- Saving settings clears `apiKeyInput`; clearing the key clears it again; only `maskedApiKey()` is shown afterwards.
- Feature code does not log key, settings or Authorization headers.
- The raw value is used for `Authorization: Bearer <key>` only; displayable returned detail is defensively redacted.
- `SharedPreferences` is accessed by `PhotoQuestionSettings` within the module only.
- Storage is intentionally plain SharedPreferences: rooted-device or backup extraction remains possible, and the UI warns users to use a limited key.

## OpenRouter Request Shape
Endpoint: `https://openrouter.ai/api/v1/chat/completions`.
Headers: `Authorization: Bearer <key>`, `Content-Type: application/json`, `Accept: application/json`.
Payload: `{"model":"<UI model>","messages":[{"role":"user","content":[{"type":"text","text":"<question>"},{"type":"image_url","image_url":{"url":"data:<mime>;base64,..."}}]}]}`.
Response parsing reads `choices[0].message.content`, accepting either string content or text parts.
The data-URL vision shape matches OpenRouter documentation: <https://openrouter.ai/docs/features/multimodal/images>.

## Error Handling
Before/network preparation handles missing key/model, unreadable or empty image, image larger than 4 MB and non-image MIME fallback to `image/jpeg`.
The client handles DNS/no internet, timeout, 400 model/image guidance, 401, 402, 429, 5xx, malformed/empty content and oversized responses.
Missing photo and empty question are blocked by the disabled `Спросить` button and guarded again before starting work; they are validation states rather than inline error messages.

## Lifecycle And Memory
Image reading and the OpenRouter call run on `PhotoQuestionResponder`'s background executor; callback UI writes run through a main-looper `Handler`.
`requestInFlight` prevents a repeated submission until a callback completes.
`onDestroy()` calls `shutdown()`, increments callback generation and prevents a late result from writing to the destroyed Activity.
Selected image bytes are streamed through `ContentResolver` with a 4 MB cap; arbitrary full-size UI preview decoding was removed.
HTTP response reading is bounded to 1 MB; the request still includes bounded base64 expansion of the selected image.

## Manifest Optionality
Default debug build contains `PhotoQuestionActivity`, `com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION` and `INTERNET`; merger attribution shows the activity/action from `:photo-question-tools`.
With `-PincludePhotoQuestionTools=false`, build succeeds, activity/action are absent and the merger report contains no module contribution.
`INTERNET` remains in the disabled final manifest because `app/src/main/AndroidManifest.xml` declares it independently, not because of this module.

## Loose Coupling
`:photo-question-tools` has no `:app`, SDK, BLE or networking-library dependency; HTTP uses platform `HttpURLConnection`, `org.json` and `Base64`.
Search of `app/src/main/java` finds no `photo_question_tools` imports; launch remains through the existing action-based feature shell.
No sensitive outside-scope implementation files listed above were changed relative to `40a28dd`.

## Verification
- `git diff --check`: passed.
- `rg -n "sk-or-" --glob '!**/.git/**' --glob '!**/build/**' .`: only hint/comments/review docs; no real key.
- `rg -n "photo_question_tools" app/src/main/java`: no matches.
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace`: passed.
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace`: passed.
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhotoQuestionTools=false`: passed.
- Manifest inspection: present by default; activity/action absent and no module merge contribution when disabled.
The instructed `/opt/android-studio/jbr` is absent in this workspace; installed OpenJDK 21 satisfies Java 17+ and was used.

## Residual Risks
- Plain SharedPreferences does not protect a key from rooted-device or backup extraction.
- OpenRouter Direct sends the selected image and question to OpenRouter/the selected provider.
- The 4 MB image becomes a larger in-memory JSON body after base64 encoding; its cost is bounded, not eliminated.
- Missing-photo/question feedback is disabled-state validation rather than an explicit message.

## Phone Test Checklist
1. Открыть `Инструменты / Диагностика`.
2. Открыть `Фото и вопрос`.
3. Проверить `Mock mode`.
4. Переключить `OpenRouter Direct`.
5. Ввести API key.
6. Ввести model id.
7. Сохранить настройки; проверить очистку raw input и masked key.
8. Выбрать фото.
9. Задать вопрос.
10. Получить ответ или понятную ошибку.
11. Очистить ключ.
