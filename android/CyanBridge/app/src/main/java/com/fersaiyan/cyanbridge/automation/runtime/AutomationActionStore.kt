package com.fersaiyan.cyanbridge.automation.runtime

import android.content.Context
import org.json.JSONArray

/**
 * Local persistence for saved automation actions (Module G).
 *
 * Backed by a single SharedPreferences entry holding a JSON array of [AutomationAction]s — no
 * Room database and no server config, by module constraint. All reads are defensive: a corrupt
 * or partially-written entry degrades to an empty list rather than throwing.
 */
class AutomationActionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** All saved actions, newest first. */
    fun loadAll(): List<AutomationAction> {
        val raw = prefs.getString(KEY_ACTIONS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val list = ArrayList<AutomationAction>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            AutomationAction.fromJson(obj)?.let { list.add(it) }
        }
        return list.asReversed()
    }

    fun get(id: String): AutomationAction? = loadAll().firstOrNull { it.id == id }

    /** Inserts a new action or replaces the existing one with the same id. */
    fun save(action: AutomationAction) {
        val current = loadAll().filterNot { it.id == action.id }
        // loadAll() returns newest-first; persist oldest-first then put the saved one last (newest).
        val ordered = current.asReversed() + action
        persist(ordered)
    }

    fun delete(id: String) {
        val remaining = loadAll().filterNot { it.id == id }.asReversed()
        persist(remaining)
    }

    private fun persist(oldestFirst: List<AutomationAction>) {
        val array = JSONArray()
        oldestFirst.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_ACTIONS, array.toString()).apply()
    }

    private companion object {
        const val PREFS = "cyanbridge_automation_runtime"
        const val KEY_ACTIONS = "actions"
    }
}
