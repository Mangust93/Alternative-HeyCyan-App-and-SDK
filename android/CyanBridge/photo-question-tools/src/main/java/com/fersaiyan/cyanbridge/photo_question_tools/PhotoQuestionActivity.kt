package com.fersaiyan.cyanbridge.photo_question_tools

import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
 *   3. Send to [PhotoQuestionResponder] — a mock/server placeholder that returns a local
 *      answer today and is the single seam where a real backend call would slot in.
 *   4. Show the answer on screen.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.photo_question_tools.PhotoQuestionActivity
 */
class PhotoQuestionActivity : AppCompatActivity() {

    private val responder = PhotoQuestionResponder()

    private var selectedImage: Uri? = null
    private var selectedImageName: String = ""
    private var requestInFlight = false

    private lateinit var imagePreview: ImageView
    private lateinit var imageStatus: TextView
    private lateinit var questionInput: EditText
    private lateinit var askButton: Button
    private lateinit var answerView: TextView

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
                "текстом и получите ответ. Сейчас ответ — это заглушка/placeholder; " +
                "позже на его месте будет ответ сервера."
            textSize = 14f
            setPadding(0, 0, 0, gap)
        })

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

        updateAskEnabled()
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

        requestInFlight = true
        answerView.text = "Запрос отправлен (заглушка)…"
        updateAskEnabled()

        responder.respond(
            PhotoQuestionResponder.Request(imageName = selectedImageName, question = question),
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
