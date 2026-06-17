package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.home.QuickNoteActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * AI — hub for the on-glasses/phone AI features (Module F).
 *
 * Presents AI-first features as large hero cards. Translation and transcription are no longer
 * mixed into this hub: translation belongs to its own product surface, while transcription is
 * opened from Gallery / Audio / Recordings workflows.
 */
class AiScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("AI"))
        column.addView(ctx.v2Subtitle("Чат, вопросы по фото, история AI и заметки"))

        column.addView(ctx.v2Card(
            title = "Чат с AI",
            description = "Открыть основной AI-чат и Hermes/Life Agent сценарии",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.AI_USER_SHELL) },
        ))
        column.addView(ctx.v2Card(
            title = "Фото и вопрос",
            description = "Выберите фото и задайте вопрос AI",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.PHOTO_QUESTION) },
        ))
        column.addView(ctx.v2Card(
            title = "История AI",
            description = "Последние вопросы, ответы и фото-запросы",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.AI_USER_SHELL) },
        ))
        column.addView(ctx.v2Card(
            title = "Быстрая заметка",
            description = "Быстро сохранить текстовую заметку на телефоне",
            large = true,
            onClick = { V2Nav.openLocal(activity, QuickNoteActivity::class.java) },
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }
}
