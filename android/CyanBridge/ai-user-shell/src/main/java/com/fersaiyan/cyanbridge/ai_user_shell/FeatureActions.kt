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
}
