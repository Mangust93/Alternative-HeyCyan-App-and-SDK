package com.fersaiyan.cyanbridge.ai_history_core

/**
 * Which AI feature produced a history record.
 *
 * The persisted form is the enum [name]; [fromStored] maps an unknown/legacy/corrupt
 * value back to [OTHER] so reading old or malformed data never throws.
 */
enum class AiRequestHistoryFeatureType {
    PHOTO_QUESTION,
    TRANSLATION,
    OTHER,
    ;

    companion object {
        fun fromStored(raw: String?): AiRequestHistoryFeatureType =
            entries.firstOrNull { it.name == raw } ?: OTHER
    }
}
