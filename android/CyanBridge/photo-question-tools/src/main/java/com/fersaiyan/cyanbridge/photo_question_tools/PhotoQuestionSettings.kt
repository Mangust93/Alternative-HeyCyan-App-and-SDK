package com.fersaiyan.cyanbridge.photo_question_tools

import android.content.Context
import android.content.SharedPreferences

/**
 * Module-local persistence for the photo-question feature.
 *
 * Stored in a private SharedPreferences file owned by this module's package context, so
 * nothing in :app or any other module reads or writes it. It holds three things:
 *   - the answer [Mode] (Mock by default),
 *   - a user-provided OpenRouter API key,
 *   - a user-provided model id.
 *
 * The API key is user-supplied at runtime; it is NEVER bundled in the APK, never logged,
 * and never shown in full once saved (see [maskedApiKey]). This is plain SharedPreferences
 * on purpose: EncryptedSharedPreferences would pull androidx.security-crypto + Tink, a
 * heavier and historically churn-prone dependency, which this optional module avoids. The
 * trade-off (key readable by a root/backup attacker on-device) is documented for the user.
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

    /** Raw key. Used only to build the Authorization header; never log or display this. */
    val apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()

    val modelId: String
        get() = prefs.getString(KEY_MODEL_ID, "").orEmpty()

    val hasApiKey: Boolean
        get() = apiKey.isNotEmpty()

    /** Persist key + model id, trimming surrounding whitespace. Empty values are cleared. */
    fun saveCredentials(apiKey: String, modelId: String) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_MODEL_ID, modelId.trim())
            .apply()
    }

    /** Forget the API key only; mode and model id are kept. */
    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    /**
     * A display-safe rendering of the stored key, e.g. "sk-or-...abcd". Shows enough to
     * recognise which key is set without revealing it. Never returns the full key.
     */
    fun maskedApiKey(): String {
        val key = apiKey
        if (key.isEmpty()) return "(ключ не сохранён)"
        if (key.length <= MASK_VISIBLE_PREFIX + MASK_VISIBLE_SUFFIX) return "••••"
        val prefix = key.take(MASK_VISIBLE_PREFIX)
        val suffix = key.takeLast(MASK_VISIBLE_SUFFIX)
        return "$prefix...$suffix"
    }

    private companion object {
        const val PREFS_NAME = "photo_question_tools"
        const val KEY_MODE = "mode"
        const val KEY_API_KEY = "openrouter_api_key"
        const val KEY_MODEL_ID = "openrouter_model_id"

        // "sk-or-" is the OpenRouter key prefix, so showing 6 keeps it recognisable.
        const val MASK_VISIBLE_PREFIX = 6
        const val MASK_VISIBLE_SUFFIX = 4
    }
}
