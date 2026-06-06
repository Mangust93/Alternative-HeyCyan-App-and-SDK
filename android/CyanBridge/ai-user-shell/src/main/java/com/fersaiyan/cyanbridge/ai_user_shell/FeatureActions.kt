package com.fersaiyan.cyanbridge.ai_user_shell

/**
 * Intent actions this shell uses to open the already-shipping AI feature screens.
 *
 * These strings intentionally DUPLICATE the action names declared by the feature
 * modules' manifests (and by the host app's own FeatureIntents). The duplication is
 * deliberate: copying three string literals is what keeps this module from taking a
 * compile-time dependency on :app, :conversation-translation or :photo-question-tools.
 * Each value must stay in sync with the matching <intent-filter><action> in the target
 * module manifest.
 */
internal object FeatureActions {
    /** Opens :conversation-translation's ConversationTranslationActivity. */
    const val CONVERSATION_TRANSLATION =
        "com.fersaiyan.cyanbridge.feature.CONVERSATION_TRANSLATION"

    /** Opens :photo-question-tools' PhotoQuestionActivity. */
    const val PHOTO_QUESTION =
        "com.fersaiyan.cyanbridge.feature.PHOTO_QUESTION"

    /**
     * Internal marker for the settings card. The settings screen lives in this module and
     * is launched with an explicit Activity intent, so no manifest action is exposed.
     */
    const val AI_SETTINGS =
        "com.fersaiyan.cyanbridge.feature.AI_SETTINGS"

    /**
     * Internal marker for the history card. Like settings, the history screen lives in this
     * module and is launched with an explicit Activity intent, so no manifest action is
     * exposed (the activity stays android:exported="false").
     */
    const val AI_HISTORY =
        "com.fersaiyan.cyanbridge.feature.AI_HISTORY"

    /**
     * Internal marker for the automation card. Like settings and history, the automation
     * screen lives in this module and is launched with an explicit Activity intent, so no
     * manifest action is exposed (the activity stays android:exported="false"). It is a
     * UI-only placeholder shell: no executor, no background service, no permissions.
     */
    const val AUTOMATION =
        "com.fersaiyan.cyanbridge.feature.AUTOMATION"
}
