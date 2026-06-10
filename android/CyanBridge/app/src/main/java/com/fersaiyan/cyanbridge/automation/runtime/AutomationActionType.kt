package com.fersaiyan.cyanbridge.automation.runtime

/**
 * The set of practical, locally-executable automation actions supported by the Automation Hub
 * (Module G). These are deliberately small, self-contained Android actions — there is no
 * Telegram/Notion/Calendar API, no BLE/media trigger and no visual flow editor here.
 */
enum class AutomationActionType(val storageKey: String, val displayName: String) {
    HTTP_REQUEST("http_request", "HTTP-запрос"),
    WEBHOOK_POST("webhook_post", "Webhook"),
    ANDROID_INTENT("android_intent", "Android Intent"),
    OPEN_APP("open_app", "Открыть приложение"),
    OPEN_SETTINGS("open_settings", "Открыть настройки"),
    SHARE_TEXT("share_text", "Поделиться текстом");

    companion object {
        fun fromStorageKey(key: String?): AutomationActionType? =
            values().firstOrNull { it.storageKey == key }
    }
}
