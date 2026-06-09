package com.fersaiyan.cyanbridge.ai_user_shell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    /**
     * Single background thread used only to decode small history thumbnails off the UI
     * thread. Image decoding is best-effort: any failure leaves the preview hidden so a
     * missing/unreadable reference never blocks or crashes the screen.
     */
    private val previewExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }

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

    override fun onDestroy() {
        // Stop any in-flight thumbnail decode; a superseded result is dropped (see attachPreview).
        previewExecutor.shutdownNow()
        super.onDestroy()
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

        // Optional photo metadata: a small preview (when the local image reference still
        // resolves) and/or the saved image label. Both degrade gracefully — a missing or
        // unreadable reference simply shows no preview and never crashes the screen.
        addPhotoPreview(cardLayout, item)

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

    /**
     * Add an optional photo label line and a small image preview for items that carry a
     * local image reference. The label is shown synchronously when present. The preview is
     * decoded on a background thread and shown only if it succeeds; any failure (no
     * reference, revoked access, unreadable/oversized/corrupt image) leaves no preview and
     * never throws — satisfying the "missing image must not crash" requirement.
     */
    private fun addPhotoPreview(card: LinearLayout, item: AiRequestHistoryItem) {
        item.imageLabel?.takeIf { it.isNotBlank() }?.let { label ->
            card.addView(TextView(this).apply {
                text = "Фото: ${shorten(label, MAX_QUESTION_CHARS)}"
                textSize = 13f
                setTextColor(Color.parseColor("#9AA0A6"))
                setPadding(0, dp(6), 0, 0)
            })
        }

        val uriString = item.imageUri?.takeIf { it.isNotBlank() } ?: return
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return

        val previewSize = dp(PREVIEW_SIZE_DP)
        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            // Hidden until a bitmap is ready, so a failed/slow decode shows nothing at all.
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.parseColor("#101418"))
            }
            layoutParams = LinearLayout.LayoutParams(previewSize, previewSize).apply {
                topMargin = dp(8)
            }
        }
        card.addView(preview)

        // Tag the target view with the request so a recycled/superseded decode is dropped.
        preview.tag = uriString
        previewExecutor.execute {
            val bitmap = decodeThumbnail(uri, previewSize)
            if (bitmap == null) return@execute
            preview.post {
                if (isDestroyed || isFinishing) return@post
                if (preview.tag != uriString) return@post
                preview.setImageBitmap(bitmap)
                preview.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Best-effort, memory-bounded thumbnail decode. Uses inJustDecodeBounds to read the
     * dimensions first and an inSampleSize so a large image is never fully loaded. Returns
     * null on any problem (missing/revoked Uri, non-image, decode failure, OOM) instead of
     * throwing, so callers can simply skip the preview.
     */
    private fun decodeThumbnail(uri: Uri, targetPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val target = targetPx.coerceAtLeast(1)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }.getOrNull()

    private fun formatTime(millis: Long): String =
        runCatching { timeFormat.format(Date(millis)) }.getOrElse { "—" }

    private fun featureLabel(type: AiRequestHistoryFeatureType): String = when (type) {
        AiRequestHistoryFeatureType.PHOTO_QUESTION -> "Фото и вопрос"
        AiRequestHistoryFeatureType.TRANSLATION -> "Переводчик"
        AiRequestHistoryFeatureType.OTHER -> "Другое"
    }

    private fun statusLabel(status: AiRequestHistoryStatus): String = when (status) {
        AiRequestHistoryStatus.PENDING -> "Выполняется…"
        AiRequestHistoryStatus.SUCCESS -> "Успешно"
        AiRequestHistoryStatus.ERROR -> "Ошибка"
        AiRequestHistoryStatus.CANCELLED -> "Отменено"
        AiRequestHistoryStatus.CONFIG_MISSING -> "Нет настроек"
    }

    private fun statusColor(status: AiRequestHistoryStatus): Int = when (status) {
        AiRequestHistoryStatus.PENDING -> Color.parseColor("#7AA7FF")
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
        const val PREVIEW_SIZE_DP = 72
    }
}
