package com.fersaiyan.cyanbridge.ui.home.v2

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

/**
 * CyanBridge V2 design system (Module F).
 *
 * A tiny, dependency-free set of dark-premium building blocks shared by the five product
 * screens (Очки / AI / Галерея / Автоматизация / Профиль). Everything is built from plain
 * Android views and parsed colors so there is no layout/resource wiring and no heavy
 * animation library — only the framework's own `animate()` is used for press feedback.
 *
 * Inspiration: Ray-Ban Meta / Nothing OS / Linear — large rounded cards, generous spacing,
 * muted neutrals with a single cyan accent.
 */
object V2Theme {
    val BG = Color.parseColor("#101418")
    val SURFACE = Color.parseColor("#14181D")
    val CARD = Color.parseColor("#1B2026")
    val CARD_STROKE = Color.parseColor("#262C33")
    val TEXT = Color.parseColor("#FFFFFF")
    val TEXT_SECONDARY = Color.parseColor("#9AA0A6")
    val TEXT_MUTED = Color.parseColor("#6B7178")
    val ACCENT = Color.parseColor("#21D0C3")
    val ACCENT_DIM = Color.parseColor("#13312F")
    val SKELETON = Color.parseColor("#232931")
}

/** Density-aware dp → px helper. */
fun Context.v2dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * Subtle scale-down press feedback for tappable cards. Returns `false` from the touch
 * handler so the host view still dispatches its own click — the animation is purely visual.
 */
fun View.addPressFeedback() {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
        }
        false
    }
}

/** Bold screen/section heading. */
fun Context.v2SectionTitle(text: CharSequence, topDp: Int = 16): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(V2Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, v2dp(topDp), 0, v2dp(8))
    }

/** Large screen title used at the top of each tab. */
fun Context.v2ScreenTitle(text: CharSequence): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 26f
        setTextColor(V2Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
    }

/** Muted one-line subtitle under a title. */
fun Context.v2Subtitle(text: CharSequence): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(V2Theme.TEXT_SECONDARY)
        setPadding(0, v2dp(4), 0, v2dp(12))
    }

/** Small rounded status pill (e.g. "Подключено" / "Скоро"). */
fun Context.v2StatusPill(text: CharSequence, active: Boolean): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (active) V2Theme.ACCENT else V2Theme.TEXT_MUTED)
        setPadding(v2dp(10), v2dp(5), v2dp(10), v2dp(5))
        background = GradientDrawable().apply {
            cornerRadius = v2dp(20).toFloat()
            setColor(if (active) V2Theme.ACCENT_DIM else V2Theme.SURFACE)
            setStroke(v2dp(1), if (active) V2Theme.ACCENT else V2Theme.CARD_STROKE)
        }
    }

/**
 * A premium card with a title, optional description, an optional trailing [pill], and an
 * optional tap action (which also enables the press animation). When [large] is set the card
 * uses bigger padding/typography for the AI hub's hero cards.
 */
fun Context.v2Card(
    title: CharSequence,
    description: CharSequence? = null,
    pill: TextView? = null,
    large: Boolean = false,
    onClick: (() -> Unit)? = null,
): View {
    val pad = if (large) v2dp(22) else v2dp(18)
    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = v2dp(16).toFloat()
            setColor(V2Theme.CARD)
            setStroke(v2dp(1), V2Theme.CARD_STROKE)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = v2dp(12)
        }
    }

    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }
    headerRow.addView(TextView(this).apply {
        text = title
        textSize = if (large) 19f else 16f
        setTextColor(V2Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    })
    if (pill != null) headerRow.addView(pill)
    card.addView(headerRow)

    if (!description.isNullOrBlank()) {
        card.addView(TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(V2Theme.TEXT_SECONDARY)
            setPadding(0, v2dp(6), 0, 0)
        })
    }

    if (onClick != null) {
        card.isClickable = true
        card.isFocusable = true
        card.addPressFeedback()
        card.setOnClickListener { runCatching { onClick() } }
    }
    return card
}

/** A neutral, content-less placeholder card used to suggest "loading / coming soon" surfaces. */
fun Context.v2SkeletonCard(heightDp: Int = 64): View =
    View(this).apply {
        background = GradientDrawable().apply {
            cornerRadius = v2dp(16).toFloat()
            setColor(V2Theme.SKELETON)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, v2dp(heightDp)).apply {
            bottomMargin = v2dp(12)
        }
        alpha = 0.5f
    }

/** Centered empty-state block with a title and a meaningful message. */
fun Context.v2EmptyState(title: CharSequence, message: CharSequence): View {
    val block = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(v2dp(24), v2dp(36), v2dp(24), v2dp(36))
        background = GradientDrawable().apply {
            cornerRadius = v2dp(16).toFloat()
            setColor(V2Theme.CARD)
            setStroke(v2dp(1), V2Theme.CARD_STROKE)
        }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = v2dp(8)
        }
    }
    block.addView(TextView(this).apply {
        this.text = title
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(V2Theme.TEXT)
        setTypeface(typeface, Typeface.BOLD)
    })
    block.addView(TextView(this).apply {
        this.text = message
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(V2Theme.TEXT_SECONDARY)
        setPadding(0, v2dp(8), 0, 0)
    })
    return block
}
