package com.fersaiyan.cyanbridge.photo_question_tools

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ai_config.AiSettingsStore

/**
 * Photo question screen.
 *
 * Lives in the optional, standalone :photo-question-tools module. Beyond the shared
 * :ai-config settings layer it has NO compile dependency on :app, the glasses SDK
 * (com.oudmon.ble.*), BLE, the media flow or any other optional module.
 *
 * Flow:
 *   1. Pick a photo with the system photo picker (GetContent — no storage permission;
 *      the picker also lists photos already downloaded from the glasses into the gallery).
 *   2. Type a text question about it.
 *   3. Send to [PhotoQuestionResponder], which answers either with a local Mock placeholder
 *      (default) or, in OpenRouter Direct mode, by calling OpenRouter with the user's own
 *      API key and model.
 *   4. Show the answer on screen.
 *
 * Only the answer [PhotoQuestionSettings.Mode] (Mock / OpenRouter Direct) is stored by this
 * module. The OpenRouter API key and model are read from the shared :ai-config store
 * ([AiSettingsStore]) — the single source of truth managed on the "Настройки AI" screen —
 * so this screen no longer asks for them. The settings panel here only shows the
 * (masked) key status and the effective model; the raw key is never displayed.
 *
 * This internal screen is opened from Tools / Diagnostics through the package-scoped
 * PHOTO_QUESTION intent action; it is deliberately not exported for adb/external launch.
 */
class PhotoQuestionActivity : AppCompatActivity() {

    private val settings by lazy { PhotoQuestionSettings(this) }
    private val aiSettings by lazy { AiSettingsStore(this) }
    private val responder by lazy { PhotoQuestionResponder(this) }

    private var selectedImage: Uri? = null
    private var selectedImageName: String = ""
    private var requestInFlight = false

    private lateinit var imageStatus: TextView
    private lateinit var questionInput: EditText
    private lateinit var askButton: Button
    private lateinit var answerView: TextView

    private lateinit var modeGroup: RadioGroup
    private lateinit var mockRadio: RadioButton
    private lateinit var openRouterRadio: RadioButton
    private lateinit var credentialsPanel: LinearLayout
    private lateinit var aiKeyStatusView: TextView
    private lateinit var aiModelStatusView: TextView

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

    /**
     * In OpenRouter Direct mode the key and model come from the shared "Настройки AI" store,
     * so this panel only reports their (read-only) status; it never edits or displays the
     * raw key. The user manages them on the dedicated AI settings screen.
     */
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
            text = "OpenRouter Direct (ключ из Настроек AI)"
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
                refreshAiStatus()
                updateAskEnabled()
            }
        }
        root.addView(modeGroup)

        credentialsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        credentialsPanel.addView(TextView(this).apply {
            text = "Ключ и модель берутся из раздела «Настройки AI»."
            textSize = 13f
            setPadding(0, gap, 0, 0)
        })

        aiKeyStatusView = TextView(this).apply {
            textSize = 13f
            setPadding(0, gap, 0, 0)
        }
        credentialsPanel.addView(aiKeyStatusView)

        aiModelStatusView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, gap)
        }
        credentialsPanel.addView(aiModelStatusView)

        credentialsPanel.addView(TextView(this).apply {
            text = "Чтобы изменить ключ или модель, откройте «Настройки AI». Ключ хранится " +
                "только на этом устройстве, не логируется и не показывается полностью."
            textSize = 12f
            setPadding(0, gap, 0, gap)
        })

        root.addView(credentialsPanel)
    }

    /** Initialise the settings widgets from stored values. The raw key is never shown. */
    private fun bindSettings() {
        val mode = settings.mode
        when (mode) {
            PhotoQuestionSettings.Mode.MOCK -> mockRadio.isChecked = true
            PhotoQuestionSettings.Mode.OPENROUTER_DIRECT -> openRouterRadio.isChecked = true
        }
        refreshAiStatus()
        updateCredentialsVisibility(mode)
    }

    override fun onResume() {
        super.onResume()
        // The key/model may have changed on the "Настройки AI" screen while we were away.
        refreshAiStatus()
        updateAskEnabled()
    }

    private fun updateCredentialsVisibility(mode: PhotoQuestionSettings.Mode) {
        credentialsPanel.visibility =
            if (mode == PhotoQuestionSettings.Mode.OPENROUTER_DIRECT) View.VISIBLE else View.GONE
    }

    /** Reflect the shared AI settings (masked key + effective model) without revealing the key. */
    private fun refreshAiStatus() {
        if (!::aiKeyStatusView.isInitialized) return
        val masked = aiSettings.getMaskedApiKey()
        aiKeyStatusView.text = if (masked != null) {
            "Ключ OpenRouter: $masked"
        } else {
            "Ключ OpenRouter: не задан — добавьте в «Настройки AI»"
        }
        aiModelStatusView.text = "Модель: ${aiSettings.getEffectiveModelId()}"
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
        // Do not decode an arbitrary provider image on the UI thread before the bounded
        // background reader validates its size. The selected-name status is sufficient here.
        updateAskEnabled()
    }

    private fun onAsk() {
        val uri = selectedImage ?: return
        val question = questionInput.text?.toString()?.trim().orEmpty()
        if (question.isEmpty() || requestInFlight) return

        // The no-key case (and every other outcome) is handled inside the responder, which
        // returns a user-ready message, sends no OpenRouter request when the key is missing,
        // and records the matching history entry (SUCCESS / ERROR / CONFIG_MISSING). The
        // model always resolves to a valid id, so there is no separate "model not set" gate.
        val mode = settings.mode

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
