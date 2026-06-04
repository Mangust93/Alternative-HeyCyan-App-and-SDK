# CLAUDE — Conversation Translation (ML Kit) — V1 Review

Optional module `:conversation-translation`. On-device dialog translation for the V1
smart-glasses app **without Hermes and without OpenRouter**:

```
Android SpeechRecognizer
  -> ML Kit Language Identification   (detect source language from recognized text)
  -> ML Kit Translation               (on-device, downloadable models)
  -> Android TextToSpeech             (speak the translation)
```

The module is a standalone Android library. It has **no dependency on `:app`**, imports no
official-app code, does no reverse engineering, and never touches DeviceBindActivity,
PictureVm, AlbumDownloader, BleIpBridge/P2P, NativeAutomationEngine, MainActivity,
ChatThreadActivity, the glasses SDK flow, the Moonshine fallback, or the media flow.

---

## 1. What was created

| File | Purpose |
| --- | --- |
| `conversation-translation/build.gradle` | Android library module, namespace `com.fersaiyan.cyanbridge.conversation_translation`, ML Kit deps. |
| `conversation-translation/src/main/AndroidManifest.xml` | Declares `RECORD_AUDIO` / `INTERNET` / `ACCESS_NETWORK_STATE` and the `exported=true` activity (adb/manual testing). |
| `conversation-translation/src/main/java/.../ConversationTranslationActivity.kt` | Full UI + pipeline (SpeechRecognizer, ML Kit Language ID, ML Kit Translation, TextToSpeech). |
| `settings.gradle.kts` | `include(":conversation-translation")`. |
| `app/build.gradle` | Optional `debugImplementation`, gated by `includeConversationTranslation`. |

No commit and no push were made.

### UI elements (programmatic, no XML layout)
- Заголовок: **Перевод диалога**
- **Языковая пара**: Русский ↔ Английский, Русский ↔ Испанский
- **Режим направления**: Авто / RU→EN / EN→RU / RU→ES / ES→RU
- Кнопки: **Скачать модели**, **Старт**, **Стоп**, **Озвучить**
- Переключатель: **Автоозвучка** (по умолчанию вкл.)
- Поля: **Исходная фраза**, **Определённый язык**, **Перевод**, **Статус / ошибка**

---

## 2. How to enable the module

Enabled by default (debug builds):

```bash
./gradlew :app:assembleDebug
```

The property defaults to `true`:

```groovy
def includeConversationTranslation =
    providers.gradleProperty("includeConversationTranslation").orElse("true").get().toBoolean()
if (includeConversationTranslation) {
    debugImplementation project(":conversation-translation")
}
```

### Why `debugImplementation` and not `implementation` (V1 decision)

The task allowed either, asking to justify if `debugImplementation` is chosen. It is, for
three reasons consistent with the existing optional modules (`:phone-test-tools`,
`:debug-log-tools`, `:runtime-diagnostics-tools`):

1. **`exported=true`.** The activity is exported only so it can be launched via adb for
   manual testing. An exported screen with no in-app entry point should not ship in a
   release APK. `debugImplementation` guarantees it is absent from release builds.
2. **V1 has no real entry point yet.** Per the MVP scope, launch is from adb, not from a
   glasses/headset button. Until a gated in-app entry point exists, release inclusion adds
   risk with no user-facing benefit.
3. **Consistency.** All other optional modules in this repo use the same
   `debugImplementation` + `includeXxx` Gradle-property pattern, so reviewers and Codex see
   one predictable wiring.

When this graduates to a release feature, switch to `implementation`, add an in-app entry
point, and either keep the flag or set `android:exported="false"`.

---

## 3. How to disable the module

```bash
./gradlew :app:assembleDebug -PincludeConversationTranslation=false
```

`:app` then builds without the dependency (core/skeleton unaffected). The module is also
absent from any `assembleRelease` regardless of the flag, because it is wired as
`debugImplementation` only.

---

## 4. How to open the activity via adb

