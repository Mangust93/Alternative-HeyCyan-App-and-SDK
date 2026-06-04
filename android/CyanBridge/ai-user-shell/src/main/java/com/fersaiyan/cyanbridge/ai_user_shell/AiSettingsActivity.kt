package com.fersaiyan.cyanbridge.ai_user_shell

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ai_config.AiModelOption
import com.fersaiyan.cyanbridge.ai_config.AiModels
import com.fersaiyan.cyanbridge.ai_config.AiSettingsStore

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
    private lateinit var maskText: TextView
    private lateinit var modelStatusText: TextView

    private val density: Float by lazy { resources.displayMetrics.density }

    private val tealColor = Color.parseColor("#21D0C3")
    private val mutedColor = Color.parseColor("#9AA0A6")

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
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(statusText)

        // The masked key (e.g. "sk-or-v1-••••1234"); never the full key. Hidden when no key.
        maskText = TextView(this).apply {
            textSize = 13f
            setTextColor(mutedColor)
            setPadding(0, dp(2), 0, 0)
        }
        root.addView(maskText)

        // --- Model ---
        root.addView(label("Модель"))
        modelSpinner = Spinner(this).apply {
            // Show human-readable titles; the canonical id is resolved from the position on
            // save (and surfaced in the "selected model" line below).
            adapter = ArrayAdapter(
                this@AiSettingsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                AiModels.OPTIONS.map { it.title },
            )
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    renderModelStatus(AiModels.OPTIONS.getOrNull(pos)?.id)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        root.addView(modelSpinner)

        // Shows the currently selected model (falls back to the default when none is stored).
        modelStatusText = TextView(this).apply {
            textSize = 13f
            setTextColor(mutedColor)
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(modelStatusText)

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
            text = "Ключ хранится локально на устройстве и не отображается после сохранения."
            textSize = 12f
            setTextColor(mutedColor)
            setPadding(0, dp(16), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = "Не передавайте APK с сохранённым ключом другим людям."
            textSize = 12f
            setTextColor(mutedColor)
            setPadding(0, dp(4), 0, 0)
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
        val model = AiModels.MODEL_IDS.getOrElse(modelSpinner.selectedItemPosition) {
            AiModels.DEFAULT_MODEL_ID
        }
        store.saveApiKey(key)
        store.saveSelectedModelId(model)
        // Don't keep the typed secret in the editable field after saving.
        apiKeyInput.text?.clear()
        renderStatus()
        renderModelStatus(store.getEffectiveModelId())
        Toast.makeText(this, "Настройки AI сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun onClearKey() {
        store.clearApiKey()
        apiKeyInput.text?.clear()
        renderStatus()
        Toast.makeText(this, "Ключ OpenRouter удалён", Toast.LENGTH_SHORT).show()
    }

    private fun restoreModelSelection() {
        val effective = store.getEffectiveModelId()
        val index = AiModels.MODEL_IDS.indexOf(effective)
        if (index >= 0) modelSpinner.setSelection(index)
        // Reflect the stored selection immediately, even before the spinner emits a callback.
        renderModelStatus(effective)
    }

    /** Refresh the key status line + mask. The full key is never rendered here. */
    private fun renderStatus() {
        val masked = store.getMaskedApiKey()
        if (store.hasApiKey() && masked != null) {
            statusText.text = "Ключ OpenRouter сохранён"
            statusText.setTextColor(tealColor)
            maskText.text = "Маска ключа: $masked"
            maskText.visibility = View.VISIBLE
        } else {
            statusText.text = "Ключ OpenRouter не задан"
            statusText.setTextColor(mutedColor)
            maskText.text = ""
            maskText.visibility = View.GONE
        }
    }

    /**
     * Show the currently selected model. [modelId] is the spinner's live choice; a null or
     * unknown id falls back to the default (google/gemini-2.0-flash-001) so a model is always
     * shown.
     */
    private fun renderModelStatus(modelId: String?) {
        val effective = modelId?.takeIf { AiModels.isKnown(it) } ?: AiModels.DEFAULT_MODEL_ID
        val option: AiModelOption? = AiModels.OPTIONS.firstOrNull { it.id == effective }
        val title = option?.title
        modelStatusText.text = if (title != null) {
            "Выбранная модель: $title ($effective)"
        } else {
            "Выбранная модель: $effective"
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
