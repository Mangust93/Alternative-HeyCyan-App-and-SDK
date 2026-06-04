package com.fersaiyan.cyanbridge.ai_config

import android.content.Context
import android.content.SharedPreferences

/**
 * Shared persistence for the user-facing AI settings.
 *
 * Moved out of :ai-user-shell into this small :ai-config layer so more than one module can
 * read the same configuration (today :ai-user-shell; later Photo Question) without taking a
 * compile-time dependency on each other. The SharedPreferences file name and keys are kept
 * IDENTICAL to the V1.4 :ai-user-shell store ([PREFS_NAME], [KEY_API_KEY], [KEY_MODEL_ID]),
 * so values already saved on a device keep being read after this move.
 *
 * It holds two things:
 *   - a user-provided OpenRouter API key,
 *   - the selected model id (from the fixed local list, see [AiModels]).
 *
 * The API key is user-supplied at runtime; it is NEVER bundled in the APK, never logged,
 * and never shown in full once saved (see [getMaskedApiKey]). This is plain SharedPreferences
 * on purpose, mirroring the rest of the project: EncryptedSharedPreferences would pull
 * androidx.security-crypto + Tink into this otherwise-dependency-free module. The trade-off
 * (key readable by a root/backup attacker on-device) is surfaced to the user on screen.
 *
 * This class only persists settings. It performs NO networking and holds NO OpenRouter
 * request/response logic — consuming the values to make a call is the caller's job.
 */
class AiSettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Raw stored API key, or null when none is set. Never log or render this in full. */
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotEmpty() }

    /** True when a non-empty API key is stored. */
    fun hasApiKey(): Boolean = !getApiKey().isNullOrEmpty()

    /** Persist the key (trimmed). A blank key clears the stored key instead. */
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey()
        } else {
            prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        }
    }

    /** Forget the API key only; the selected model is kept. */
    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    /** The model id the user explicitly selected, or null when nothing is stored. */
    fun getSelectedModelId(): String? = prefs.getString(KEY_MODEL_ID, null)

    /** Persist the selected model. An unknown id falls back to the default. */
    fun saveSelectedModelId(modelId: String) {
        val safe = if (AiModels.isKnown(modelId)) modelId else AiModels.DEFAULT_MODEL_ID
        prefs.edit().putString(KEY_MODEL_ID, safe).apply()
    }

    /** The model id to actually use: the stored selection if valid, otherwise the default. */
    fun getEffectiveModelId(): String =
        getSelectedModelId()?.takeIf { AiModels.isKnown(it) } ?: AiModels.DEFAULT_MODEL_ID

    /**
     * A display-safe rendering of the stored key, e.g. "sk-or-v1-••••1234", or null when no
     * key is stored. Shows just the recognisable OpenRouter prefix and the last 4 characters;
     * the middle is masked and the full key is never returned. For a key too short to mask
     * safely it returns only bullets.
     */
    fun getMaskedApiKey(): String? {
        val key = getApiKey() ?: return null
        if (key.length <= VISIBLE_SUFFIX) return BULLETS
        val suffix = key.takeLast(VISIBLE_SUFFIX)
        val prefix = KNOWN_PREFIXES.firstOrNull { key.startsWith(it) }.orEmpty()
        // Only keep the prefix if there is still hidden material between it and the suffix.
        val keepPrefix = prefix.isNotEmpty() && key.length > prefix.length + VISIBLE_SUFFIX
        return if (keepPrefix) "$prefix$BULLETS$suffix" else "$BULLETS$suffix"
    }

    /** An immutable snapshot of the current configuration for consumers. */
    fun openRouterSettings(): OpenRouterSettings = OpenRouterSettings(
        apiKey = getApiKey(),
        selectedModelId = getSelectedModelId(),
        effectiveModelId = getEffectiveModelId(),
        hasApiKey = hasApiKey(),
        maskedApiKey = getMaskedApiKey(),
    )

    companion object {
        // Kept identical to the V1.4 :ai-user-shell store for on-device read compatibility.
        private const val PREFS_NAME = "ai_user_shell_settings"
        private const val KEY_API_KEY = "openrouter_api_key"
        private const val KEY_MODEL_ID = "openrouter_model_id"

        private const val VISIBLE_SUFFIX = 4
        private const val BULLETS = "••••"
        private val KNOWN_PREFIXES = listOf("sk-or-v1-", "sk-or-")
    }
}
