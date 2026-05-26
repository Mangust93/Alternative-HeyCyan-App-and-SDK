package com.fersaiyan.cyanbridge.photo_question_tools

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Photo question screen.
 *
 * Lives in the optional, standalone :photo-question-tools module. It has NO compile
 * dependency on :app, the glasses SDK (com.oudmon.ble.*), BLE, the media flow or any
 * other optional module.
 *
 * Flow:
 *   1. Pick a photo with the system photo picker (GetContent — no storage permission;
 *      the picker also lists photos already downloaded from the glasses into the gallery).
 *   2. Type a text question about it.
 *   3. Send to [PhotoQuestionResponder], which answers either with a local Mock placeholder
 *      (default) or, in OpenRouter Direct mode, by calling OpenRouter with the user's own
 *      API key and model id (see the settings panel below).
 *   4. Show the answer on screen.
 *
 * Answer settings (mode, OpenRouter API key, model id) are stored locally by
 * [PhotoQuestionSettings] in this module's own SharedPreferences. The key is user-supplied,
 * never bundled, never logged, and shown only masked once saved.
 *
 * This internal screen is opened from Tools / Diagnostics through the package-scoped
 * PHOTO_QUESTION intent action; it is deliberately not exported for adb/external launch.
 */
class PhotoQuestionActivity : AppCompatActivity() {

    private val settings by lazy { PhotoQuestionSettings(this) }
    private val responder by lazy { PhotoQuestionResponder(this) }

    private var selectedImage: Uri? = null
    private var selectedImageName: String = ""
    private var requestInFlight = false

    private lateinit var imagePreview: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var questionInput: EditText
    private lateinit var askButton: Button
    private lateinit var answerView: TextView

    private lateinit var modeGroup: RadioGroup
    private lateinit var mockRadio: RadioButton
    private lateinit var openRouterRadio: RadioButton
    private lateinit var credentialsPanel: LinearLayout
    private lateinit var apiKeyInput: EditText
    private lateinit var modelIdInput: EditText
    private lateinit var keyStatusView: TextView

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onImagePicked(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Фото и вопрос"

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val gap = (8 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Фото и вопрос"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, gap)
        })

        root.addView(TextView(this).apply {
            text = "Выберите фото (включая фото, скачанные с очков), задайте вопрос " +
                "текстом и получите ответ. По умолчанию ответ — это локальная заглушка " +
                "(режим Mock). Для настоящего ответа включите режим OpenRouter Direct ниже."
            textSize = 14f
            setPadding(0, 0, 0, gap)
        })

        addSettingsPanel(root, gap)

        root.addView(Button(this).apply {
            text = "Выбрать фото"
            setOnClickListener { launchPicker() }
        })

        imageStatus = TextView(this).apply {
            text = "Фото не выбрано"
            textSize = 13f
            setPadding(0, gap, 0, gap)
        }
        root.addView(imageStatus)

        imagePreview = ImageView(this).apply {
            adjustViewBounds = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, (220 * density).toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(imagePreview)

        root.addView(TextView(this).apply {
            text = "Вопрос"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, gap)
        })

