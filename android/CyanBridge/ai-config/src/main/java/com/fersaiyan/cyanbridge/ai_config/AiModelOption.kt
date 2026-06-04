package com.fersaiyan.cyanbridge.ai_config

/**
 * A single selectable AI model in the fixed local list (see [AiModels]).
 *
 * [id] is the canonical OpenRouter model id (e.g. "google/gemini-2.0-flash-001") and is
 * the value persisted by [AiSettingsStore]. [title] is a short human-readable label;
 * [description] is optional extra context. Only [id] affects behaviour — the label and
 * description are presentation-only and never sent anywhere.
 */
data class AiModelOption(
    val id: String,
    val title: String,
    val description: String? = null,
)
