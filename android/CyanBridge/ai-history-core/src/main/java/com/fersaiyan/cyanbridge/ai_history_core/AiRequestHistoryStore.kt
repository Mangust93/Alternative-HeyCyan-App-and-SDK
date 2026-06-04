package com.fersaiyan.cyanbridge.ai_history_core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, on-device store for the AI request history.
 *
 * Persists a capped list of [AiRequestHistoryItem] as a JSON array string in a private
 * SharedPreferences file ([PREFS_NAME] / [KEY_ITEMS_JSON]). It uses only the framework's
 * bundled org.json — no Room/SQLite, no networking, no server sync, no extra dependencies.
 *
 * Design notes / guarantees:
 *   - Newest first: [listHistoryItems] returns the most recently saved item first.
 *   - Bounded: at most [MAX_ENTRIES] records are kept; older ones are dropped on save.
 *   - Crash-safe reads: corrupt or unparseable JSON yields an empty list, never a throw.
 *   - No secrets: an [AiRequestHistoryItem] has no API-key field, and this class never
 *     reads, stores or logs a key. It logs nothing at all — not the question, the answer
 *     nor anything else.
 *
 * This is a core layer only: it is not yet wired to any feature or UI.
 */
class AiRequestHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save [item] as the newest record. Any existing record with the same [AiRequestHistoryItem.id]
     * is replaced (and moved to the front). The list is then capped to [MAX_ENTRIES].
     */
    fun saveHistoryItem(item: AiRequestHistoryItem) {
        val current = readItems().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        writeItems(current.take(MAX_ENTRIES))
    }

    /** All stored records, newest first. Returns an empty list when none or on corrupt data. */
    fun listHistoryItems(): List<AiRequestHistoryItem> = readItems()

    /** The record with [id], or null when absent. */
    fun getHistoryItem(id: String): AiRequestHistoryItem? =
        readItems().firstOrNull { it.id == id }

    /** Remove the record with [id], if present. */
    fun deleteHistoryItem(id: String) {
        val current = readItems()
        val filtered = current.filterNot { it.id == id }
        if (filtered.size != current.size) writeItems(filtered)
    }

    /** Forget the entire local history. */
    fun clearHistory() {
        prefs.edit().remove(KEY_ITEMS_JSON).apply()
    }

    private fun readItems(): List<AiRequestHistoryItem> {
        val json = prefs.getString(KEY_ITEMS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(itemFromJson(obj))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeItems(items: List<AiRequestHistoryItem>) {
        val array = JSONArray()
        items.forEach { array.put(itemToJson(it)) }
        prefs.edit().putString(KEY_ITEMS_JSON, array.toString()).apply()
    }

    private fun itemToJson(item: AiRequestHistoryItem): JSONObject {
        val obj = JSONObject()
        obj.put(FIELD_ID, item.id)
        obj.put(FIELD_TIMESTAMP, item.timestampMillis)
        obj.put(FIELD_FEATURE_TYPE, item.featureType.name)
        obj.put(FIELD_QUESTION, item.question)
        obj.put(FIELD_STATUS, item.status.name)
        item.answer?.let { obj.put(FIELD_ANSWER, it) }
        item.modelId?.let { obj.put(FIELD_MODEL_ID, it) }
        item.provider?.let { obj.put(FIELD_PROVIDER, it) }
        item.imageUri?.let { obj.put(FIELD_IMAGE_URI, it) }
        item.imageLabel?.let { obj.put(FIELD_IMAGE_LABEL, it) }
        item.errorMessage?.let { obj.put(FIELD_ERROR_MESSAGE, it) }
        return obj
    }

    private fun itemFromJson(obj: JSONObject): AiRequestHistoryItem =
        AiRequestHistoryItem(
            id = obj.optString(FIELD_ID),
            timestampMillis = obj.optLong(FIELD_TIMESTAMP),
            featureType = AiRequestHistoryFeatureType.fromStored(obj.optString(FIELD_FEATURE_TYPE)),
            question = obj.optString(FIELD_QUESTION),
            answer = obj.optStringOrNull(FIELD_ANSWER),
            modelId = obj.optStringOrNull(FIELD_MODEL_ID),
            provider = obj.optStringOrNull(FIELD_PROVIDER),
            imageUri = obj.optStringOrNull(FIELD_IMAGE_URI),
            imageLabel = obj.optStringOrNull(FIELD_IMAGE_LABEL),
            status = AiRequestHistoryStatus.fromStored(obj.optString(FIELD_STATUS)),
            errorMessage = obj.optStringOrNull(FIELD_ERROR_MESSAGE),
        )

    /** optString returns "" for a missing/null key; preserve the null instead. */
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    companion object {
        const val PREFS_NAME = "ai_request_history"
        const val KEY_ITEMS_JSON = "history_items_json"
        const val MAX_ENTRIES = 100

        private const val FIELD_ID = "id"
        private const val FIELD_TIMESTAMP = "timestampMillis"
        private const val FIELD_FEATURE_TYPE = "featureType"
        private const val FIELD_QUESTION = "question"
        private const val FIELD_ANSWER = "answer"
        private const val FIELD_MODEL_ID = "modelId"
        private const val FIELD_PROVIDER = "provider"
        private const val FIELD_IMAGE_URI = "imageUri"
        private const val FIELD_IMAGE_LABEL = "imageLabel"
        private const val FIELD_STATUS = "status"
        private const val FIELD_ERROR_MESSAGE = "errorMessage"
    }
}
