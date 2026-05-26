package com.fersaiyan.cyanbridge.ui.tools

/**
 * Intent actions used to open optional feature modules without a compile-time
 * dependency on their Activity classes.
 *
 * Each constant must match an <intent-filter><action> declared in the matching
 * module manifest. The core app launches a module with
 * Intent(action).setPackage(packageName); it never imports the module classes.
 */
object FeatureIntents {
    const val CONVERSATION_TRANSLATION =
        "com.fersaiyan.cyanbridge.feature.CONVERSATION_TRANSLATION"
    const val HEADSET_BUTTON_DIAGNOSTIC =
        "com.fersaiyan.cyanbridge.feature.HEADSET_BUTTON_DIAGNOSTIC"
    const val GLASSES_BUTTON_EVENT_DIAGNOSTIC =
        "com.fersaiyan.cyanbridge.feature.GLASSES_BUTTON_EVENT_DIAGNOSTIC"
    const val DEBUG_LOGS =
        "com.fersaiyan.cyanbridge.feature.DEBUG_LOGS"
    const val RUNTIME_DIAGNOSTICS =
        "com.fersaiyan.cyanbridge.feature.RUNTIME_DIAGNOSTICS"
    const val PHONE_TEST_TOOLS =
        "com.fersaiyan.cyanbridge.feature.PHONE_TEST_TOOLS"
    const val PHOTO_QUESTION =
        "com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION"
}
