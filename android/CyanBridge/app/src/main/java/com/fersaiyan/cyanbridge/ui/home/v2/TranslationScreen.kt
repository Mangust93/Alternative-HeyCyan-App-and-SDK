package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * Перевод — dedicated translation surface.
 *
 * Translation is a primary glasses scenario, so it lives outside the AI hub.
 * The current entry point reuses the existing conversation translation module.
 */
class TranslationScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Перевод"))
        column.addView(ctx.v2Subtitle("Речь, диалог и озвучивание через очки"))

        column.addView(ctx.v2Card(
            title = "Переводчик",
            description = "Перевод речи с озвучиванием в очки",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.CONVERSATION_TRANSLATION) },
        ))

        column.addView(ctx.v2Card(
            title = "Перевод диалога",
            description = "Режим разговора с автоматическим переводом",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.CONVERSATION_TRANSLATION) },
        ))

        column.addView(ctx.v2Card(
            title = "История переводов",
            description = "Сохранённые переводы появятся здесь после отдельной интеграции истории",
            pill = ctx.v2StatusPill("Скоро", active = false),
            onClick = null,
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }
}
