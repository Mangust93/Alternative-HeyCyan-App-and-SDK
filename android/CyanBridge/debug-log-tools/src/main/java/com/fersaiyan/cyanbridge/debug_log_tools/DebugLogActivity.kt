package com.fersaiyan.cyanbridge.debug_log_tools

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
 * Simple, real debug-log viewer for manual phone testing.
 *
 * Lives in the optional, standalone :debug-log-tools module. It has NO compile
 * dependency on :app and never touches the real glasses / media / BLE / P2P flow.
 * Every button performs a real action against [DebugLogStore]:
 *   - Refresh           -> reads the on-disk log file
 *   - Copy diagnostic   -> copies the real bundle to the system clipboard
 *   - Clear logs        -> deletes the on-disk log file
 *   - Add test log event-> writes a real event to the log file
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.debug_log_tools.DebugLogActivity
 */
class DebugLogActivity : AppCompatActivity() {

    private companion object {
        const val TAG_UI = "DebugLogUI"
    }

    private lateinit var statusView: TextView
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Debug Log Tools"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Debug log tools"
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
            text = "Add test log event"
            setOnClickListener { onAddTestEvent() }
        })
        root.addView(Button(this).apply {
            text = "Refresh"
            setOnClickListener { onRefresh() }
        })
        root.addView(Button(this).apply {
            text = "Copy diagnostic bundle"
            setOnClickListener { onCopyBundle() }
        })
        root.addView(Button(this).apply {
            text = "Clear logs"
            setOnClickListener { onClear() }
        })

        root.addView(TextView(this).apply {
            text = "Recent events"
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

        DebugLogStore.append(this, TAG_UI, "DebugLogActivity opened")
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun onAddTestEvent() {
        DebugLogStore.append(
            this,
            "TEST",
            "Manual test log event from DebugLogActivity (button tap)",
        )
        Toast.makeText(this, "Test event written", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun onRefresh() {
        refresh()
        Toast.makeText(this, "Reloaded from log file", Toast.LENGTH_SHORT).show()
    }

    private fun onCopyBundle() {
        val bundle = DebugLogStore.buildDiagnosticBundle(this)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_LONG).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("CyanBridge diagnostics", bundle))
        DebugLogStore.append(this, TAG_UI, "Diagnostic bundle copied to clipboard")
        Toast.makeText(this, "Diagnostic bundle copied", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun onClear() {
        DebugLogStore.clear(this)
        DebugLogStore.append(this, TAG_UI, "Logs cleared from DebugLogActivity")
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun refresh() {
        statusView.text = buildStatus()
        val recent = DebugLogStore.readRecent(this)
        logView.text = if (recent.isEmpty()) "(no events captured yet)" else recent
    }

    private fun buildStatus(): String {
        val path = DebugLogStore.logFilePath(this)
        val recent = DebugLogStore.readRecent(this)
        val count = if (recent.isEmpty()) 0 else recent.count { it == '\n' } + 1
        return buildString {
            appendLine("log file : $path")
            append("events   : $count shown")
        }
    }
}
