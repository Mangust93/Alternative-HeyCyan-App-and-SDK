package com.fersaiyan.cyanbridge.ai_history_core

/**
 * One record in the local AI request history.
 *
 * A plain, immutable value holder persisted by [AiRequestHistoryStore]. It deliberately
 * carries NO API key and NO secret of any kind — only what is needed to show a past
 * request to the user. Callers must never put a key (or any credential) into [question],
 * [answer] or any other field.
 *
 * Required fields ([id], [timestampMillis], [featureType], [question], [status]) are always
 * persisted. The remaining fields are optional and stored only when non-null.
 *
 * @property id            Stable unique id for this record (caller-generated, e.g. a UUID).
 * @property timestampMillis When the request was made (epoch millis).
 * @property featureType   Which feature produced it (see [AiRequestHistoryFeatureType]).
 * @property question      The user's question/prompt text.
 * @property answer        The answer text, or null when there is none (error/cancelled).
 * @property modelId       The model id used, when known.
 * @property provider      The provider used (e.g. "openrouter"), when known.
 * @property imageUri      A reference to the associated image, when any.
 * @property imageLabel    A human-readable image name/label, when any.
 * @property status        The outcome (see [AiRequestHistoryStatus]).
 * @property errorMessage  A user-readable error message when [status] is not success.
 */
data class AiRequestHistoryItem(
    val id: String,
    val timestampMillis: Long,
    val featureType: AiRequestHistoryFeatureType,
    val question: String,
    val answer: String? = null,
    val modelId: String? = null,
    val provider: String? = null,
    val imageUri: String? = null,
    val imageLabel: String? = null,
    val status: AiRequestHistoryStatus,
    val errorMessage: String? = null,
)
