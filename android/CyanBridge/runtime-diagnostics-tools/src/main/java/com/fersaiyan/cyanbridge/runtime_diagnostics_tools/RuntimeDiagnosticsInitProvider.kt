package com.fersaiyan.cyanbridge.runtime_diagnostics_tools

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * No-op [ContentProvider] used purely for auto-initialization.
 *
 * Android creates every declared provider during process startup, before the first
 * activity runs, so this lets the optional :runtime-diagnostics-tools module install its
 * instrumentation WITHOUT any change to :app code. [onCreate] simply calls
 * [RuntimeDiagnostics.install]; all the data-access methods are safe no-ops because this
 * provider is never queried (it is exported=false and serves no content).
 *
 * It has NO dependency on :app and touches no glasses / media / BLE / P2P / Moonshine code.
 */
class RuntimeDiagnosticsInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        // context is non-null here in practice; install() is idempotent and never throws.
        context?.let { RuntimeDiagnostics.install(it) }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
