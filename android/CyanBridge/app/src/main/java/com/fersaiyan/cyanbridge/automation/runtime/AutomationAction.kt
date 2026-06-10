package com.fersaiyan.cyanbridge.automation.runtime

import org.json.JSONObject
import java.util.UUID

/**
 * A single saved automation action (Module G).
 *
 * One flat, type-agnostic data class is used for every [AutomationActionType]: only the fields
 * relevant to the action's type are populated/edited. This keeps the local JSON store trivial
 * (one object shape) and avoids a sealed-class/serializer ceremony for what is a small feature.
 *
 * Field meaning by type:
 *  - HTTP_REQUEST : [method], [url], [headers] (raw "Key: Value" lines), [body]
 *  - WEBHOOK_POST : [url], [body] (JSON payload, always POSTed)
 *  - ANDROID_INTENT: [intentAction], [packageName] (opt), [dataUri] (opt), [extras] ("k=v" lines)
 *  - OPEN_APP     : [packageName]
 *  - OPEN_SETTINGS: [settingsTarget] (a preset key, see [AutomationExecutor])
 *  - SHARE_TEXT   : [text]
 */
data class AutomationAction(
    val id: String = UUID.randomUUID().toString(),
    val type: AutomationActionType,
    val name: String = "",
    // HTTP / Webhook
    val method: String = "GET",
    val url: String = "",
    val headers: String = "",
    val body: String = "",
    // Android Intent
    val intentAction: String = "",
    val packageName: String = "",
    val dataUri: String = "",
    val extras: String = "",
    // Open Settings
    val settingsTarget: String = "",
    // Share Text
    val text: String = "",
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.storageKey)
        put("name", name)
        put("method", method)
        put("url", url)
        put("headers", headers)
        put("body", body)
        put("intentAction", intentAction)
        put("packageName", packageName)
        put("dataUri", dataUri)
        put("extras", extras)
        put("settingsTarget", settingsTarget)
        put("text", text)
    }

    /** Short, human-friendly one-line summary for the saved-actions list. */
    fun summary(): String = when (type) {
        AutomationActionType.HTTP_REQUEST -> "$method ${url.ifBlank { "—" }}"
        AutomationActionType.WEBHOOK_POST -> "POST ${url.ifBlank { "—" }}"
        AutomationActionType.ANDROID_INTENT -> intentAction.ifBlank { "—" }
        AutomationActionType.OPEN_APP -> packageName.ifBlank { "—" }
        AutomationActionType.OPEN_SETTINGS -> settingsTarget.ifBlank { "—" }
        AutomationActionType.SHARE_TEXT -> text.take(40).ifBlank { "—" }
    }

    companion object {
        fun fromJson(json: JSONObject): AutomationAction? {
            val type = AutomationActionType.fromStorageKey(json.optString("type")) ?: return null
            return AutomationAction(
                id = json.optString("id", UUID.randomUUID().toString()),
                type = type,
                name = json.optString("name"),
                method = json.optString("method", "GET"),
                url = json.optString("url"),
                headers = json.optString("headers"),
                body = json.optString("body"),
                intentAction = json.optString("intentAction"),
                packageName = json.optString("packageName"),
                dataUri = json.optString("dataUri"),
                extras = json.optString("extras"),
                settingsTarget = json.optString("settingsTarget"),
                text = json.optString("text"),
            )
        }
    }
}
