package com.fersaiyan.cyanbridge.ai_user_shell

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
 * Automation core UI shell ("Автоматизация") — part of the user-facing :ai-user-shell
 * module. This is the weakly-coupled entry point for a future Tasker/plugin replacement.
 *
 * IMPORTANT: this is a UI-ONLY placeholder / MVP shell. It deliberately contains NO
 * automation executor, NO background service, NO AccessibilityService hook, NO
 * permissions, NO networking and NO storage. It only lists the planned automation
 * categories so the feature has a stable, safe in-app home; nothing here runs any action.
 *
 * Like the other in-module screens (settings, history) it is opened only through the
 * internal [FeatureActions.AUTOMATION] marker with an explicit Activity intent and is
 * declared android:exported="false", so external apps cannot launch it.
 */
class AutomationShellActivity : AppCompatActivity() {

    /** A planned automation category. These are labels only — none is wired to an executor. */
    private data class Category(
        val title: String,
        val description: String,
    )

    private val categories = listOf(
        Category(
            title = "Голосовые команды",
            description = "Запуск сценариев по голосовой команде",
        ),
        Category(
            title = "События очков / фото",
            description = "Реакция на нажатия кнопок очков и съёмку фото",
        ),
        Category(
            title = "AI-действия",
            description = "Автоматические действия на основе ответов AI",
        ),
        Category(
            title = "Локальные действия устройства",
            description = "Управление функциями телефона на устройстве",
        ),
    )

    private val density: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Автоматизация"

        val pad = dp(16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "Автоматизация"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Заготовка раздела автоматизации"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        // Prominent safety banner: makes clear this is a non-executing placeholder.
        root.addView(buildBanner())

        val categoriesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        categoriesContainer.addView(TextView(this).apply {
            text = "Планируемые категории"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(8))
        })
        categories.forEach { categoriesContainer.addView(buildCategoryView(it)) }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(categoriesContainer)
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
            text = "Это пустой каркас интерфейса. Автоматизация пока НЕ выполняется: " +
                "нет фоновых служб, разрешений и действий. Ниже — только список " +
                "запланированных категорий."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(6), 0, 0)
        })
        return banner
    }

    private fun buildCategoryView(category: Category): View {
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
            text = category.title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        cardLayout.addView(TextView(this).apply {
            text = category.description
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        })
        cardLayout.addView(TextView(this).apply {
            text = "Будет добавлено позже"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, 0)
        })
        return cardLayout
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
