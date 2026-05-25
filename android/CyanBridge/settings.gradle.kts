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

// HeyCyan Core - bundled as composite build for easy compilation
val heycyanCoreDir = file("../../heycyan-core")
if (heycyanCoreDir.exists()) {
    includeBuild(heycyanCoreDir)
}
