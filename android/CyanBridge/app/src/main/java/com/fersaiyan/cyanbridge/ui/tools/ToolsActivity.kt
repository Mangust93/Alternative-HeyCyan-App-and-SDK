package com.fersaiyan.cyanbridge.ui.tools

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.databinding.ActivityToolsBinding
import com.fersaiyan.cyanbridge.databinding.ItemToolCardBinding

/**
 * "Tools / Diagnostics" shell.
 *
 * Loosely-coupled launcher for the optional feature modules
 * (:conversation-translation, :headset-button-tools, :glasses-button-event-tools,
 * :debug-log-tools, :runtime-diagnostics-tools, :phone-test-tools).
 *
 * Coupling rules this screen follows:
 *  - it never references any module Activity class (no compile-time dependency);
 *  - each module is opened with an implicit Intent(action) scoped to this package;
 *  - every card resolves its action first, so a module that is toggled out of the
 *    build (or absent from a release) simply shows "Module not included" and the
 *    core app keeps working;
 *  - launch failures are caught and surfaced as a Toast instead of crashing.
 */
class ToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityToolsBinding

    private data class FeatureEntry(
        @StringRes val titleRes: Int,
        @StringRes val descRes: Int,
        val action: String,
    )

    private val features = listOf(
        FeatureEntry(
            R.string.feature_conversation_translation_title,
            R.string.feature_conversation_translation_desc,
            FeatureIntents.CONVERSATION_TRANSLATION,
        ),
        FeatureEntry(
            R.string.feature_headset_button_title,
            R.string.feature_headset_button_desc,
            FeatureIntents.HEADSET_BUTTON_DIAGNOSTIC,
        ),
        FeatureEntry(
            R.string.feature_glasses_button_title,
            R.string.feature_glasses_button_desc,
            FeatureIntents.GLASSES_BUTTON_EVENT_DIAGNOSTIC,
        ),
        FeatureEntry(
            R.string.feature_debug_logs_title,
            R.string.feature_debug_logs_desc,
            FeatureIntents.DEBUG_LOGS,
        ),
        FeatureEntry(
            R.string.feature_runtime_diagnostics_title,
            R.string.feature_runtime_diagnostics_desc,
            FeatureIntents.RUNTIME_DIAGNOSTICS,
        ),
        FeatureEntry(
            R.string.feature_phone_test_title,
            R.string.feature_phone_test_desc,
            FeatureIntents.PHONE_TEST_TOOLS,
        ),
        FeatureEntry(
            R.string.feature_photo_question_title,
            R.string.feature_photo_question_desc,
            FeatureIntents.PHOTO_QUESTION,
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        renderCards()
    }

    override fun onResume() {
        super.onResume()
        // A module's availability can only change between installs, but re-render on
        // resume so the screen always reflects the current resolvable set.
        renderCards()
    }

    private fun renderCards() {
        val container = binding.containerTools
        container.removeAllViews()

        features.forEach { feature ->
            val card = ItemToolCardBinding.inflate(layoutInflater, container, false)
            card.tvToolTitle.text = getString(feature.titleRes)
            card.tvToolDesc.text = getString(feature.descRes)

            val available = isFeatureAvailable(feature.action)
            if (available) {
                card.tvToolStatus.text = getString(R.string.tools_status_available)
                card.tvToolStatus.setTextColor(ContextCompat.getColor(this, R.color.cyan_accent))
                card.btnToolOpen.isEnabled = true
                card.btnToolOpen.alpha = 1f
                card.btnToolOpen.setOnClickListener { openFeature(feature.action) }
            } else {
                card.tvToolStatus.text = getString(R.string.tools_status_unavailable)
                card.tvToolStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                card.btnToolOpen.isEnabled = false
                card.btnToolOpen.alpha = 0.45f
                card.btnToolOpen.setOnClickListener(null)
            }

            container.addView(card.root)
        }
    }

    private fun featureIntent(action: String): Intent =
        Intent(action).setPackage(packageName)

    private fun isFeatureAvailable(action: String): Boolean =
        packageManager.resolveActivity(featureIntent(action), 0) != null

    private fun openFeature(action: String) {
        val intent = featureIntent(action)
        if (packageManager.resolveActivity(intent, 0) == null) {
            Toast.makeText(this, R.string.tools_status_unavailable, Toast.LENGTH_SHORT).show()
            renderCards()
            return
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.tools_launch_failed, Toast.LENGTH_SHORT).show()
            }
    }
}
