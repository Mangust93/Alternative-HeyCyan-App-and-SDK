package com.fersaiyan.cyanbridge.glasses_button_event_tools

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Glasses button-event diagnostics.
 *
 * Lives in the optional, standalone :glasses-button-event-tools module. It has NO
 * compile dependency on :app, the glasses SDK (com.oudmon.ble.*), BLE, the media flow
 * or :conversation-translation, and it binds nothing to translation.
 *
 * Why this exists separately from :headset-button-tools: glasses buttons were found NOT
 * to arrive as Android key / media-button events. They arrive over the HeyCyan SDK BLE
 * notify path (GlassesDeviceNotifyListener.parseData -> GlassesDeviceNotifyRsp), which
 * :app already logs for EVERY frame as:
 *     Log.i("DeviceNotify", "cmdType=..., loadData=...")
 *
 * This screen is therefore read-only: it tails the app's OWN process logcat for the
 * "DeviceNotify" tag (the same technique SettingsActivity already uses for its log
 * bundle) and decodes the loadData[6] opcode into a human label. It never registers an
 * SDK listener and never changes any glasses / BLE / media behaviour.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.glasses_button_event_tools.GlassesButtonEventDiagnosticsActivity
 */
class GlassesButtonEventDiagnosticsActivity : AppCompatActivity() {

    private companion object {
        const val MAX_EVENTS = 200
        const val MAX_LOG_LINES = 500
        const val POLL_INTERVAL_MS = 1_000L

        /** The tag :app prints every glasses notify frame under (MainActivity). */
        const val LOG_TAG = "DeviceNotify"

        /** Matches the frame line: "cmdType=100, loadData=-95,2,3,...". */
        val FRAME_REGEX =
            Regex("""cmdType=(-?\d+),\s*loadData=(.*)$""")

        /**
         * loadData[6] opcode -> human label, mirrored (NOT imported) from
         * MainActivity.MyDeviceNotifyListener.parseData so this module stays
         * dependency-free. Opcodes that are physical button presses are flagged.
         */
        fun describeOpcode(opcode: Int): Pair<String, Boolean> = when (opcode) {
            0x02 -> "AI Photo / кнопка фото (быстрое распознавание)" to true
            0x03 -> "AI / микрофон (кнопка)" to true
            0x0c -> "Пауза (кнопка паузы)" to true
            0x04 -> "OTA upgrade прогресс" to false
            0x05 -> "Батарея (отчёт)" to false
            0x0d -> "Отвязка приложения" to false
            0x0e -> "Мало памяти" to false
            0x10 -> "Пауза перевода" to false
            0x12 -> "Изменение громкости" to false
            0x08 -> "WiFi IP (data download)" to false
            0x09 -> "P2P / WiFi ошибка" to false
            else -> "Неизвестный opcode" to false
        }
    }

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val events = ArrayDeque<String>()

    /** Raw logcat lines already shown, so polling only adds genuinely new frames. */
    private val seenRawLines = LinkedHashSet<String>()

