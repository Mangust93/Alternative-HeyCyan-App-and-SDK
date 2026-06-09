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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * CyanBridge Home Screen V2 — the new product home (Module E).
 *
 * This is the main, user-facing entry point of the app. It replaces the old habit of
 * dropping the user straight into the device/diagnostics screen ([MainActivity]) and
 * instead presents large, touch-friendly cards for each product area.
 *
 * Design goals / constraints:
 *  - It is weakly coupled. Existing feature screens that live in optional feature modules
 *    (translator, photo-question) are opened only through package-scoped Intent actions
 *    checked with resolveActivity first, so a toggled-out module degrades to a Toast
 *    instead of crashing. It never references those modules' classes.
 *  - It does NOT change device sync/search. The "Синхронизация устройств" card simply
 *    opens the existing, working [MainActivity] flow unchanged.
 *  - New lightweight screens (quick note, transcription shell, Telegram share, plugins
 *    catalog, unified history landing) live in this same module as plain Activities.
 *
 * The screen is dark-styled to match the existing AI shell and built programmatically to
 * stay lightweight (no new layout/resource wiring).
 */
class HomeV2Activity : AppCompatActivity() {

    /** One home card. [launch] performs the navigation when the whole card is tapped. */
    private data class HomeCard(
        val title: String,
        val description: String,
        val launch: () -> Unit,
    )

    private val density: Float by lazy { resources.displayMetrics.density }

    private val cards: List<HomeCard> by lazy {
        listOf(
            HomeCard(
                title = "Синхронизация устройств",
                description = "Поиск, подключение и синхронизация очков",
                launch = { openDeviceSync() },
            ),
            HomeCard(
                title = "Переводчик",
                description = "Перевод речи с озвучиванием в очки",
                launch = { openFeatureAction(FeatureIntents.CONVERSATION_TRANSLATION) },
            ),
            HomeCard(
                title = "Фото и вопрос",
                description = "Выберите фото и задайте вопрос AI",
                launch = { openFeatureAction(FeatureIntents.PHOTO_QUESTION) },
            ),
            HomeCard(
                title = "Транскрибация",
                description = "Аудио → текст, видео → аудио, видео → текст",
                launch = { openLocal(TranscriptionPlaceholderActivity::class.java) },
            ),
            HomeCard(
                title = "Быстрая заметка",
                description = "Быстро сохранить текстовую заметку на телефоне",
                launch = { openLocal(QuickNoteActivity::class.java) },
            ),
            HomeCard(
                title = "История / Галерея",
                description = "История AI-запросов и будущая единая галерея",
                launch = { openLocal(HistoryGalleryActivity::class.java) },
            ),
            HomeCard(
                title = "Telegram",
                description = "Отправить текст через системный обмен в Telegram",
                launch = { openLocal(TelegramShareActivity::class.java) },
            ),
            HomeCard(
                title = "Плагины",
                description = "Каталог автоматизаций и интеграций (заготовка)",
                launch = { openLocal(PluginsCatalogActivity::class.java) },
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "CyanBridge"

        val pad = dp(16)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "CyanBridge"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Главный экран — выберите раздел"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })

        val cardsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        cards.forEach { cardsContainer.addView(buildCardView(it)) }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
            isFillViewport = true
            addView(cardsContainer)
        }
        root.addView(scroll)

        setContentView(root)
    }

    private fun buildCardView(card: HomeCard): View {
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#1B2026"))
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { runCatching { card.launch() } }
        }

        cardLayout.addView(TextView(this).apply {
            text = card.title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        cardLayout.addView(TextView(this).apply {
            text = card.description
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(6), 0, 0)
        })
        return cardLayout
    }

    /** Opens the existing, working device sync/search screen unchanged. */
    private fun openDeviceSync() {
        runCatching { startActivity(Intent(this, MainActivity::class.java)) }
            .onFailure { Toast.makeText(this, "Не удалось открыть раздел", Toast.LENGTH_SHORT).show() }
    }

    private fun openLocal(target: Class<*>) {
        runCatching { startActivity(Intent(this, target)) }
            .onFailure { Toast.makeText(this, "Не удалось открыть раздел", Toast.LENGTH_SHORT).show() }
    }

    /**
     * Opens a feature screen that lives in an optional feature module, via a package-scoped
     * Intent action only. Checks resolveActivity first so the screen degrades gracefully to
     * a Toast when the module is toggled out of the build.
     */
    private fun openFeatureAction(action: String) {
        val intent = Intent(action)
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
        if (!resolveFeatureActivity(intent)) {
            Toast.makeText(this, "Модуль не включён", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Не удалось открыть функцию", Toast.LENGTH_SHORT).show() }
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
