package com.fersaiyan.cyanbridge.ui.home.v2

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity
import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaGalleryActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * Галерея — the unified Media Hub V1 (Module F).
 *
 * Presents the media categories as large V2 cards instead of filter chips and routes the
 * working categories to the existing synced media, recordings and AI screens. Transcription is
 * not a separate tab/Activity — it stays a workflow opened from the recordings/audio section,
 * surfaced here only as an informational card.
 */
class GalleryScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Галерея"))
        column.addView(ctx.v2Subtitle("Фото, видео, аудио и материалы с очков"))

        column.addView(ctx.v2Card(
            title = "Фото и видео",
            description = "Просмотр синхронизированных фото и видео",
            large = true,
            onClick = { V2Nav.openLocal(activity, SyncedMediaGalleryActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "Аудио и записи",
            description = "Записи, диктофон, транскрибация",
            large = true,
            onClick = { V2Nav.openLocal(activity, RecordingsListActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "AI история",
            description = "Материалы, созданные AI и история запросов",
            large = true,
            onClick = {
                V2Nav.openFeature(
                    activity,
                    FeatureIntents.AI_USER_SHELL,
                    unavailableMessage = "Раздел истории пока недоступен",
                )
            },
        ))
        column.addView(ctx.v2Card(
            title = "Заметки",
            description = "Локальные заметки пользователя",
            pill = ctx.v2StatusPill("Скоро", active = false),
            large = true,
        ))

        column.addView(ctx.v2Card(
            title = "Транскрибация",
            description = "Аудио и видео можно расшифровывать через раздел записей",
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }
}
