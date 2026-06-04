package com.fersaiyan.cyanbridge.ai_config

/**
 * An immutable snapshot of the user's OpenRouter configuration, as read from
 * [AiSettingsStore].
 *
 * This is a plain value holder: it carries the settings a consumer needs (the API key,
 * the selected/effective model and the display-safe mask) without exposing the store or
 * any persistence detail. It performs NO networking and contains NO request/response
 * logic — building OpenRouter calls is the consumer's responsibility and out of scope here.
 *
 * [apiKey] is the raw secret and must never be logged or shown in full; use
 * [maskedApiKey] for any UI.
 */
data class OpenRouterSettings(
    /** Raw OpenRouter API key, or null when none is stored. Never log or render in full. */
    val apiKey: String?,
    /** The model id the user explicitly selected, or null when nothing is stored. */
    val selectedModelId: String?,
    /** The model id to actually use: the selection if valid, otherwise the default. */
    val effectiveModelId: String,
    /** True when a non-empty API key is stored. */
    val hasApiKey: Boolean,
    /** Display-safe masked key (e.g. "sk-or-v1-••••1234"), or null when none is stored. */
    val maskedApiKey: String?,
)
