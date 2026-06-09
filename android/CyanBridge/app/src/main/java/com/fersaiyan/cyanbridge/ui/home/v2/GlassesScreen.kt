package com.fersaiyan.cyanbridge.ui.home.v2

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Очки — device home / glasses control center (Module F).
 *
 * Shows a premium device status card with a connected / not-connected state and a single,
 * prominent entry point into the existing, working device sync/search flow. The connection
 * state is a *read-only* glance at the BLE manager (the same value the legacy screen reads in
 * onStart); this screen never drives BLE, P2P or sync internals — the sync card just opens the
 * unchanged [com.fersaiyan.cyanbridge.MainActivity].
 */
class GlassesScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Очки"))
        column.addView(ctx.v2Subtitle("Статус устройства и управление очками"))

        column.addView(buildStatusCard())

        column.addView(ctx.v2Card(
            title = "Синхронизация и поиск",
            description = "Поиск, подключение и синхронизация очков",
            pill = ctx.v2StatusPill("Открыть", active = true),
            onClick = { V2Nav.openDeviceSync(activity) },
        ))

        column.addView(ctx.v2SectionTitle("Управление"))
        column.addView(ctx.v2Card(
            title = "Камера, видео и запись",
            description = "Управление съёмкой и записью на очках",
            pill = ctx.v2StatusPill("В синхронизации", active = false),
            onClick = { V2Nav.openDeviceSync(activity) },
        ))
        column.addView(ctx.v2Card(
            title = "Состояние и батарея",
            description = "Версия прошивки, заряд и громкость",
            pill = ctx.v2StatusPill("В синхронизации", active = false),
            onClick = { V2Nav.openDeviceSync(activity) },
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }

    private fun buildStatusCard(): View {
        val ctx = activity
        val connected = isGlassesConnected()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.v2dp(22), ctx.v2dp(22), ctx.v2dp(22), ctx.v2dp(22))
            background = GradientDrawable().apply {
                cornerRadius = ctx.v2dp(18).toFloat()
                setColor(V2Theme.CARD)
                setStroke(ctx.v2dp(1), if (connected) V2Theme.ACCENT else V2Theme.CARD_STROKE)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = ctx.v2dp(14)
            }
        }

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerRow.addView(TextView(ctx).apply {
            text = "HeyCyan Glasses"
            textSize = 20f
            setTextColor(V2Theme.TEXT)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        })
        headerRow.addView(ctx.v2StatusPill(
            if (connected) "Подключено" else "Не подключено",
            active = connected,
        ))
        card.addView(headerRow)

        card.addView(TextView(ctx).apply {
            text = if (connected) {
                "Очки подключены и готовы к работе."
            } else {
                "Очки не подключены. Откройте синхронизацию, чтобы найти и подключить устройство."
            }
            textSize = 13f
            setTextColor(V2Theme.TEXT_SECONDARY)
            setPadding(0, ctx.v2dp(10), 0, 0)
        })
        return card
    }

    /**
     * Read-only connection glance. Wrapped in runCatching so a missing/uninitialised BLE stack
     * can never crash the home screen — it just renders the "not connected" state.
     */
    private fun isGlassesConnected(): Boolean = runCatching {
        com.oudmon.ble.base.bluetooth.BleOperateManager.getInstance().isConnected
    }.getOrDefault(false)
}
