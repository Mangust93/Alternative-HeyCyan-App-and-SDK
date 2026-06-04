package com.fersaiyan.cyanbridge.photo_question_tools

import android.content.Context
import android.content.SharedPreferences

/**
 * Module-local persistence for the photo-question feature.
 *
 * Stored in a private SharedPreferences file owned by this module's package context, so
 * nothing in :app or any other module reads or writes it. It holds a single thing:
 *   - the answer [Mode] (Mock by default).
 *
 * The OpenRouter API key and model are NO LONGER stored here. As of Module 2 they live in
 * the shared :ai-config store (AiSettingsStore), the single source of truth managed on the
 * "Настройки AI" screen, so the picture/question flow and that screen never diverge. This
 * class therefore keeps no secret at all — only the non-sensitive answer mode.
 */
class PhotoQuestionSettings(context: Context) {

    enum class Mode {
        /** Local deterministic placeholder answer. Default and offline fallback. */
        MOCK,

        /** Direct call to OpenRouter from the device using the user's own key. */
        OPENROUTER_DIRECT,
        ;

        companion object {
            fun fromStored(raw: String?): Mode =
                entries.firstOrNull { it.name == raw } ?: MOCK
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: Mode
        get() = Mode.fromStored(prefs.getString(KEY_MODE, null))
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).apply()
        }

    private companion object {
        const val PREFS_NAME = "photo_question_tools"
        const val KEY_MODE = "mode"
    }
}
