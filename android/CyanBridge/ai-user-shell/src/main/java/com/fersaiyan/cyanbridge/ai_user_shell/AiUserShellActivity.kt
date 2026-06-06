package com.fersaiyan.cyanbridge.ai_user_shell

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

/**
 * AI user shell — the first user-facing "AI-функции" / "AI ассистент" screen.
 *
 * Lives in the optional, standalone :ai-user-shell module. It has NO compile dependency
 * on :app, the glasses SDK (com.oudmon.ble.*), BLE, the media flow, OpenRouter, or — by
 * design — on the feature modules whose screens it launches
 * (:conversation-translation, :photo-question-tools). It opens those screens only through
 * package-scoped Intent actions checked with resolveActivity, so:
 *  - it never references a feature module class (no compile-time dependency);
 *  - a feature that is toggled out of the build simply shows "Модуль не включён";
 *  - launch failures are caught and surfaced as a Toast instead of crashing.
 *
 * This is the USER-facing counterpart to the debug "Инструменты / Диагностика" shell;
 * that diagnostics shell is unchanged and still hosts the same features for testers.
 *
 * The screen is opened from the host app through the package-scoped AI_USER_SHELL intent
 * action; it is deliberately not exported for adb/external launch.
 */
class AiUserShellActivity : AppCompatActivity() {

    /** One card in the shell. [action] == null means a not-yet-implemented placeholder. */
    private data class Card(
        val title: String,
        val description: String,
        val action: String?,
    )

    private val cards = listOf(
        Card(
            title = "Переводчик",
            description = "Перевод речи с озвучиванием в очки",
            action = FeatureActions.CONVERSATION_TRANSLATION,
        ),
        Card(
            title = "Фото и вопрос",
            description = "Выберите фото и задайте вопрос AI",
            action = FeatureActions.PHOTO_QUESTION,
        ),
        Card(
            title = "История запросов",
            description = "Список прошлых AI-запросов",
            action = FeatureActions.AI_HISTORY,
        ),
        Card(
            title = "Настройки AI",
            description = "OpenRouter API key и модель",
            action = FeatureActions.AI_SETTINGS,
        ),
        Card(
            title = "Автоматизация",
            description = "Сценарии автоматизации (заготовка)",
            action = FeatureActions.AUTOMATION,
        ),
    )

    private lateinit var cardsContainer: LinearLayout
    private val density: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AI-функции"

        val pad = dp(16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "AI-функции"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Пользовательские AI-функции приложения"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(cardsContainer)
        }
        root.addView(scroll)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // A feature module's availability only changes between installs, but re-render on
        // resume so the screen always reflects the current resolvable set.
        renderCards()
    }

    private fun renderCards() {
        cardsContainer.removeAllViews()
        cards.forEach { cardsContainer.addView(buildCardView(it)) }
    }

    private fun buildCardView(card: Card): View {
        val available = card.action != null && isActionAvailable(card.action)

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
            text = card.title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        cardLayout.addView(TextView(this).apply {
            text = card.description
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        val statusText: String
        val statusColor: Int
        when {
            card.action == null -> {
                statusText = "Будет добавлено позже"
                statusColor = Color.parseColor("#9AA0A6")
            }
            available -> {
                statusText = "Доступно"
                statusColor = Color.parseColor("#21D0C3")
            }
            else -> {
                statusText = "Модуль не включён"
                statusColor = Color.parseColor("#9AA0A6")
            }
        }

        statusRow.addView(TextView(this).apply {
            text = statusText
            textSize = 13f
            setTextColor(statusColor)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })

        val openButton = Button(this).apply {
            text = "Открыть"
            isEnabled = available
            alpha = if (available) 1f else 0.45f
            if (available) {
                setOnClickListener { openAction(card.action!!) }
            }
        }
        statusRow.addView(openButton)

        cardLayout.addView(statusRow)
        return cardLayout
    }

    /**
     * Actions whose screens live in THIS module (settings, history). They are launched with
     * an explicit Activity intent, are always available, and need no resolveActivity check.
     */
    private fun isInternalAction(action: String): Boolean =
        action == FeatureActions.AI_SETTINGS ||
            action == FeatureActions.AI_HISTORY ||
            action == FeatureActions.AUTOMATION

    private fun featureIntent(action: String): Intent =
        when (action) {
            FeatureActions.AI_SETTINGS -> Intent(this, AiSettingsActivity::class.java)
            FeatureActions.AI_HISTORY -> Intent(this, AiRequestHistoryActivity::class.java)
            FeatureActions.AUTOMATION -> Intent(this, AutomationShellActivity::class.java)
            else -> Intent(action)
                .setPackage(packageName)
                .addCategory(Intent.CATEGORY_DEFAULT)
        }

    private fun isActionAvailable(action: String): Boolean =
        isInternalAction(action) || resolveFeatureActivity(featureIntent(action))

    private fun openAction(action: String) {
        val intent = featureIntent(action)
        if (!isInternalAction(action) && !resolveFeatureActivity(intent)) {
            Toast.makeText(this, "Модуль не включён", Toast.LENGTH_SHORT).show()
            renderCards()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, "Не удалось открыть функцию", Toast.LENGTH_SHORT).show()
            }
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