    private lateinit var statusView: TextView
    private lateinit var logView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var readerExecutor: ExecutorService? = null
    @Volatile
    private var activeProcess: Process? = null
    private var readGeneration = 0
    private var pollStatus = "Logcat: ожидание"
    private var sawAnyFrame = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            requestLogcatRead(primeOnly = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Диагностика кнопок очков"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Диагностика кнопок очков"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, gap)
        })

        root.addView(TextView(this).apply {
            text = "Нажмите переднюю / заднюю / сенсорную кнопку очков. " +
                "Кнопки очков приходят не как Android-клавиши, а как BLE-уведомления " +
                "(tag DeviceNotify). Каждое уведомление появится ниже: сырое и разобранное."
            textSize = 14f
            setPadding(0, 0, 0, gap)
        })

        statusView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextIsSelectable(true)
            setPadding(0, 0, 0, gap)
        }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "Очистить"
            setOnClickListener { onClear() }
        })

        root.addView(TextView(this).apply {
            text = "Последние события"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, gap)
        })

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
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

        render()
    }

    override fun onStart() {
        super.onStart()
        readGeneration += 1
        readerExecutor = Executors.newSingleThreadExecutor()
        // Skip whatever is already in the buffer so the screen only shows presses made
        // while it is open; mark those existing lines as "seen".
        requestLogcatRead(primeOnly = true)
    }

    override fun onStop() {
        stopReading()
        super.onStop()
    }

    override fun onDestroy() {
        stopReading()
        super.onDestroy()
    }

    private fun stopReading() {
        readGeneration += 1
        handler.removeCallbacks(pollRunnable)
        activeProcess?.destroy()
        activeProcess = null
        readerExecutor?.shutdownNow()
        readerExecutor = null
    }

    private fun requestLogcatRead(primeOnly: Boolean) {
        val executor = readerExecutor ?: return
        val generation = readGeneration
        executor.execute {
            val lines = readDeviceNotifyLogcat()
            handler.post {
                if (generation != readGeneration || isFinishing || isDestroyed) return@post
                if (primeOnly) {
                    primeSeenLinesFromBuffer(lines)
                } else {
                    pollLogcatOnce(lines)
                }
                handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
            }
        }
    }

    /** Read the current buffer once and mark every DeviceNotify line as already seen. */
    private fun primeSeenLinesFromBuffer(lines: List<String>?) {
        if (lines == null) {
            pollStatus = "Logcat: недоступен (нет доступа к собственному логу)"
            render()
            return
        }
        updateSeenSnapshot(lines)
        pollStatus = "Logcat: слушаю tag \"$LOG_TAG\""
        render()
    }

    private fun pollLogcatOnce(lines: List<String>?) {
        lines ?: run {
            pollStatus = "Logcat: недоступен (нет доступа к собственному логу)"
            render()
            return
        }
        var added = false
        for (raw in lines) {
            if (raw in seenRawLines) continue
            added = true
            sawAnyFrame = true
            recordRawLine(raw)
        }
        updateSeenSnapshot(lines)
        if (added) render()
    }

    private fun updateSeenSnapshot(lines: List<String>) {
        seenRawLines.clear()
        seenRawLines.addAll(lines.takeLast(MAX_LOG_LINES))
    }

    /**
     * Read a bounded snapshot of the app's own DeviceNotify buffer. This runs on the
     * reader executor; onStop/onDestroy destroys an in-flight process before shutting
     * down the executor. Reading own logs requires no READ_LOGS permission.
     */
    private fun readDeviceNotifyLogcat(): List<String>? {
        var process: Process? = null
        return try {
            process = ProcessBuilder(
                "logcat",
                "-d",
                "-t",
                MAX_LOG_LINES.toString(),
                "-v",
                "time",
                "-s",
                "$LOG_TAG:I",
            ).redirectErrorStream(true).start()
            activeProcess = process
            val text = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() != 0) return null
            text.lineSequence()
                .map { it.trim() }
                .filter { it.contains("$LOG_TAG:") }
                .toList()
        } catch (_: Exception) {
            null
        } finally {
            if (activeProcess === process) activeProcess = null
            process?.destroy()
        }
    }

    private fun recordRawLine(rawLogcatLine: String) {
        val match = FRAME_REGEX.find(rawLogcatLine)
        val parsed = if (match != null) {
            val cmdType = match.groupValues[1]
            val payload = match.groupValues[2].trim()
            val bytes = payload
                .split(',')
                .map { it.trim().toIntOrNull() }
            val malformedPayload = payload.isEmpty() ||
                bytes.any { it == null || it !in -128..127 }
            val opcode = bytes.getOrNull(6)
            if (malformedPayload) {
                "cmdType=$cmdType (malformed payload - opcode не разобран)"
            } else if (opcode != null) {
                val (label, isButton) = describeOpcode(opcode and 0xFF)
                val isPress = isButton &&
                    (opcode and 0xFF !in setOf(0x03, 0x0c) || bytes.getOrNull(7) == 1)
                val flag = if (isPress) "  <== КНОПКА" else ""
                val state = if (isButton && !isPress) " (не press-state)" else ""
                "cmdType=$cmdType opcode=0x%02x → $label$state$flag".format(opcode and 0xFF)
            } else {
                "cmdType=$cmdType (payload короче 7 байт — нет opcode)"
            }
        } else {
            // A DeviceNotify line that is not a raw frame (e.g. a decoded
            // "AI Photo Button Pressed" message); surface it verbatim.
            rawLogcatLine.substringAfter("$LOG_TAG:").trim()
        }

        val line = buildString {
            append(timestampFormat.format(Date()))
            append("  ")
            append(parsed)
            append("\n    RAW: ")
            append(rawLogcatLine)
        }
        events.addLast(line)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    private fun onClear() {
        events.clear()
        render()
    }

    private fun render() {
        val eventStatus = if (events.isEmpty()) {
            if (sawAnyFrame) "События: получены и очищены" else "События: ожидание"
        } else {
            "События: получено - ${events.size} (хранятся последние $MAX_EVENTS)"
        }
        statusView.text = "$pollStatus\n$eventStatus"
        logView.text = if (events.isEmpty()) {
            "(событий пока нет — нажмите кнопку на очках)"
        } else {
            events.reversed().joinToString("\n")
        }
    }
}
