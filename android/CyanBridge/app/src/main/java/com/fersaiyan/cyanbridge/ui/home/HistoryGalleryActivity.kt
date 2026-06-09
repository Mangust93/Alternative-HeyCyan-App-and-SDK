package com.fersaiyan.cyanbridge.ui.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * История / Галерея — landing for the future unified gallery/history (Module E).
 *
 * For now this opens the existing AI request history (which lives in the optional
 * :ai-user-shell module) through the package-scoped AI_USER_SHELL action, checked with
 * resolveActivity so it degrades to "скоро" when the module is toggled out of the build.
 *
 * The screen also documents the future unified sections (фото с очков, фото + вопрос + ответ,
 * аудио, видео, заметки) so the direction is visible without building them yet.
 */
class HistoryGalleryActivity : AppCompatActivity() {

    private val density: Float by lazy { resources.displayMetrics.density }

    private val futureSections = listOf(
        "Фото с очков",
        "Фото + вопрос + ответ",
        "Аудио",
        "Видео",
        "Заметки",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "История / Галерея"

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "История / Галерея"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Будущая единая галерея и история. Пока открывается история AI-запросов."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        container.addView(buildHistoryCard())

        container.addView(TextView(this).apply {
            text = "Будущие разделы"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        })
        futureSections.forEach { container.addView(buildFutureSectionView(it)) }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(container)
        }
        root.addView(scroll)

        setContentView(root)
    }

    private fun buildHistoryCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        card.addView(TextView(this).apply {
            text = "История AI-запросов"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Список прошлых AI-запросов"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(Button(this).apply {
            text = "Открыть историю AI-запросов"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener { openAiHistory() }
        })
        return card
    }

    private fun buildFutureSectionView(name: String): View {
        val card = LinearLayout(this).apply {
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
        card.addView(TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Скоро"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        })
        return card
    }

    private fun openAiHistory() {
        val intent = Intent(FeatureIntents.AI_USER_SHELL)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
        if (!resolveFeatureActivity(intent)) {
            Toast.makeText(this, "Раздел истории пока недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Не удалось открыть историю", Toast.LENGTH_SHORT).show() }
    }

    private fun resolveFeatureActivity(intent: Intent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ) != null
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }

    private fun dp(value: Int): Int = (value * density).toInt()
}
