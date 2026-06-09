package com.fersaiyan.cyanbridge.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Плагины — plugin catalog placeholder (Module E).
 *
 * A weakly-coupled, UI-only catalog of planned phone/service automation & integration
 * modules. This is NOT the translator and NOT the photo-question feature — these are
 * device/service automation integrations only. It runs nothing: there is no executor, no
 * background service, no permissions and no networking. Install / enable / configure are
 * disabled and show "скоро" until a later step wires real plugins.
 */
class PluginsCatalogActivity : AppCompatActivity() {

    private data class Plugin(val name: String, val summary: String)
    private data class PluginCategory(val title: String, val plugins: List<Plugin>)

    // At least 30 planned automation/integration plugins, grouped by category.
    private val categories = listOf(
        PluginCategory(
            "Сеть и API",
            listOf(
                Plugin("HTTP Request", "Произвольный HTTP-запрос"),
                Plugin("Webhook POST", "Отправка POST на webhook"),
                Plugin("Send to Custom API", "Отправка данных в свой API"),
                Plugin("Intent Action", "Запуск произвольного Android Intent"),
            ),
        ),
        PluginCategory(
            "Telegram",
            listOf(
                Plugin("Telegram Message", "Отправка текстового сообщения"),
                Plugin("Telegram Photo", "Отправка фотографии"),
                Plugin("Telegram File", "Отправка файла"),
            ),
        ),
        PluginCategory(
            "Notion",
            listOf(
                Plugin("Notion Note", "Создание заметки в Notion"),
                Plugin("Notion Page", "Создание страницы в Notion"),
            ),
        ),
        PluginCategory(
            "Календарь и напоминания",
            listOf(
                Plugin("Calendar Event", "Создание события в календаре"),
                Plugin("Alarm", "Установка будильника"),
                Plugin("Timer", "Запуск таймера"),
                Plugin("Reminder", "Создание напоминания"),
            ),
        ),
        PluginCategory(
            "Обмен и буфер",
            listOf(
                Plugin("Clipboard Copy", "Копирование текста в буфер обмена"),
                Plugin("Share Text", "Поделиться текстом"),
                Plugin("Share Photo", "Поделиться фотографией"),
            ),
        ),
        PluginCategory(
            "Приложения и система",
            listOf(
                Plugin("Open App", "Открыть приложение"),
                Plugin("Open URL", "Открыть ссылку"),
                Plugin("Open Bluetooth Settings", "Открыть настройки Bluetooth"),
                Plugin("Open Wi-Fi Settings", "Открыть настройки Wi-Fi"),
                Plugin("Open System Settings", "Открыть системные настройки"),
            ),
        ),
        PluginCategory(
            "Файлы и экспорт",
            listOf(
                Plugin("Save Text File", "Сохранить текст в файл"),
                Plugin("Save Photo File", "Сохранить фото в файл"),
                Plugin("Export History Item", "Экспорт элемента истории"),
            ),
        ),
        PluginCategory(
            "Заметки",
            listOf(
                Plugin("Quick Note", "Быстрая заметка"),
                Plugin("Voice Note", "Голосовая заметка"),
                Plugin("Meeting Note", "Заметка со встречи"),
            ),
        ),
        PluginCategory(
            "AI-действия",
            listOf(
                Plugin("AI Summarize Text", "Краткое содержание текста"),
                Plugin("AI Analyze Photo", "Анализ фотографии"),
                Plugin("AI Rewrite Note", "Переписать заметку"),
            ),
        ),
    )

    private val density: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Плагины"

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        val total = categories.sumOf { it.plugins.size }

        root.addView(TextView(this).apply {
            text = "Плагины"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Каталог автоматизаций и интеграций — запланировано $total модулей"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, dp(12))
        })
        root.addView(buildBanner())

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        categories.forEach { category ->
            container.addView(TextView(this).apply {
                text = category.title
                textSize = 15f
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(8))
            })
            category.plugins.forEach { container.addView(buildPluginView(it)) }
        }

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
            text = "Это каталог-заготовка. Плагины пока НЕ выполняются: установка, включение " +
                "и настройка станут доступны позже."
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(6), 0, 0)
        })
        return banner
    }

    private fun buildPluginView(plugin: Plugin): View {
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
            text = plugin.name
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = plugin.summary
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(4), 0, 0)
        })

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        actionsRow.addView(TextView(this).apply {
            text = "Скоро"
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0A6"))
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        actionsRow.addView(disabledButton("Установить"))
        actionsRow.addView(disabledButton("Настроить"))
        card.addView(actionsRow)
        return card
    }

    private fun disabledButton(label: String): Button = Button(this).apply {
        text = label
        isEnabled = false
        alpha = 0.45f
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            leftMargin = dp(8)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
