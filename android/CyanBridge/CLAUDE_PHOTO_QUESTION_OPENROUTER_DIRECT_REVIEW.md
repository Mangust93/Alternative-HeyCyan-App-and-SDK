# Photo Question — OpenRouter Direct mode review

Branch: `ai/claude-photo-question-openrouter-direct`
Base: `ai/claude-photo-question-feature-shell`
Scope: only the optional, loosely-coupled `:photo-question-tools` module.

## What was added

OpenRouter **Direct** answer mode inside `:photo-question-tools`, alongside the existing
**Mock** placeholder (which stays the default and offline fallback). The user supplies their
own OpenRouter API key and model id at runtime, in the app; nothing is bundled in the APK.

New files (all in `photo-question-tools/src/main/java/.../photo_question_tools/`):

- `PhotoQuestionSettings.kt` — module-local persistence (mode, API key, model id) in a
  private SharedPreferences file. Provides a masked-key rendering and a clear-key op.
- `OpenRouterClient.kt` — dependency-free OpenRouter `chat/completions` client built on
  `HttpURLConnection` + `org.json` + `android.util.Base64`. Maps every failure to a
  user-readable (Russian) message via `PhotoQuestionException`.

Changed files:

- `PhotoQuestionResponder.kt` — now takes a `Context`, reads `PhotoQuestionSettings`, and
  routes each request to Mock or OpenRouter Direct. Reads the picked image safely with a
  size cap. Still background-thread + main-thread callback + generation-guarded cancel.
- `PhotoQuestionActivity.kt` — adds the settings panel (mode radios, key field, model field,
  Save / Clear-key buttons, masked-key status line, local-storage warning) plus mode-aware
  validation before sending.
- `src/main/AndroidManifest.xml` — adds `<uses-permission android:name="android.permission.INTERNET" />`
  to the module manifest only.

No `:app` source, glasses SDK, BLE/P2P/media, ConversationTranslationActivity, diagnostics
modules, DeviceBindActivity, PictureVm, AlbumDownloader, NativeAutomationEngine or feature
integration shell files were touched.

## Where the key is entered

`PhotoQuestionActivity` → switch the **Режим ответа** radio to **OpenRouter Direct (свой
ключ)** → the credentials panel appears → **OpenRouter API ключ** field → **Сохранить
настройки**. The raw field is cleared from the screen on save; the status line then shows a
masked form like `sk-or-...abcd`. **Очистить ключ** removes it from the device.

## Where the model id is entered

Same panel, **Model id** field (e.g. `openai/gpt-4o-mini`), persisted by the same **Сохранить
настройки** button. It is shown in full (it is not a secret) and survives a key clear.

## How Mock mode works

Default. `PhotoQuestionResponder` sleeps briefly and returns a deterministic local
placeholder that echoes the image name and question. No key, no network, no INTERNET use.
This is also the implicit fallback: if `mode` is unset/unknown it resolves to Mock.

## How OpenRouter Direct mode works

1. Activity validates a photo is picked, the question is non-empty, and (for this mode) a
   saved key and model id exist; otherwise it shows an inline error and does not send.
2. `PhotoQuestionResponder`, on its background thread, reads the image bytes from the picked
   `imageUri` via `contentResolver` (MIME from `getType(uri)`, fallback `image/jpeg`), capped
   at 4 MB.
3. `OpenRouterClient` POSTs to `https://openrouter.ai/api/v1/chat/completions` with
   `Authorization: Bearer <key>`, `Content-Type: application/json`, a `user` message
   combining the text question and an `image_url` whose URL is a `data:<mime>;base64,...`
   data URL.
4. The answer is `choices[0].message.content` (string, or concatenated text parts), trimmed
   and posted to the UI on the main thread.

## Seam for a future Hermes/server mode

`PhotoQuestionResponder.computeAnswer()` is the single branch point. A Hermes/server mode is
added as one more `PhotoQuestionSettings.Mode` entry and one more `when` branch there (plus
its own client) — `PhotoQuestionActivity`'s send path and the rest of the screen stay
unchanged. Not implemented in this iteration.

## How loose coupling is preserved

- `:photo-question-tools` declares no `project(":app")`, no glasses SDK, no BLE, no
  networking library — only `androidx.appcompat` (HTTP uses the platform `HttpURLConnection`
  + `org.json`, both part of Android).
- `:app` never imports module classes; it launches the screen by the package-scoped
  `com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION` action and checks `resolveActivity`, so it
  degrades gracefully when the module is absent. Verified: `grep -rn photo_question_tools app/src/`
  returns nothing.
- The module is wired as `debugImplementation` and only when `includePhotoQuestionTools`
  (default `true`). `-PincludePhotoQuestionTools=false` drops the module, its activity, its
  PHOTO_QUESTION action and its INTERNET line from the build.

## Risks / limitations of a user-provided API key

- Stored in **plain** SharedPreferences (no `EncryptedSharedPreferences`) to avoid pulling
  `androidx.security-crypto` + Tink into this optional module. A root user or an unencrypted
  device backup could read it. Mitigation surfaced in the UI: use a **separate, spend-limited
  key**, not a high-limit one; **Очистить ключ** removes it.
- The key is **never logged**, **never bundled**, and **never shown in full** after save
  (masked as `sk-or-...abcd`); it only ever appears in the `Authorization` header.
- The image is sent base64-inline to a third party (OpenRouter / the chosen model provider).
  Standard data-sharing caveat for any cloud vision call.
- 4 MB raw-image cap for this first version; larger images return a clear on-screen error
  instead of risking OOM. base64 inflates the request ~33%.

## Builds run (all passed)

- `git diff --check` → clean (`DIFF-CHECK-OK`).
- `./gradlew :app:compileDebugKotlin --no-daemon --no-watch-fs --stacktrace` → BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace` → BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug --no-daemon --no-watch-fs --stacktrace -PincludePhotoQuestionTools=false`
  → BUILD SUCCESSFUL.

Extra verification:

- `grep -R "sk-or-" -n .` → only comments / an input hint / the masking doc string; **no
  hardcoded key**.
- `grep -rn photo_question_tools app/src/` → nothing; `:app` does not import the module.
- Merged manifest (default debug): `PHOTO_QUESTION`, the module activity, and `INTERNET` all
  present.
- Merged manifest (`-PincludePhotoQuestionTools=false`): `PHOTO_QUESTION` and the module
  activity **absent**; `INTERNET` still present because the **host app declares it
  independently** in `app/src/main/AndroidManifest.xml` — the module's contribution is gone.

## What to check on the phone

1. Tools / Diagnostics → "Фото и вопрос" opens.
2. Default (Mock): pick photo → type question → **Спросить** → placeholder answer appears.
3. Switch to **OpenRouter Direct**: credentials panel appears; save a real spend-limited key
   + valid vision model id (e.g. `openai/gpt-4o-mini`); status line shows masked key.
4. Ask with a real photo → a genuine model answer appears.
5. Error paths show on-screen (no crash): no key, no model id, no photo, empty question, no
   internet (airplane mode), bad key (401), large image (>4 MB).
6. **Очистить ключ** → status returns to "(ключ не сохранён)"; OpenRouter ask now reports the
   missing-key error.
7. Rotate / re-open the screen: model id and masked key persist; raw key is never re-shown.
8. Build/install with `-PincludePhotoQuestionTools=false`: the "Фото и вопрос" card degrades
   gracefully (action no longer resolves) and the app behaves normally.
