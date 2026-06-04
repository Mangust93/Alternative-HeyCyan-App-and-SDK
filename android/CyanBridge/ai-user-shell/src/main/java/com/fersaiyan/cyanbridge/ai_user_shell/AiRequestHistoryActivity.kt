package com.fersaiyan.cyanbridge.ai_user_shell

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryFeatureType
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryItem
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryStatus
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI request history screen ("История запросов") — part of the user-facing
 * :ai-user-shell module.
 *
 * It reads the locally-stored AI request log through [AiRequestHistoryStore] (plain
 * SharedPreferences, see :ai-history-core) and shows it newest-first. It performs no
 * networking, no server sync, and reads neither the OpenRouter API key nor any other
 * credential — history records carry no key, and none is shown here. A corrupt/unreadable
 * store yields an empty list (never a crash), so this screen simply shows the empty state.
 *
 * Opened only through the package-scoped [FeatureActions.AI_HISTORY] action and declared
 * android:exported="false", so external apps cannot launch it.
 */
class AiRequestHistoryActivity : AppCompatActivity() {

    private lateinit var store: AiRequestHistoryStore

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    private val density: Float by lazy { resources.displayMetrics.density }

    private val timeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "История запросов"
        store = AiRequestHistoryStore(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "История запросов"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })

        emptyView = TextView(this).apply {
            text = "История запросов пока пуста"
            textSize = 14f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(emptyView)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(listContainer)
        }
        root.addView(scroll)

        root.addView(Button(this).apply {
            text = "Очистить историю"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { onClearHistory() }
        })
        root.addView(Button(this).apply {
            text = "Назад"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener { finish() }
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        renderHistory()
    }

    private fun onClearHistory() {
        store.clearHistory()
        renderHistory()
        Toast.makeText(this, "История запросов очищена", Toast.LENGTH_SHORT).show()
    }

    private fun renderHistory() {
        // Reads are crash-safe: a corrupt store returns an empty list (see
        // AiRequestHistoryStore), so this never throws. Newest items come first.
        val items = runCatching { store.listHistoryItems() }.getOrElse { emptyList() }

        listContainer.removeAllViews()
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        items.forEach { listContainer.addView(buildItemView(it)) }
    }

    private fun buildItemView(item: AiRequestHistoryItem): View {
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

        // Header: date/time on the left, status chip on the right.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = formatTime(item.timestampMillis)
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = statusLabel(item.status)
            textSize = 12f
            setTextColor(statusColor(item.status))
            setTypeface(typeface, Typeface.BOLD)
        })
        cardLayout.addView(header)

        // Feature type + model line.
        cardLayout.addView(TextView(this).apply {
            text = buildString {
                append(featureLabel(item.featureType))
                item.modelId?.takeIf { it.isNotBlank() }?.let {
                    append(" · ")
                    append(it)
                }
            }
            textSize = 13f
            setTextColor(Color.parseColor("#C7CCD1"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        })

        // Question (visually shortened when long).
        val question = item.question.takeIf { it.isNotBlank() } ?: "—"
        cardLayout.addView(TextView(this).apply {
            text = "Вопрос: ${shorten(question, MAX_QUESTION_CHARS)}"
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, 0)
        })

        // Short answer, when present.
        item.answer?.takeIf { it.isNotBlank() }?.let { answer ->
            cardLayout.addView(TextView(this).apply {
                text = "Ответ: ${shorten(answer, MAX_ANSWER_CHARS)}"
                textSize = 13f
                setTextColor(Color.parseColor("#C7CCD1"))
                setPadding(0, dp(6), 0, 0)
            })
        }

        // Error message only for failure-like outcomes.
        val showError = item.status == AiRequestHistoryStatus.ERROR ||
            item.status == AiRequestHistoryStatus.CONFIG_MISSING
        if (showError) {
            val error = item.errorMessage?.takeIf { it.isNotBlank() } ?: "Неизвестная ошибка"
            cardLayout.addView(TextView(this).apply {
                text = "Ошибка: ${shorten(error, MAX_ANSWER_CHARS)}"
                textSize = 13f
                setTextColor(Color.parseColor("#FF6B6B"))
                setPadding(0, dp(6), 0, 0)
            })
        }

        return cardLayout
    }

    private fun formatTime(millis: Long): String =
        runCatching { timeFormat.format(Date(millis)) }.getOrElse { "—" }

    private fun featureLabel(type: AiRequestHistoryFeatureType): String = when (type) {
        AiRequestHistoryFeatureType.PHOTO_QUESTION -> "Фото и вопрос"
        AiRequestHistoryFeatureType.TRANSLATION -> "Переводчик"
        AiRequestHistoryFeatureType.OTHER -> "Другое"
    }

    private fun statusLabel(status: AiRequestHistoryStatus): String = when (status) {
        AiRequestHistoryStatus.SUCCESS -> "Успешно"
        AiRequestHistoryStatus.ERROR -> "Ошибка"
        AiRequestHistoryStatus.CANCELLED -> "Отменено"
        AiRequestHistoryStatus.CONFIG_MISSING -> "Нет настроек"
    }

    private fun statusColor(status: AiRequestHistoryStatus): Int = when (status) {
        AiRequestHistoryStatus.SUCCESS -> Color.parseColor("#21D0C3")
        AiRequestHistoryStatus.ERROR -> Color.parseColor("#FF6B6B")
        AiRequestHistoryStatus.CONFIG_MISSING -> Color.parseColor("#FFB347")
        AiRequestHistoryStatus.CANCELLED -> Color.parseColor("#9AA0A6")
    }

    /** Visually shorten long text with an ellipsis; the stored value is never changed. */
    private fun shorten(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max).trimEnd() + "…"

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val MAX_QUESTION_CHARS = 160
        const val MAX_ANSWER_CHARS = 240
    }
}
