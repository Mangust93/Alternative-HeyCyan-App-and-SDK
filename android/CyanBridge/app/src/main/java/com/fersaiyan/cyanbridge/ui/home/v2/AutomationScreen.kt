package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.automation.runtime.AutomationActionEditorActivity
import com.fersaiyan.cyanbridge.automation.runtime.AutomationActionStore
import com.fersaiyan.cyanbridge.automation.runtime.AutomationActionType

/**
 * Авто — the local Automation Hub (Module G).
 *
 * Replaces the old "скоро" integration catalog: the «Быстрые действия» section now opens real,
 * locally-executable action editors (HTTP / Webhook / Intent / Open App / Open Settings / Share),
 * and «Мои действия» lists actions the user saved locally (via [AutomationActionStore]) so they
 * can be run again. «Будущие интеграции» keeps the not-yet-built services as honest placeholders,
 * and «Сценарии» is a lightweight placeholder until actions can be wired to glasses events.
 *
 * This screen touches no SDK/BLE/P2P/device-sync internals — it only opens the runtime editor
 * Activity and reads the local action store.
 */
class AutomationScreen(private val activity: AppCompatActivity) {

    private data class QuickAction(val type: AutomationActionType, val title: String, val summary: String)

    private val quickActions = listOf(
        QuickAction(AutomationActionType.HTTP_REQUEST, "HTTP", "Произвольный HTTP-запрос"),
        QuickAction(AutomationActionType.WEBHOOK_POST, "Webhook", "POST JSON на webhook"),
        QuickAction(AutomationActionType.ANDROID_INTENT, "Android Intent", "Запуск системного Intent"),
        QuickAction(AutomationActionType.OPEN_APP, "Open App", "Открыть приложение по пакету"),
        QuickAction(AutomationActionType.OPEN_SETTINGS, "Open Settings", "Открыть системные настройки"),
        QuickAction(AutomationActionType.SHARE_TEXT, "Share Text", "Поделиться текстом"),
    )

    private val futureIntegrations = listOf(
        "Telegram" to "Отправка сообщений в Telegram",
        "Notion" to "Создание заметок и страниц",
        "Calendar" to "Создание событий в календаре",
        "Reminders" to "Создание напоминаний",
        "Alarm" to "Установка будильника",
        "Clipboard" to "Копирование в буфер обмена",
        "File Export" to "Экспорт данных в файл",
    )

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Автоматизация"))
        column.addView(ctx.v2Subtitle("Локальный хаб действий — работает без сервера"))

        // Working quick actions.
        column.addView(ctx.v2SectionTitle("Быстрые действия", topDp = 8))
        quickActions.forEach { qa ->
            column.addView(ctx.v2Card(
                title = qa.title,
                description = qa.summary,
                pill = ctx.v2StatusPill("Действие", active = true),
                onClick = { AutomationActionEditorActivity.open(activity, qa.type) },
            ))
        }

        // Saved actions.
        column.addView(ctx.v2SectionTitle("Мои действия"))
        val saved = runCatching { AutomationActionStore(ctx).loadAll() }.getOrDefault(emptyList())
        if (saved.isEmpty()) {
            column.addView(ctx.v2EmptyState(
                title = "Пока пусто",
                message = "Сохранённые действия появятся здесь. Откройте любое быстрое действие и нажмите «Сохранить».",
            ))
        } else {
            saved.forEach { action ->
                column.addView(ctx.v2Card(
                    title = action.name,
                    description = "${action.type.displayName} · ${action.summary()}",
                    pill = ctx.v2StatusPill("Запустить", active = true),
                    onClick = { AutomationActionEditorActivity.open(activity, action.type, action.id) },
                ))
            }
        }

        // Future integrations (honest placeholders).
        column.addView(ctx.v2SectionTitle("Будущие интеграции"))
        futureIntegrations.forEach { (title, summary) ->
            column.addView(ctx.v2Card(
                title = title,
                description = summary,
                pill = ctx.v2StatusPill("Скоро", active = false),
                onClick = null,
            ))
        }

        // Scenarios placeholder.
        column.addView(ctx.v2SectionTitle("Сценарии"))
        column.addView(ctx.v2EmptyState(
            title = "Сценарии",
            message = "Сценарии появятся после подключения действий к событиям очков.",
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }
}
