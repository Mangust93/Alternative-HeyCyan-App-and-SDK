package com.fersaiyan.cyanbridge.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Транскрибация — UI-only placeholder shell (Module E).
 *
 * Lists the planned transcription modes (audio → text, video → audio, video → text). This is
 * a stable, safe home for the feature: it performs NO transcription, hosts no service, does no
 * networking and declares no permissions. Each mode shows "скоро" until a later step wires it.
 */
class TranscriptionPlaceholderActivity : AppCompatActivity() {

    private data class Mode(val title: String, val description: String)

    private val modes = listOf(
        Mode("Аудио → текст", "Расшифровка аудиозаписи в текст"),
        Mode("Видео → аудио", "Извлечение звуковой дорожки из видео"),
        Mode("Видео → текст", "Расшифровка речи из видео в текст"),
    )

    private val density: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Транскрибация"

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "Транскрибация"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Заготовка раздела расшифровки"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })
        root.addView(buildBanner())

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        modes.forEach { container.addView(buildModeView(it)) }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(container)
        }
        root.addView(scroll)

        setContentView(root)
    }

    private fun buildBanner(): View {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
                setStroke(dp(1), Color.parseColor("#21D0C3"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        banner.addView(TextView(this).apply {
            text = "Безопасная заготовка (MVP)"
            textSize = 15f
            setTextColor(Color.parseColor("#21D0C3"))
            setTypeface(typeface, Typeface.BOLD)
        })
        banner.addView(TextView(this).apply {
            text = "Это пустой каркас интерфейса. Расшифровка пока НЕ выполняется. " +
                "Ниже — только список запланированных режимов."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(6), 0, 0)
        })
        return banner
    }

    private fun buildModeView(mode: Mode): View {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        }
        cardLayout.addView(TextView(this).apply {
            text = mode.title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        cardLayout.addView(TextView(this).apply {
            text = mode.description
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        })
        cardLayout.addView(TextView(this).apply {
            text = "Скоро"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        })
        return cardLayout
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
