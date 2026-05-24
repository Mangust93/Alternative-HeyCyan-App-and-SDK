package com.fersaiyan.cyanbridge.runtime_diagnostics_tools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Real, file-backed store for runtime-diagnostics events.
 *
 * Lives in the optional, standalone :runtime-diagnostics-tools module. It has NO compile
 * dependency on :app and never touches the real glasses / media / BLE / P2P flow or the
 * Moonshine runtime. It only persists the events [RuntimeDiagnostics] hands it (app
 * start, build/device facts, uncaught crashes, activity lifecycle) to app-specific
 * storage and reads them back.
 *
 * Events are stored, one per line, in:
 *   filesDir/runtime-diagnostics/runtime-events.log
 * with the shape:
 *   <timestamp> | <tag> | <message>
 *
 * The file is hard-capped at [MAX_FILE_BYTES]; when an append would exceed the cap the
 * oldest events are dropped (the most recent half is kept) so the log never grows
 * without bound.
 */
object RuntimeDiagnosticsStore {

    private const val DIR_NAME = "runtime-diagnostics"
    private const val FILE_NAME = "runtime-events.log"

    /** Hard cap on the on-disk log size. Oldest lines are trimmed past this. */
    private const val MAX_FILE_BYTES = 1 * 1024 * 1024L // 1 MB

    /** Default number of trailing lines [readRecent] returns when no limit is given. */
    const val DEFAULT_MAX_LINES = 300

    /** Trailing lines embedded in a diagnostic bundle (kept small to stay paste-friendly). */
    private const val BUNDLE_MAX_LINES = 200

    private val lock = Any()

    private val timestampFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    /**
     * Append one real runtime event. Safe to call from any thread (including the
     * uncaught-exception handler thread).
     *
     * @param tag short subsystem tag (e.g. "APP", "CRASH", "LIFECYCLE").
     * @param message the event text; newlines are flattened so one event = one line.
     * @param throwable optional error; recorded compactly as class + message + top frame.
     */
    fun append(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val line = buildString {
            append(timestampFormat.format(Date()))
            append(" | ")
            append(sanitize(tag))
            append(" | ")
            append(sanitize(message))
            if (throwable != null) {
                append(" <- ")
                append(describeThrowable(throwable))
            }
        }

        synchronized(lock) {
            runCatching {
                val file = logFile(context)
                file.appendText(line + "\n")
                if (file.length() > MAX_FILE_BYTES) {
                    trimToCap(file)
                }
            }
        }
    }

    /**
     * Return the most recent [maxLines] events as a single newline-joined string
     * (oldest first). Empty string if nothing has been logged yet.
     */
    fun readRecent(context: Context, maxLines: Int = DEFAULT_MAX_LINES): String {
        synchronized(lock) {
            val file = logFile(context)
            if (!file.exists()) return ""
            val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
            if (lines.isEmpty()) return ""
            val tail = if (lines.size > maxLines) lines.subList(lines.size - maxLines, lines.size) else lines
            return tail.joinToString("\n")
        }
    }

    /** Delete all stored events. Safe no-op if the file does not exist. */
    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { logFile(context).delete() }
        }
    }

    /**
     * Build a compact, copy-paste-friendly diagnostic bundle: app + device facts plus
     * the most recent events. Intended to be pasted straight into ChatGPT.
     */
    fun buildDiagnosticBundle(context: Context): String {
        val pkgInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = pkgInfo?.versionCode ?: -1
        val debuggable =
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val file = logFile(context)
        val sizeBytes = if (file.exists()) file.length() else 0L
        val recent = readRecent(context, BUNDLE_MAX_LINES)
        val eventCount = if (recent.isEmpty()) 0 else recent.count { it == '\n' } + 1

        return buildString {
            appendLine("=== CyanBridge runtime diagnostic bundle ===")
            appendLine("generated   : ${timestampFormat.format(Date())}")
            appendLine()
            appendLine("[APP]")
            appendLine("  package     : ${context.packageName}")
            appendLine("  versionName : $versionName")
            appendLine("  versionCode : $versionCode")
            appendLine("  debuggable  : ${if (debuggable) "YES" else "NO"}")
            appendLine()
            appendLine("[DEVICE]")
            appendLine("  manufacturer: ${Build.MANUFACTURER}")
            appendLine("  model       : ${Build.MODEL}")
            appendLine("  android     : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("[LOG]")
            appendLine("  file        : ${file.absolutePath}")
            appendLine("  size        : $sizeBytes bytes (cap ${MAX_FILE_BYTES})")
            appendLine("  shown events: $eventCount (most recent, max $BUNDLE_MAX_LINES)")
            appendLine()
            appendLine("[RECENT EVENTS]")
            if (recent.isEmpty()) {
                appendLine("  (no events captured yet)")
            } else {
                appendLine(recent)
            }
            appendLine("=== end bundle ===")
        }
    }

    /** Absolute path of the log file, for display in the UI / bundle. */
    fun logFilePath(context: Context): String = logFile(context).absolutePath

    private fun logFile(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, FILE_NAME)
    }

    /** Keep only the most recent half of the file so it stays under the cap. */
    private fun trimToCap(file: File) {
        runCatching {
            val lines = file.readLines()
            if (lines.size < 2) return
            val kept = lines.subList(lines.size / 2, lines.size)
            file.writeText(kept.joinToString("\n", postfix = "\n"))
        }
    }

    private fun describeThrowable(t: Throwable): String {
        val name = t.javaClass.name
        val msg = t.message?.let { sanitize(it) }
        val top = t.stackTrace.firstOrNull()?.let {
            "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
        }
        return buildString {
            append(name)
            if (!msg.isNullOrEmpty()) {
                append(": ")
                append(msg)
            }
            if (top != null) {
                append(" @ ")
                append(top)
            }
        }
    }

    /** Flatten newlines / carriage returns so one event always stays on one line. */
    private fun sanitize(value: String): String =
        value.replace('\n', ' ').replace('\r', ' ').trim()
}
