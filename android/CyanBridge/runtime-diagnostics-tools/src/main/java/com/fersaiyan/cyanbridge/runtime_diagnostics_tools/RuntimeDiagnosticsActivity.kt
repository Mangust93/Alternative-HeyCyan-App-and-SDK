package com.fersaiyan.cyanbridge.runtime_diagnostics_tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Simple, real runtime-diagnostics viewer for manual phone testing.
 *
 * Lives in the optional, standalone :runtime-diagnostics-tools module. It has NO compile
 * dependency on :app and never touches the real glasses / media / BLE / P2P flow.
 * It shows whether the module installed itself (via the auto-init provider) and renders
 * the recent REAL runtime events captured by [RuntimeDiagnosticsStore] (app start,
 * build/device facts, crashes, activity lifecycle). Every button performs a real action:
 *   - Refresh            -> reads the on-disk log file
 *   - Add heartbeat event-> writes a real module heartbeat event (verifies disk writes)
 *   - Copy bundle        -> copies the real diagnostic bundle to the system clipboard
 *   - Clear logs         -> deletes the on-disk log file
 *
 * The heartbeat is a real event written by THIS module — not a simulated SDK/glasses
 * event — used to confirm the log pipeline is working.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.runtime_diagnostics_tools.RuntimeDiagnosticsActivity
 */
class RuntimeDiagnosticsActivity : AppCompatActivity() {

    private companion object {
        const val TAG_UI = "RuntimeUI"
        const val TAG_HEARTBEAT = "HEARTBEAT"
    }

    private lateinit var statusView: TextView
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Runtime Diagnostics"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Runtime diagnostics tools"
            textSize = 20f
            setPadding(0, 0, 0, gap)
        })

        statusView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "Add heartbeat event"
            setOnClickListener { onAddHeartbeat() }
        })
        root.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { onRefresh() }
        })
        root.addView(Button(this).apply {
            text = "Copy runtime diagnostic bundle"
            setOnClickListener { onCopyBundle() }
        })
        root.addView(Button(this).apply {
            text = "Clear logs"
            setOnClickListener { onClear() }
        })

        root.addView(TextView(this).apply {
            text = "Recent runtime events"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, gap)
        })

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextIsSelectable(true)
        }
        root.addView(logView)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        RuntimeDiagnosticsStore.append(this, TAG_UI, "RuntimeDiagnosticsActivity opened")
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onAddHeartbeat() {
        RuntimeDiagnosticsStore.append(
            this,
            TAG_HEARTBEAT,
            "Manual heartbeat from RuntimeDiagnosticsActivity (button tap)",
        )
        Toast.makeText(this, "Heartbeat event written", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun onRefresh() {
        refresh()
        Toast.makeText(this, "Reloaded from log file", Toast.LENGTH_SHORT).show()
    }

    private fun onCopyBundle() {
        val bundle = RuntimeDiagnosticsStore.buildDiagnosticBundle(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_LONG).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CyanBridge runtime diagnostics", bundle))
        RuntimeDiagnosticsStore.append(this, TAG_UI, "Runtime diagnostic bundle copied to clipboard")
        Toast.makeText(this, "Runtime diagnostic bundle copied", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun onClear() {
        RuntimeDiagnosticsStore.clear(this)
        RuntimeDiagnosticsStore.append(this, TAG_UI, "Logs cleared from RuntimeDiagnosticsActivity")
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        statusView.text = buildStatus()
        val recent = RuntimeDiagnosticsStore.readRecent(this)
        logView.text = if (recent.isEmpty()) "(no events captured yet)" else recent
    }

    private fun buildStatus(): String {
        val path = RuntimeDiagnosticsStore.logFilePath(this)
        val recent = RuntimeDiagnosticsStore.readRecent(this)
        val count = if (recent.isEmpty()) 0 else recent.count { it == '\n' } + 1
        val installed = if (RuntimeDiagnostics.isInstalled) "INSTALLED (auto-init)" else "NOT installed"
        return buildString {
            appendLine("module   : $installed")
            appendLine("log file : $path")
            append("events   : $count shown")
        }
    }
}
