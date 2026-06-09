package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Автоматизация — future automation & integration hub (Module F).
 *
 * This replaces the old plugin marketplace concept: there are no downloads, votes or trending
 * here. It presents automation/integration *categories* as placeholder cards grouped by area.
 * Nothing executes — tapping a card shows a "скоро" toast. No SDK, networking, Telegram/Notion
 * API or real plugin execution is wired in this module.
 */
class AutomationScreen(private val activity: AppCompatActivity) {

    private data class Integration(val title: String, val summary: String)
    private data class Group(val title: String, val items: List<Integration>)

    private val groups = listOf(
        Group(
            "Сеть и API",
            listOf(
                Integration("Webhook", "Отправка события на webhook"),
                Integration("HTTP", "Произвольный HTTP-запрос"),
                Integration("Android Intents", "Запуск системного Android Intent"),
            ),
        ),
        Group(
            "Сервисы",
            listOf(
                Integration("Telegram", "Отправка сообщений в Telegram"),
                Integration("Notion", "Создание заметок и страниц в Notion"),
            ),
        ),
        Group(
            "Время и напоминания",
            listOf(
                Integration("Calendar", "Создание событий в календаре"),
                Integration("Reminders", "Создание напоминаний"),
                Integration("Alarm", "Установка будильника"),
            ),
        ),
        Group(
            "Система и обмен",
            listOf(
                Integration("Clipboard", "Копирование текста в буфер обмена"),
                Integration("Share", "Поделиться текстом или фото"),
                Integration("Open App", "Открыть приложение"),
                Integration("Open Settings", "Открыть системные настройки"),
            ),
        ),
    )

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Автоматизация"))
        column.addView(ctx.v2Subtitle("Интеграции и сценарии — заготовка будущего хаба"))

        groups.forEachIndexed { index, group ->
            column.addView(ctx.v2SectionTitle(group.title, topDp = if (index == 0) 8 else 16))
            group.items.forEach { item ->
                column.addView(ctx.v2Card(
                    title = item.title,
                    description = item.summary,
                    pill = ctx.v2StatusPill("Скоро", active = false),
                    onClick = { soon(item.title) },
                ))
            }
        }

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }

    private fun soon(name: String) {
        Toast.makeText(activity, "$name — скоро", Toast.LENGTH_SHORT).show()
    }
}
