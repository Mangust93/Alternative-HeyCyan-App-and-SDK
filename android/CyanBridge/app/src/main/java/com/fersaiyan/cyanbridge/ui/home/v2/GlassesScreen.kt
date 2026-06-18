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
import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity
import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaGalleryActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * Главная — the CyanBridge V2 dashboard (Module F).
 *
 * The app's home surface: a premium device hero card, quick actions into the main product
 * features, and a device/sync section. The connection state is a *read-only* glance at the BLE
 * manager (the same value the legacy screen reads in onStart); this screen never drives BLE,
 * P2P or sync internals — every action just opens existing, working screens unchanged.
 */
class GlassesScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Главная"))
        column.addView(ctx.v2Subtitle("Очки, быстрые действия и состояние системы"))

        column.addView(buildHeroCard())

        column.addView(ctx.v2SectionTitle("Быстрые действия"))
        column.addView(ctx.v2Card(
            title = "Перевод",
            description = "Перевести речь и озвучить результат",
            onClick = { V2Nav.openFeature(activity, FeatureIntents.CONVERSATION_TRANSLATION) },
        ))
        column.addView(ctx.v2Card(
            title = "Фото и вопрос",
            description = "Сделать или выбрать фото и спросить AI",
            onClick = { V2Nav.openFeature(activity, FeatureIntents.PHOTO_QUESTION) },
        ))
        column.addView(ctx.v2Card(
            title = "Галерея",
            description = "Открыть фото, видео, аудио и записи",
            onClick = { V2Nav.openLocal(activity, SyncedMediaGalleryActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "Записи",
            description = "Открыть аудио, записи и транскрибацию",
            onClick = { V2Nav.openLocal(activity, RecordingsListActivity::class.java) },
        ))

        column.addView(ctx.v2SectionTitle("Устройство"))
        column.addView(ctx.v2Card(
            title = "Подключение и синхронизация",
            description = "Поиск, подключение и синхронизация очков",
            pill = ctx.v2StatusPill("Открыть", active = true),
            onClick = { V2Nav.openDeviceSync(activity) },
        ))
        column.addView(ctx.v2Card(
            title = "Состояние очков",
            description = "Батарея, версия, память и параметры устройства",
            pill = ctx.v2StatusPill("В устройстве", active = false),
            onClick = { V2Nav.openDeviceSync(activity) },
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }

    /**
     * Premium device hero card: name, read-only connection state and a single primary action
     * into the existing, working device sync flow.
     */
    private fun buildHeroCard(): View {
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
                "Очки не подключены. Откройте подключение, чтобы найти и подключить устройство."
            }
            textSize = 13f
            setTextColor(V2Theme.TEXT_SECONDARY)
            setPadding(0, ctx.v2dp(10), 0, 0)
        })

        card.addView(buildPrimaryAction("Открыть подключение") { V2Nav.openDeviceSync(activity) })
        return card
    }

    /** Accent-filled primary button used as the hero card's main call to action. */
    private fun buildPrimaryAction(text: CharSequence, onClick: () -> Unit): View {
        val ctx = activity
        return TextView(ctx).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(V2Theme.BG)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(ctx.v2dp(18), ctx.v2dp(12), ctx.v2dp(18), ctx.v2dp(12))
            background = GradientDrawable().apply {
                cornerRadius = ctx.v2dp(14).toFloat()
                setColor(V2Theme.ACCENT)
            }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = ctx.v2dp(16)
            }
            isClickable = true
            isFocusable = true
            addPressFeedback()
            setOnClickListener { runCatching { onClick() } }
        }
    }

    /**
     * Read-only connection glance. Wrapped in runCatching so a missing/uninitialised BLE stack
     * can never crash the home screen — it just renders the "not connected" state.
     */
    private fun isGlassesConnected(): Boolean = runCatching {
        com.oudmon.ble.base.bluetooth.BleOperateManager.getInstance().isConnected
    }.getOrDefault(false)
}
