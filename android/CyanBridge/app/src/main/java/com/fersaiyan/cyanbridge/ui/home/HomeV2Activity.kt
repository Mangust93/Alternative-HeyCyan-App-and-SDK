package com.fersaiyan.cyanbridge.ui.home

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ui.home.v2.AiScreen
import com.fersaiyan.cyanbridge.ui.home.v2.AutomationScreen
import com.fersaiyan.cyanbridge.ui.home.v2.GalleryScreen
import com.fersaiyan.cyanbridge.ui.home.v2.GlassesScreen
import com.fersaiyan.cyanbridge.ui.home.v2.TranslationScreen
import com.fersaiyan.cyanbridge.ui.home.v2.V2Theme
import com.fersaiyan.cyanbridge.ui.home.v2.addPressFeedback
import com.fersaiyan.cyanbridge.ui.home.v2.v2dp

/**
 * CyanBridge V2 product shell (Module F).
 *
 * This is the app's main, user-facing home. It hosts the five-tab product navigation
 * (Главная / Перевод / AI / Галерея / Автоматизация) with a custom dark-premium bottom bar and a
 * content area that fades between screens. Each tab's content is built lazily on first visit by
 * a dedicated screen class in [com.fersaiyan.cyanbridge.ui.home.v2].
 *
 * Constraints honoured:
 *  - Native Android views only, no fragments and no heavy animation library — just the
 *    framework's own `animate()` for the fade-in and card press feedback.
 *  - Device sync/search is untouched: the Очки tab opens the existing MainActivity flow.
 *  - The legacy "Чаты / Записи / Community Plugins" surfaces no longer dominate: they are not
 *    part of this product navigation (they remain reachable only inside the device screen).
 */
class HomeV2Activity : AppCompatActivity() {

    private enum class Tab(val label: String) {
        GLASSES("Главная"),
        TRANSLATION("Перевод"),
        AI("AI"),
        GALLERY("Галерея"),
        AUTOMATION("Авто"),
    }

    private lateinit var contentContainer: FrameLayout
    private val tabButtons = mutableMapOf<Tab, LinearLayout>()
    private val builtScreens = mutableMapOf<Tab, View>()
    private var currentTab: Tab? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "CyanBridge"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(V2Theme.BG)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        root.addView(contentContainer)
        root.addView(buildBottomNav())

        setContentView(root)

        selectTab(Tab.GLASSES)
    }

    private fun buildBottomNav(): View {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(V2Theme.SURFACE)
            setPadding(v2dp(6), v2dp(6), v2dp(6), v2dp(6))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        // Thin top divider to separate the bar from content.
        nav.background = GradientDrawable().apply {
            setColor(V2Theme.SURFACE)
            setStroke(v2dp(1), V2Theme.CARD_STROKE)
        }

        Tab.values().forEach { tab ->
            val button = buildTabButton(tab)
            tabButtons[tab] = button
            nav.addView(button)
        }
        return nav
    }

    private fun buildTabButton(tab: Tab): LinearLayout {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(v2dp(4), v2dp(10), v2dp(4), v2dp(10))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            isClickable = true
            isFocusable = true
            addPressFeedback()
            setOnClickListener { selectTab(tab) }
        }
        // Accent indicator bar above the label, shown only for the active tab.
        button.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(v2dp(22), v2dp(3)).apply {
                bottomMargin = v2dp(6)
            }
            background = GradientDrawable().apply {
                cornerRadius = v2dp(2).toFloat()
                setColor(Color.TRANSPARENT)
            }
            tag = INDICATOR_TAG
        })
        button.addView(TextView(this).apply {
            text = tab.label
            textSize = 12f
            setTextColor(V2Theme.TEXT_SECONDARY)
            setTypeface(typeface, Typeface.BOLD)
            tag = LABEL_TAG
        })
        return button
    }

    private fun selectTab(tab: Tab) {
        if (tab == currentTab) return
        currentTab = tab

        // Update bottom-bar selection state.
        tabButtons.forEach { (t, button) ->
            val active = t == tab
            (button.findViewWithTag<View>(INDICATOR_TAG))?.background = GradientDrawable().apply {
                cornerRadius = v2dp(2).toFloat()
                setColor(if (active) V2Theme.ACCENT else Color.TRANSPARENT)
            }
            (button.findViewWithTag<TextView>(LABEL_TAG))?.setTextColor(
                if (active) V2Theme.ACCENT else V2Theme.TEXT_SECONDARY
            )
        }

        // Swap content, building each screen lazily on first visit, with a light fade-in.
        val screen = builtScreens.getOrPut(tab) { buildScreen(tab) }
        contentContainer.removeAllViews()
        contentContainer.addView(screen)
        screen.alpha = 0f
        screen.animate().alpha(1f).setDuration(180).start()
    }

    private fun buildScreen(tab: Tab): View = when (tab) {
        Tab.GLASSES -> GlassesScreen(this).build()
        Tab.TRANSLATION -> TranslationScreen(this).build()
        Tab.AI -> AiScreen(this).build()
        Tab.GALLERY -> GalleryScreen(this).build()
        Tab.AUTOMATION -> AutomationScreen(this).build()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild stateful tabs on return so live changes are reflected:
        //  - Очки: connection state changed in the device screen.
        //  - Авто: a new action may have been saved in the automation editor ("Мои действия").
        builtScreens.remove(Tab.GLASSES)
        builtScreens.remove(Tab.AUTOMATION)
        val tab = currentTab
        if (tab == Tab.GLASSES || tab == Tab.AUTOMATION) {
            val fresh = buildScreen(tab)
            builtScreens[tab] = fresh
            contentContainer.removeAllViews()
            contentContainer.addView(fresh)
        }
    }

    private companion object {
        const val INDICATOR_TAG = "v2_tab_indicator"
        const val LABEL_TAG = "v2_tab_label"
    }
}
