package com.fersaiyan.cyanbridge.ai_history_core

/**
 * The outcome of an AI request as recorded in history.
 *
 *   - [PENDING]        — the request was submitted and is still running (or was interrupted
 *                        before it could finish, e.g. the app was closed mid-request). Written
 *                        the moment the user sends a request so it is visible immediately, then
 *                        replaced in-place by a terminal status once the result is known.
 *   - [SUCCESS]        — the request completed and produced an answer.
 *   - [ERROR]          — the request failed (network, server, parse, ...).
 *   - [CANCELLED]      — the request was cancelled/superseded before it finished.
 *   - [CONFIG_MISSING] — the request was not sent because configuration (e.g. the API key)
 *                        was missing.
 *
 * The persisted form is the enum [name]; [fromStored] maps an unknown/legacy/corrupt value
 * back to [ERROR] so reading old or malformed data never throws.
 */
enum class AiRequestHistoryStatus {
    PENDING,
    SUCCESS,
    ERROR,
    CANCELLED,
    CONFIG_MISSING,
    ;

    companion object {
        fun fromStored(raw: String?): AiRequestHistoryStatus =
            entries.firstOrNull { it.name == raw } ?: ERROR
    }
}
