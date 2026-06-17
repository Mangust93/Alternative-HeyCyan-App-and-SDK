package com.fersaiyan.cyanbridge.ui.home.v2

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.recordings.RecordingsListActivity
import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaGalleryActivity
import com.fersaiyan.cyanbridge.ui.tools.FeatureIntents

/**
 * Галерея — landing for the future unified Media Hub (Module F).
 *
 * Shows the media categories (Фото / Видео / Аудио / Записи / AI / Заметки) as filter chips
 * and routes the working media categories to the existing synced media and recordings screens.
 * Transcription remains a full workflow opened from recordings/audio instead of a bottom tab.
 */
class GalleryScreen(private val activity: AppCompatActivity) {

    private data class Category(val label: String, val onTap: () -> Unit)

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val categories = listOf(
            Category("Фото") { V2Nav.openLocal(activity, SyncedMediaGalleryActivity::class.java) },
            Category("Видео") { V2Nav.openLocal(activity, SyncedMediaGalleryActivity::class.java) },
            Category("Аудио") { V2Nav.openLocal(activity, RecordingsListActivity::class.java) },
            Category("Записи") { V2Nav.openLocal(activity, RecordingsListActivity::class.java) },
            Category("AI") {
                V2Nav.openFeature(
                    activity,
                    FeatureIntents.AI_USER_SHELL,
                    unavailableMessage = "Раздел истории пока недоступен",
                )
            },
            Category("Заметки") { soon("Заметки") },
        )

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Галерея"))
        column.addView(ctx.v2Subtitle("Единый центр медиа с очков и из AI"))

        column.addView(buildChipRow(categories))

        column.addView(ctx.v2EmptyState(
            title = "Пока пусто",
            message = "Пока нет материалов. Сделайте фото на очках или выполните синхронизацию.",
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }

    private fun buildChipRow(categories: List<Category>): View {
        val ctx = activity
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ctx.v2dp(4), 0, ctx.v2dp(8))
        }
        categories.forEachIndexed { index, category ->
            row.addView(buildChip(category, first = index == 0))
        }
        return HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(row)
        }
    }

    private fun buildChip(category: Category, first: Boolean): View {
        val ctx = activity
        return TextView(ctx).apply {
            text = category.label
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(V2Theme.TEXT)
            gravity = Gravity.CENTER
            setPadding(ctx.v2dp(18), ctx.v2dp(10), ctx.v2dp(18), ctx.v2dp(10))
            background = GradientDrawable().apply {
                cornerRadius = ctx.v2dp(22).toFloat()
                setColor(V2Theme.CARD)
                setStroke(ctx.v2dp(1), V2Theme.CARD_STROKE)
            }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                leftMargin = if (first) 0 else ctx.v2dp(8)
            }
            isClickable = true
            isFocusable = true
            addPressFeedback()
            setOnClickListener { category.onTap() }
        }
    }

    private fun soon(name: String) {
        Toast.makeText(activity, "$name — скоро", Toast.LENGTH_SHORT).show()
    }
}
