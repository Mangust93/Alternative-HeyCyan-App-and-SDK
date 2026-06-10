package com.fersaiyan.cyanbridge.automation.runtime

/**
 * The outcome of running (or testing) an [AutomationAction] (Module G).
 *
 * [statusCode] and [responsePreview] are only meaningful for HTTP/Webhook actions; the UI
 * shows them when present. Every executor path returns one of these instead of throwing, so a
 * failing action degrades to a readable error rather than crashing the app.
 */
data class AutomationActionResult(
    val success: Boolean,
    val title: String,
    val message: String,
    val statusCode: Int? = null,
    val responsePreview: String? = null,
) {
    companion object {
        fun ok(title: String, message: String, statusCode: Int? = null, preview: String? = null) =
            AutomationActionResult(true, title, message, statusCode, preview)

        fun error(title: String, message: String, statusCode: Int? = null, preview: String? = null) =
            AutomationActionResult(false, title, message, statusCode, preview)
    }
}