        questionInput = EditText(this).apply {
            hint = "Например: что изображено на фото?"
            textSize = 15f
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = updateAskEnabled()
            })
        }
        root.addView(questionInput)

        askButton = Button(this).apply {
            text = "Спросить"
            setOnClickListener { onAsk() }
        }
        root.addView(askButton)

        root.addView(TextView(this).apply {
            text = "Ответ"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad, 0, gap)
        })

        answerView = TextView(this).apply {
            text = "(ответа пока нет)"
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        root.addView(answerView)

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        bindSettings()
        updateAskEnabled()
    }

    private fun addSettingsPanel(root: LinearLayout, gap: Int) {
        root.addView(TextView(this).apply {
            text = "Режим ответа"
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, gap, 0, gap)
        })

        mockRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "Mock (локальная заглушка)"
        }
        openRouterRadio = RadioButton(this).apply {
            id = View.generateViewId()
            text = "OpenRouter Direct (свой ключ)"
        }
        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            addView(mockRadio)
            addView(openRouterRadio)
            setOnCheckedChangeListener { _, checkedId ->
                val mode = if (checkedId == openRouterRadio.id) {
                    PhotoQuestionSettings.Mode.OPENROUTER_DIRECT
                } else {
                    PhotoQuestionSettings.Mode.MOCK
                }
                settings.mode = mode
                updateCredentialsVisibility(mode)
                updateAskEnabled()
            }
        }
        root.addView(modeGroup)

        credentialsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        credentialsPanel.addView(TextView(this).apply {
            text = "OpenRouter API ключ"
            textSize = 13f
            setPadding(0, gap, 0, 0)
        })
        apiKeyInput = EditText(this).apply {
            hint = "sk-or-..."
            textSize = 14f
            // VISIBLE_PASSWORD keeps the key readable for pasting but stops the keyboard
            // from learning/suggesting it; combined with no multiline.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        credentialsPanel.addView(apiKeyInput)

        keyStatusView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, gap)
        }
        credentialsPanel.addView(keyStatusView)

        credentialsPanel.addView(TextView(this).apply {
            text = "Model id"
            textSize = 13f
        })
        modelIdInput = EditText(this).apply {
            hint = "например: openai/gpt-4o-mini"
            textSize = 14f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        credentialsPanel.addView(modelIdInput)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, gap, 0, 0)
        }
        buttonRow.addView(Button(this).apply {
            text = "Сохранить настройки"
            setOnClickListener { onSaveSettings() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        buttonRow.addView(Button(this).apply {
            text = "Очистить ключ"
            setOnClickListener { onClearKey() }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        credentialsPanel.addView(buttonRow)

        credentialsPanel.addView(TextView(this).apply {
            text = "Внимание: ключ хранится локально на этом устройстве (без шифрования). " +
                "Не используйте ключ с большим лимитом — задайте отдельный ключ с " +
                "ограничением расходов. Ключ не логируется и не показывается полностью."
            textSize = 12f
            setPadding(0, gap, 0, gap)
        })

        root.addView(credentialsPanel)
    }

    /** Initialise the settings widgets from stored values. The raw key is never prefilled. */
    private fun bindSettings() {
        val mode = settings.mode
        when (mode) {
            PhotoQuestionSettings.Mode.MOCK -> mockRadio.isChecked = true
            PhotoQuestionSettings.Mode.OPENROUTER_DIRECT -> openRouterRadio.isChecked = true
        }
        modelIdInput.setText(settings.modelId)
        updateKeyStatus()
        updateCredentialsVisibility(mode)
    }

    private fun updateCredentialsVisibility(mode: PhotoQuestionSettings.Mode) {
        credentialsPanel.visibility =
            if (mode == PhotoQuestionSettings.Mode.OPENROUTER_DIRECT) View.VISIBLE else View.GONE
    }

    private fun onSaveSettings() {
        val key = apiKeyInput.text?.toString().orEmpty()
        val model = modelIdInput.text?.toString().orEmpty()
        // An empty key field on save keeps any previously saved key rather than wiping it.
        val effectiveKey = key.trim().ifEmpty { settings.apiKey }
        settings.saveCredentials(effectiveKey, model)
        // Drop the raw key from the input so it does not linger on screen.
        apiKeyInput.text?.clear()
        modelIdInput.setText(settings.modelId)
        updateKeyStatus()
        answerView.text = "Настройки сохранены."
        updateAskEnabled()
    }

    private fun onClearKey() {
        settings.clearApiKey()
        apiKeyInput.text?.clear()
        updateKeyStatus()
        answerView.text = "Ключ удалён с устройства."
        updateAskEnabled()
    }

    private fun updateKeyStatus() {
        keyStatusView.text = "Сохранённый ключ: ${settings.maskedApiKey()}"
    }

    override fun onDestroy() {
        responder.shutdown()
        super.onDestroy()
    }

    private fun launchPicker() {
        runCatching { pickImage.launch("image/*") }
            .onFailure {
                imageStatus.text = "Не удалось открыть выбор фото"
            }
    }

    private fun onImagePicked(uri: Uri) {
        selectedImage = uri
        selectedImageName = queryDisplayName(uri)
        imageStatus.text = "Фото: $selectedImageName"
        runCatching { imagePreview.setImageURI(uri) }
            .onSuccess { imagePreview.visibility = View.VISIBLE }
            .onFailure { imagePreview.visibility = View.GONE }
        updateAskEnabled()
    }

    private fun onAsk() {
        val uri = selectedImage ?: return
        val question = questionInput.text?.toString()?.trim().orEmpty()
        if (question.isEmpty() || requestInFlight) return

        val mode = settings.mode
        if (mode == PhotoQuestionSettings.Mode.OPENROUTER_DIRECT) {
            if (!settings.hasApiKey) {
                answerView.text = "Не задан OpenRouter API ключ. Сохраните ключ в настройках."
                return
            }
            if (settings.modelId.isEmpty()) {
                answerView.text = "Не задан model id. Укажите модель в настройках."
                return
            }
        }

        requestInFlight = true
        answerView.text = when (mode) {
            PhotoQuestionSettings.Mode.MOCK -> "Запрос отправлен (заглушка)…"
            PhotoQuestionSettings.Mode.OPENROUTER_DIRECT -> "Запрос отправлен в OpenRouter…"
        }
        updateAskEnabled()

        responder.respond(
            PhotoQuestionResponder.Request(
                imageUri = uri,
                imageName = selectedImageName,
                question = question,
            ),
        ) { answer ->
            requestInFlight = false
            answerView.text = answer
            updateAskEnabled()
        }
    }

    private fun updateAskEnabled() {
        val ready = selectedImage != null &&
            !questionInput.text.isNullOrBlank() &&
            !requestInFlight
        askButton.isEnabled = ready
        askButton.alpha = if (ready) 1f else 0.45f
    }

    /** Best-effort human name for the picked content Uri. */
    private fun queryDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment ?: "(без имени)"
    }
}
