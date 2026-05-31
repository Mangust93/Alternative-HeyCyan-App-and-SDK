package com.fersaiyan.cyanbridge.ai_user_shell

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * AI Settings screen (V1.4) — part of the user-facing :ai-user-shell module.
 *
 * Lets the user store an OpenRouter API key and pick a model from a fixed local list, both
 * persisted via [AiSettingsStore] (plain SharedPreferences, this module's own file). It is
 * intentionally self-contained: it performs no networking, makes no test call to OpenRouter,
 * and does not touch the photo-question request path. Saved values simply wait here for a
 * later step to consume.
 *
 * Opened only through the package-scoped [FeatureActions.AI_SETTINGS] action and declared
 * android:exported="false", so external apps cannot launch it.
 */
class AiSettingsActivity : AppCompatActivity() {

    private lateinit var store: AiSettingsStore

    private lateinit var apiKeyInput: EditText
    private lateinit var modelSpinner: Spinner
    private lateinit var statusText: TextView

    private val density: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Настройки AI"
        store = AiSettingsStore(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#101418"))
        }

        root.addView(TextView(this).apply {
            text = "Настройки AI"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        })

        // --- OpenRouter API key ---
        root.addView(label("OpenRouter API key"))
        apiKeyInput = EditText(this).apply {
            hint = "sk-or-v1-..."
            setHintTextColor(Color.parseColor("#6B7178"))
            setTextColor(Color.WHITE)
            // No suggestions/autofill for a secret; the value is not pre-filled either.
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        root.addView(apiKeyInput)

        statusText = TextView(this).apply {
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(statusText)

        // --- Model ---
        root.addView(label("Model"))
        modelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AiSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AiSettingsStore.MODELS,
            )
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }
        root.addView(modelSpinner)

        // --- Buttons ---
        root.addView(Button(this).apply {
            text = "Сохранить"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(16)
            }
            setOnClickListener { onSave() }
        })
        root.addView(Button(this).apply {
            text = "Очистить ключ"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener { onClearKey() }
        })
        root.addView(Button(this).apply {
            text = "Назад"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(8)
            }
            setOnClickListener { finish() }
        })

        // --- Hint ---
        root.addView(TextView(this).apply {
            text = "Ключ хранится локально на устройстве. " +
                "Не передавайте APK с сохранённым ключом другим людям."
            textSize = 12f
            setTextColor(Color.parseColor("#9AA0A6"))
            setPadding(0, dp(16), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)

        restoreModelSelection()
        renderStatus()
    }

    private fun onSave() {
        val key = apiKeyInput.text?.toString().orEmpty()
        val model = AiSettingsStore.MODELS.getOrElse(modelSpinner.selectedItemPosition) {
            AiSettingsStore.MODELS.first()
        }
        store.save(key, model)
        // Don't keep the typed secret in the editable field after saving.
        apiKeyInput.text?.clear()
        renderStatus()
        Toast.makeText(this, "Настройки AI сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun onClearKey() {
        store.clearApiKey()
        apiKeyInput.text?.clear()
        renderStatus()
        Toast.makeText(this, "Ключ OpenRouter удалён", Toast.LENGTH_SHORT).show()
    }

    private fun restoreModelSelection() {
        val index = AiSettingsStore.MODELS.indexOf(store.modelId)
        if (index >= 0) modelSpinner.setSelection(index)
    }

    private fun renderStatus() {
        if (store.hasApiKey) {
            statusText.text = "Ключ сохранён: ${store.maskedApiKey()}"
            statusText.setTextColor(Color.parseColor("#21D0C3"))
        } else {
            statusText.text = "Ключ не задан"
            statusText.setTextColor(Color.parseColor("#9AA0A6"))
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.parseColor("#C7CCD1"))
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * density).toInt()
}
