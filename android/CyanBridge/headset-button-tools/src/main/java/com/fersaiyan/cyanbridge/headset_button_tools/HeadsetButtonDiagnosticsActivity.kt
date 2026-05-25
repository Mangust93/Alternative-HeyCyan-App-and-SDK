package com.fersaiyan.cyanbridge.headset_button_tools

import android.content.Intent
import android.graphics.Typeface
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Headset / glasses button diagnostics.
 *
 * Lives in the optional, standalone :headset-button-tools module. It has NO compile
 * dependency on :app, the glasses SDK, the media flow or :conversation-translation,
 * and it intentionally binds nothing to translation. Its single job: show a tester
 * whether pressing a button on the glasses/headset actually produces a key or
 * media-button event on Android.
 *
 * Two capture paths run at once:
 *   1. [dispatchKeyEvent] / [onKeyDown] catch hardware key events while this screen is
 *      in the foreground.
 *   2. A framework [MediaSession] set active receives ACTION_MEDIA_BUTTON events that
 *      the system routes to the active media session (works even for buttons delivered
 *      as media-button intents rather than focused key events).
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.headset_button_tools.HeadsetButtonDiagnosticsActivity
 */
class HeadsetButtonDiagnosticsActivity : AppCompatActivity() {

    private companion object {
        const val MAX_EVENTS = 200

        /** Keycodes that are the point of this diagnostic; highlighted in the log. */
        val TARGET_KEYCODES = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_VOICE_ASSIST,
        )
    }

    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val events = ArrayDeque<String>()

    private lateinit var statusView: TextView
    private lateinit var logView: TextView

    private var mediaSession: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Диагностика кнопки гарнитуры"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val gap = (8 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Диагностика кнопки гарнитуры"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, gap)
        })

        root.addView(TextView(this).apply {
            text = "Нажмите кнопку на очках/наушниках. " +
                "Каждое полученное событие появится в списке ниже."
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

        setupMediaSession()
        render()
    }

    override fun onDestroy() {
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Catch hardware key events first: this covers focused key delivery for the headset
     * hook, media transport keys and the voice-assist key while the screen is on top.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        recordKeyEvent("dispatchKeyEvent", event)
        // Let the system keep its default handling too; we only observe.
        return super.dispatchKeyEvent(event)
    }

    private fun setupMediaSession() {
        runCatching {
            val session = MediaSession(this, "HeadsetButtonDiagnostics")
            // An active session with a non-NONE playback state is what makes the system
            // route ACTION_MEDIA_BUTTON intents to our callback.
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE,
                    )
                    .setState(PlaybackState.STATE_PAUSED, 0L, 1f)
                    .build(),
            )
            session.setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val keyEvent = mediaButtonIntent
                        .getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (keyEvent != null) {
                        recordKeyEvent("ACTION_MEDIA_BUTTON", keyEvent)
                    } else {
                        record("ACTION_MEDIA_BUTTON (без KeyEvent): ${mediaButtonIntent.action}")
                    }
                    return true
                }
            })
            session.isActive = true
            mediaSession = session
            record("MediaSession активна — слушаю ACTION_MEDIA_BUTTON")
        }.onFailure {
            record("MediaSession недоступна: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    private fun recordKeyEvent(source: String, event: KeyEvent) {
        // Only log key-down to avoid doubling every press with its key-up.
        if (event.action != KeyEvent.ACTION_DOWN) return
        val keyName = KeyEvent.keyCodeToString(event.keyCode)
        val target = if (event.keyCode in TARGET_KEYCODES) "  <== ЦЕЛЕВОЕ" else ""
        record("$source: $keyName (code=${event.keyCode})$target")
    }

    private fun record(message: String) {
        val line = "${timestampFormat.format(Date())}  $message"
        events.addLast(line)
        while (events.size > MAX_EVENTS) events.removeFirst()
        render()
    }

    private fun onClear() {
        events.clear()
        render()
    }

    private fun render() {
        statusView.text = if (events.isEmpty()) {
            "Статус: Ожидание события"
        } else {
            "Статус: получено событий — ${events.size}"
        }
        logView.text = if (events.isEmpty()) {
            "(событий пока нет)"
        } else {
            // Most recent first.
            events.reversed().joinToString("\n")
        }
    }
}
