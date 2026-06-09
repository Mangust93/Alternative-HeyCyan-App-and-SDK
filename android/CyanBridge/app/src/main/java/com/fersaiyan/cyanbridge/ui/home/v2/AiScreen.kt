package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.home.QuickNoteActivity
import com.fersaiyan.cyanbridge.ui.home.TranscriptionPlaceholderActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * AI — hub for the on-glasses/phone AI features (Module F).
 *
 * Presents the four AI features as large hero cards. Existing entry points are reused exactly:
 *  - Переводчик and Фото и вопрос open their optional feature modules through package-scoped
 *    Intent actions (translator / photo-question logic is left untouched).
 *  - Транскрибация and Быстрая заметка open the existing lightweight in-app screens.
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
        column.addView(ctx.v2Subtitle("Перевод, вопросы по фото, расшифровка и заметки"))

        column.addView(ctx.v2Card(
            title = "Переводчик",
            description = "Перевод речи с озвучиванием в очки",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.CONVERSATION_TRANSLATION) },
        ))
        column.addView(ctx.v2Card(
            title = "Фото и вопрос",
            description = "Выберите фото и задайте вопрос AI",
            large = true,
            onClick = { V2Nav.openFeature(activity, FeatureIntents.PHOTO_QUESTION) },
        ))
        column.addView(ctx.v2Card(
            title = "Транскрибация",
            description = "Аудио → текст, видео → аудио, видео → текст",
            large = true,
            onClick = { V2Nav.openLocal(activity, TranscriptionPlaceholderActivity::class.java) },
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
