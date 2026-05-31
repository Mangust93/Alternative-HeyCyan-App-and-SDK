package com.fersaiyan.cyanbridge.ai_user_shell

import android.content.Context
import android.content.SharedPreferences

/**
 * Module-local persistence for the user-facing AI settings (V1.4).
 *
 * Stored in this module's OWN private SharedPreferences file. It deliberately does NOT
 * reuse :photo-question-tools' store: that would require a compile-time dependency on the
 * feature module and break the loose coupling that lets the shell build with the feature
 * toggled out. The shell keeps its settings independently; wiring this store into the
 * photo-question request path is intentionally left for a later step.
 *
 * It holds two things:
 *   - a user-provided OpenRouter API key,
 *   - the selected model id (from a fixed local list, see [MODELS]).
 *
 * The API key is user-supplied at runtime; it is NEVER bundled in the APK, never logged,
 * and never shown in full once saved (see [maskedApiKey]). This is plain SharedPreferences
 * on purpose, mirroring the rest of the project: EncryptedSharedPreferences would pull
 * androidx.security-crypto + Tink into this otherwise-dependency-free module. The trade-off
 * (key readable by a root/backup attacker on-device) is surfaced to the user on screen.
 */
internal class AiSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Raw key. Only ever used to display a mask here; never log or render in full. */
    private val apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()

    val hasApiKey: Boolean
        get() = apiKey.isNotEmpty()

    /** Selected model id; falls back to the first fixed model when nothing is stored. */
    val modelId: String
        get() = prefs.getString(KEY_MODEL_ID, null)
            ?.takeIf { it in MODELS }
            ?: MODELS.first()

    /** Persist the key (trimmed) and selected model. A blank key clears the stored key. */
    fun save(apiKey: String, modelId: String) {
        val editor = prefs.edit()
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isEmpty()) {
            editor.remove(KEY_API_KEY)
        } else {
            editor.putString(KEY_API_KEY, trimmedKey)
        }
        val safeModel = if (modelId in MODELS) modelId else MODELS.first()
        editor.putString(KEY_MODEL_ID, safeModel)
        editor.apply()
    }

    /** Forget the API key only; the selected model is kept. */
    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    /**
     * A display-safe rendering of the stored key, e.g. "sk-or-v1-••••1234". Shows just the
     * recognisable OpenRouter prefix and the last 4 characters; the middle is masked and the
     * full key is never returned. For a key too short to mask safely it returns only bullets.
     */
    fun maskedApiKey(): String {
        val key = apiKey
        if (key.isEmpty()) return ""
        if (key.length <= VISIBLE_SUFFIX) return BULLETS
        val suffix = key.takeLast(VISIBLE_SUFFIX)
        val prefix = KNOWN_PREFIXES.firstOrNull { key.startsWith(it) }.orEmpty()
        // Only keep the prefix if there is still hidden material between it and the suffix.
        val keepPrefix = prefix.isNotEmpty() && key.length > prefix.length + VISIBLE_SUFFIX
        return if (keepPrefix) "$prefix$BULLETS$suffix" else "$BULLETS$suffix"
    }

    companion object {
        /**
         * Fixed local model list. Deliberately hard-coded — no network call to discover
         * models (that is out of scope for V1.4). Keep the first entry as the default.
         */
        val MODELS: List<String> = listOf(
            "google/gemini-2.0-flash-001",
            "google/gemini-2.5-flash-preview",
            "openai/gpt-4.1-mini",
            "openai/gpt-4o-mini",
            "qwen/qwen2.5-vl-72b-instruct",
        )

        private const val PREFS_NAME = "ai_user_shell_settings"
        private const val KEY_API_KEY = "openrouter_api_key"
        private const val KEY_MODEL_ID = "openrouter_model_id"

        private const val VISIBLE_SUFFIX = 4
        private const val BULLETS = "••••"
        private val KNOWN_PREFIXES = listOf("sk-or-v1-", "sk-or-")
    }
}
