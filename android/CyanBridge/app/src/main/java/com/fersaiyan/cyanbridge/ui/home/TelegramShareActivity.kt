package com.fersaiyan.cyanbridge.ui.home

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Telegram — a simple share-out screen (Module E).
 *
 * It sends the typed text via a standard Android ACTION_SEND intent. If the Telegram app is
 * installed it targets the Telegram package directly; otherwise it falls back to the generic
 * system share sheet. There is intentionally NO Bot API token and NO direct networking here —
 * delivery is handled entirely by whichever app the user picks.
 */
class TelegramShareActivity : AppCompatActivity() {

    private val density: Float by lazy { resources.displayMetrics.density }

    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Telegram"

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "Telegram"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Отправка текста через системный обмен. Bot API пока не используется."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        input = EditText(this).apply {
            hint = "Введите текст для отправки…"
            setHintTextColor(Color.parseColor("#6B7178"))
            setTextColor(Color.WHITE)
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        root.addView(input)

        root.addView(Button(this).apply {
            text = "Отправить в Telegram"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { shareText() }
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
        }
        root.addView(scroll)

        setContentView(root)
    }

    private fun shareText() {
        val text = input.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Текст пустой", Toast.LENGTH_SHORT).show()
            return
        }

        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        // Prefer the Telegram app directly when it is installed; otherwise fall back to the
        // generic system share sheet so the user can pick any messenger.
        val telegramAvailable = TELEGRAM_PACKAGES.firstOrNull { isPackageInstalled(it) }
        if (telegramAvailable != null) {
            val direct = Intent(baseIntent).setPackage(telegramAvailable)
            if (runCatching { startActivity(direct); true }.getOrDefault(false)) {
                return
            }
        }

        runCatching {
            startActivity(Intent.createChooser(baseIntent, "Отправить через"))
        }.onFailure {
            Toast.makeText(this, "Не удалось открыть обмен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = runCatching {
        packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    private fun dp(value: Int): Int = (value * density).toInt()

    companion object {
        private val TELEGRAM_PACKAGES = listOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
        )
    }
}
