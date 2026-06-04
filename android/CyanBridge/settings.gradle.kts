pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        jcenter()
        maven { url = uri("https://jitpack.io") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        jcenter()
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "CyanBridgeManagerApp"
include(":app")
include(":LIB_GLASSES_SDK")

// Moonshine Voice (local wrapper module that builds vendored native sources)
include(":moonshine-voice")

// Optional phone-test / debug diagnostics. Wired into :app only as a debugImplementation
// and only when the `includePhoneTestTools` Gradle property is true (default).
include(":phone-test-tools")

// Optional debug-log diagnostics. Wired into :app only as a debugImplementation and
// only when the `includeDebugLogTools` Gradle property is true (default).
include(":debug-log-tools")

// Optional runtime-diagnostics. Auto-installs real runtime instrumentation (app start,
// build/device facts, crashes, activity lifecycle) via a ContentProvider. Wired into
// :app only as a debugImplementation and only when the `includeRuntimeDiagnosticsTools`
// Gradle property is true (default).
include(":runtime-diagnostics-tools")

// Optional conversation-translation (V1 on-device dialog translation:
// SpeechRecognizer -> ML Kit Language ID -> ML Kit Translation -> TextToSpeech).
// No Hermes, no OpenRouter. Wired into :app only as a debugImplementation and only when
// the `includeConversationTranslation` Gradle property is true (default).
val includeConversationTranslation =
    providers.gradleProperty("includeConversationTranslation").orElse("true").get().toBoolean()
if (includeConversationTranslation) {
    include(":conversation-translation")
}

// Optional headset/glasses button diagnostic. Captures real key + media-button
// events (KEYCODE_HEADSETHOOK / MEDIA_PLAY_PAUSE / MEDIA_PLAY / MEDIA_PAUSE /
// VOICE_ASSIST and ACTION_MEDIA_BUTTON) so a tester can confirm whether glasses/
// headset button presses reach Android. Purely diagnostic: binds nothing to
// translation and does not touch :conversation-translation. Wired into :app only as a
// debugImplementation and only when the `includeHeadsetButtonTools` Gradle property is
// true (default). See app/build.gradle.
val includeHeadsetButtonTools =
    providers.gradleProperty("includeHeadsetButtonTools").orElse("true").get().toBoolean()
if (includeHeadsetButtonTools) {
    include(":headset-button-tools")
}

// Optional glasses BUTTON EVENT diagnostic. Glasses buttons do not arrive as Android
// key/media events; they arrive over the HeyCyan SDK BLE notify path, which :app
// already logs for every frame under the "DeviceNotify" tag. This module is read-only:
// it tails the app's own logcat for that tag and decodes the loadData[6] opcode so a
// tester can confirm which glasses button produced which notify. It binds nothing to
// translation, does not touch :conversation-translation, and registers no SDK listener.
// Wired into :app only as a debugImplementation and only when the
// `includeGlassesButtonEventTools` Gradle property is true (default). See app/build.gradle.
val includeGlassesButtonEventTools =
    providers.gradleProperty("includeGlassesButtonEventTools").orElse("true").get().toBoolean()
if (includeGlassesButtonEventTools) {
    include(":glasses-button-event-tools")
}

// Optional "photo question" feature. Self-contained flow: pick a photo (incl. photos
// downloaded from the glasses), ask a text question, get an answer from a mock/server
// placeholder, show it on screen. Standalone: depends on neither :app, the glasses SDK,
// BLE, the media flow nor :conversation-translation, and ships no networking dependency.
// Wired into :app only as a debugImplementation and only when the
// `includePhotoQuestionTools` Gradle property is true (default). See app/build.gradle.
val includePhotoQuestionTools =
    providers.gradleProperty("includePhotoQuestionTools").orElse("true").get().toBoolean()
if (includePhotoQuestionTools) {
    include(":photo-question-tools")
}

// Shared ":ai-config" module — a tiny config layer for the user-facing AI settings
// (OpenRouter API key, selected model, fixed model list and API-key masking). It carries
// no networking and no OpenRouter request/response logic. Always registered so the
// modules that depend on it can resolve it, but it is pulled into the build only
// transitively (e.g. via :ai-user-shell's debugImplementation); :app never depends on it
// directly, so release builds do not include it.
include(":ai-config")

// Shared ":ai-history-core" module — a tiny local-history layer for AI requests. It
// persists a capped list of past requests (Photo Question, Translation, ...) as JSON in
// plain SharedPreferences, carries no networking, no server sync and no API key, and
// depends on neither :app, :ai-user-shell, :photo-question-tools nor any feature module.
// Registered here so future consumers can resolve it; as of Module 3 nothing depends on
// it yet (no feature wiring, no UI), so it is not pulled into any build.
include(":ai-history-core")

// Optional ":ai-user-shell" module — the first user-facing AI section of the app
// ("AI-функции" / "AI ассистент"). It shows simple cards that route to the already-
// shipping AI features (conversation translation, photo question) via package-scoped
// Intent actions only. It depends on neither :app nor the feature modules it launches,
// so it stays loosely coupled and degrades to "Модуль не включён" cards when those
// modules are toggled out of the build. This is the user-facing counterpart to the debug
// "Инструменты / Диагностика" shell, which is left untouched. Wired into :app only as a
// debugImplementation and only when the `includeAiUserShell` Gradle property is true
// (default). See app/build.gradle.
val includeAiUserShell =
    providers.gradleProperty("includeAiUserShell").orElse("true").get().toBoolean()
if (includeAiUserShell) {
    include(":ai-user-shell")
}

// HeyCyan Core - bundled as composite build for easy compilation
val heycyanCoreDir = file("../../heycyan-core")
if (heycyanCoreDir.exists()) {
    includeBuild(heycyanCoreDir)
}