```bash
adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.conversation_translation.ConversationTranslationActivity
```

(`applicationId` = `com.fersaiyan.cyanbridge`; the activity lives in the module namespace
`...conversation_translation`.)

---

## 5. ML Kit dependencies added

In `conversation-translation/build.gradle`:

```groovy
implementation 'androidx.appcompat:appcompat:1.7.1'
implementation 'com.google.mlkit:language-id:17.0.6'  // language identification
implementation 'com.google.mlkit:translate:17.0.3'    // on-device translation
```

`SpeechRecognizer` and `TextToSpeech` are Android framework APIs — no extra dependency.

---

## 6. How automatic language detection works

In **Авто** mode:
1. `SpeechRecognizer` returns the recognized phrase text.
2. ML Kit `LanguageIdentification.identifyLanguage(text)` returns a BCP-47 code
   (`ru` / `en` / `es` / `und`).
3. The detected code is matched against the selected pair:
   - Pair **RU↔EN**: `ru` → translate `ru→en`; `en` → translate `en→ru`.
   - Pair **RU↔ES**: `ru` → translate `ru→es`; `es` → translate `es→ru`.
   - `und` (undetermined) → status: «Не удалось определить язык фразы».
   - Any other detected language → status: «Распознан язык «X», он не входит в выбранную
     пару».

In explicit modes (RU→EN / EN→RU / RU→ES / ES→RU) the direction is fixed; the detected
language is still shown in **Определённый язык**, and a mismatch with the selected pair is
reported instead of silently translating.

> V1 SpeechRecognizer note: SR needs a single language hint. In Авто the hint is the pair's
> primary language (Russian); the actual text language is then identified by ML Kit. This
> can reduce recognition quality for the non-primary language while speaking in Авто — see
> limitations.

---

## 7. How to download models

- **Скачать модели** downloads **both** models of the selected pair via one
  `translator.downloadModelIfNeeded(DownloadConditions)` call (downloading a translator for
  the pair pulls both source and target language models, covering both directions).
- Before translating, the module checks `RemoteModelManager.getDownloadedModels(...)`. If
  either required model is missing it shows **«Сначала скачайте языковые модели»** instead
  of failing.
- Without internet the download fails gracefully with a clear status message — **no crash**.

---

## 8. How to verify on a phone

1. Install the debug build and grant the microphone permission when prompted.
2. Launch via the adb command in §4.
3. Pick a language pair and a direction (start with **Авто**).
4. Tap **Скачать модели** (needs internet once) and wait for «Модели готовы…».
5. Tap **Старт**, speak a phrase, tap **Стоп** if needed.
6. Confirm **Исходная фраза**, **Определённый язык**, and **Перевод** populate.
7. With **Автоозвучка** on, the translation is spoken automatically; **Озвучить** repeats it.
8. Negative checks: translate before downloading → «Сначала скачайте языковые модели»;
   speak a language outside the pair in Авто → clear out-of-pair error; airplane mode +
   download → graceful failure, no crash.

---

## 9. V1 limitations

- Launch is from the app/adb, **not** from a glasses button yet.
- The glasses microphone is verified separately; V1 uses the phone microphone via
  `SpeechRecognizer`.
- **Not** duplex / streaming translation — one phrase at a time (start → recognize → translate).
- **No** Hermes, **no** OpenRouter — translation is fully on-device via ML Kit.
- In Авто mode the SpeechRecognizer language hint is the pair's primary language (Russian),
  which can lower recognition quality for the secondary language while in Авто; explicit
  direction modes set the matching SR language.
- Language pairs limited to RU↔EN and RU↔ES.

---

## 10. Next stage

- Headset / media-button diagnostic to start translation from the glasses/headset button
  (`MediaSession` / `KeyEvent.KEYCODE_MEDIA_*` handling) instead of the on-screen Старт.
- Then: route phone TTS output / glasses microphone input through the glasses audio path,
  and evaluate partial-results streaming for lower latency.
