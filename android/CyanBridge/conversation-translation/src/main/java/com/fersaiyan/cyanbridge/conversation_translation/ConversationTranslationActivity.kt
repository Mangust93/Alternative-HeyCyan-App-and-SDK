package com.fersaiyan.cyanbridge.conversation_translation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import java.util.Locale

/**
 * V1 on-device dialog translation for the smart-glasses app.
 *
 * Pipeline (no Hermes, no OpenRouter, no official-app code):
 *   Android [SpeechRecognizer]
 *     -> ML Kit Language Identification (detect the language of the recognized text)
 *     -> ML Kit Translation (on-device, downloadable models)
 *     -> Android [TextToSpeech] (speak the translation)
 *
 * Lives in the optional, standalone :conversation-translation module. It has NO compile
 * dependency on :app and never touches the glasses / media / BLE / P2P / NativeAutomation
 * flow.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.conversation_translation.ConversationTranslationActivity
 */
class ConversationTranslationActivity : AppCompatActivity() {

    private companion object {
        const val RECORD_AUDIO_REQUEST = 4201

        // Language pairs offered in V1.
        const val PAIR_RU_EN = 0
        const val PAIR_RU_ES = 1

        // Direction modes (index into the direction spinner).
        const val DIR_AUTO = 0
        const val DIR_RU_EN = 1
        const val DIR_EN_RU = 2
        const val DIR_RU_ES = 3
        const val DIR_ES_RU = 4
    }

    private lateinit var pairSpinner: Spinner
    private lateinit var directionSpinner: Spinner
    private lateinit var autoSpeakSwitch: Switch
    private lateinit var sourceView: TextView
    private lateinit var detectedView: TextView
    private lateinit var translationView: TextView
    private lateinit var statusView: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Cache one Translator per source->target pair so we do not re-create them on every
    // phrase. All cached translators are closed in onDestroy().
    private val translators = HashMap<String, Translator>()
    private val modelManager = RemoteModelManager.getInstance()

    private var lastTranslation: String = ""
    private var lastTargetLang: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Перевод диалога"
        setContentView(buildUi())

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) setStatus("Озвучка недоступна: TextToSpeech не инициализирован")
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Распознавание речи недоступно на этом устройстве")
        }
        ensureAudioPermission()
        setStatus("Готово. Скачайте модели выбранной пары, затем нажмите Старт.")
    }

    // region UI -----------------------------------------------------------------------

    private fun buildUi(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val gap = (8 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Перевод диалога"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, gap)
        })

        root.addView(label("Языковая пара"))
        pairSpinner = Spinner(this).apply {
            adapter = simpleAdapter(listOf("Русский ↔ Английский", "Русский ↔ Испанский"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    // Reset direction to Auto whenever the pair changes to avoid a stale
                    // explicit direction that does not belong to the new pair.
                    directionSpinner.setSelection(DIR_AUTO)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        root.addView(pairSpinner)

        root.addView(label("Режим направления"))
        directionSpinner = Spinner(this).apply {
            adapter = simpleAdapter(listOf("Авто", "RU→EN", "EN→RU", "RU→ES", "ES→RU"))
        }
        root.addView(directionSpinner)

        root.addView(Button(this).apply {
            text = "Скачать модели"
            setOnClickListener { onDownloadModels() }
        })
        root.addView(Button(this).apply {
            text = "Старт"
            setOnClickListener { onStartListening() }
        })
        root.addView(Button(this).apply {
            text = "Стоп"
            setOnClickListener { onStopListening() }
        })
        root.addView(Button(this).apply {
            text = "Озвучить"
            setOnClickListener { onSpeakAgain() }
        })

        autoSpeakSwitch = Switch(this).apply {
            text = "Автоозвучка"
            isChecked = true
            setPadding(0, gap, 0, gap)
        }
        root.addView(autoSpeakSwitch)

        root.addView(label("Исходная фраза"))
        sourceView = valueView()
        root.addView(sourceView)

        root.addView(label("Определённый язык"))
        detectedView = valueView()
        root.addView(detectedView)

        root.addView(label("Перевод"))
        translationView = valueView()
        root.addView(translationView)

        root.addView(label("Статус / ошибка"))
        statusView = valueView()
        root.addView(statusView)

        return ScrollView(this).apply {
            addView(root)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
        }
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun valueView(): TextView = TextView(this).apply {
        textSize = 14f
        setTextIsSelectable(true)
        text = "—"
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    // endregion

    // region Permissions --------------------------------------------------------------

    private fun ensureAudioPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST,
            )
        }
        return granted
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            setStatus(if (ok) "Доступ к микрофону разрешён" else "Нет доступа к микрофону — распознавание не запустится")
        }
    }

    // endregion

    // region Language pair / direction helpers ----------------------------------------

    /** Two ML Kit language codes of the currently selected pair (first ↔ second). */
    private fun pairLanguages(): Pair<String, String> = when (pairSpinner.selectedItemPosition) {
        PAIR_RU_ES -> TranslateLanguage.RUSSIAN to TranslateLanguage.SPANISH
        else -> TranslateLanguage.RUSSIAN to TranslateLanguage.ENGLISH
    }

    /**
     * Resolves the (source, target) translation languages for the current phrase, given
     * the selected direction mode and (for Auto) the language detected in [detectedLang].
     * Returns null and reports a user-facing error when the input cannot be translated.
     */
    private fun resolveDirection(detectedLang: String?): Pair<String, String>? {
        val (a, b) = pairLanguages()
        return when (directionSpinner.selectedItemPosition) {
            DIR_RU_EN -> {
                if (pairSpinner.selectedItemPosition != PAIR_RU_EN) {
                    setStatus("Направление RU→EN не входит в выбранную пару"); null
                } else TranslateLanguage.RUSSIAN to TranslateLanguage.ENGLISH
            }
            DIR_EN_RU -> {
                if (pairSpinner.selectedItemPosition != PAIR_RU_EN) {
                    setStatus("Направление EN→RU не входит в выбранную пару"); null
                } else TranslateLanguage.ENGLISH to TranslateLanguage.RUSSIAN
            }
            DIR_RU_ES -> {
                if (pairSpinner.selectedItemPosition != PAIR_RU_ES) {
                    setStatus("Направление RU→ES не входит в выбранную пару"); null
                } else TranslateLanguage.RUSSIAN to TranslateLanguage.SPANISH
            }
            DIR_ES_RU -> {
                if (pairSpinner.selectedItemPosition != PAIR_RU_ES) {
                    setStatus("Направление ES→RU не входит в выбранную пару"); null
                } else TranslateLanguage.SPANISH to TranslateLanguage.RUSSIAN
            }
            else -> { // Авто: pick the direction from the detected language within the pair.
                when (detectedLang) {
                    a -> a to b
                    b -> b to a
                    null, "und" -> { setStatus("Не удалось определить язык фразы"); null }
                    else -> {
                        setStatus("Распознан язык «$detectedLang», он не входит в выбранную пару"); null
                    }
                }
            }
        }
    }

    /** Speech-recognition language hint. Auto biases to the pair's primary (Russian). */
    private fun recognitionLocale(): String = when (directionSpinner.selectedItemPosition) {
        DIR_RU_EN, DIR_RU_ES -> "ru-RU"
        DIR_EN_RU -> "en-US"
        DIR_ES_RU -> "es-ES"
        else -> if (pairSpinner.selectedItemPosition == PAIR_RU_ES) "ru-RU" else "ru-RU"
    }

    // endregion

    // region Speech recognition -------------------------------------------------------

    private fun onStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Распознавание речи недоступно на этом устройстве"); return
        }
        if (!ensureAudioPermission()) {
            setStatus("Запрошен доступ к микрофону — повторите Старт после разрешения"); return
        }
        sourceView.text = "—"
        detectedView.text = "—"
        translationView.text = "—"

        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(recognitionListener)
        speechRecognizer = recognizer

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        setStatus("Слушаю… говорите фразу")
        recognizer.startListening(intent)
    }

    private fun onStopListening() {
        speechRecognizer?.stopListening()
        setStatus("Остановлено")
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            setStatus("Ошибка распознавания: ${speechErrorText(error)}")
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isEmpty()) {
                setStatus("Пустой результат распознавания, попробуйте ещё раз"); return
            }
            sourceView.text = text
            identifyAndTranslate(text)
        }
    }

    private fun speechErrorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ошибка аудио"
        SpeechRecognizer.ERROR_CLIENT -> "ошибка клиента"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "нет разрешения на микрофон"
        SpeechRecognizer.ERROR_NETWORK -> "сетевая ошибка"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "таймаут сети"
        SpeechRecognizer.ERROR_NO_MATCH -> "речь не распознана"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "ошибка сервера"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "не было речи"
        else -> "код $error"
    }

    // endregion

    // region Language ID + translation ------------------------------------------------

    private fun identifyAndTranslate(text: String) {
        setStatus("Определяю язык…")
        val client = LanguageIdentification.getClient()
        client.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                detectedView.text = languageLabel(langCode)
                val direction = resolveDirection(langCode) ?: return@addOnSuccessListener
                translate(text, direction.first, direction.second)
            }
            .addOnFailureListener { e ->
                detectedView.text = "—"
                // Auto needs the detected language; explicit modes can still proceed.
                val direction = resolveDirection(null)
                if (direction != null) {
                    translate(text, direction.first, direction.second)
                } else {
                    setStatus("Не удалось определить язык: ${e.message ?: "ошибка ML Kit"}")
                }
            }
            .addOnCompleteListener { client.close() }
    }

    private fun translate(text: String, source: String, target: String) {
        val srcModel = TranslateRemoteModel.Builder(source).build()
        val tgtModel = TranslateRemoteModel.Builder(target).build()

        // Guard: ML Kit translate() does not auto-download; check both models are present
        // and give a clear instruction instead of a raw failure when they are missing.
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloaded ->
                val codes = downloaded.map { it.language }.toSet()
                if (!codes.contains(source) || !codes.contains(target)) {
                    setStatus("Сначала скачайте языковые модели")
                    return@addOnSuccessListener
                }
                runTranslator(text, source, target)
            }
            .addOnFailureListener {
                // If we cannot read the model list, attempt translation; it will surface a
                // clear message on its own failure path.
                runTranslator(text, source, target)
            }
    }

    private fun runTranslator(text: String, source: String, target: String) {
        setStatus("Перевожу ${languageLabel(source)} → ${languageLabel(target)}…")
        val translator = translatorFor(source, target)
        translator.translate(text)
            .addOnSuccessListener { translated ->
                translationView.text = translated
                lastTranslation = translated
                lastTargetLang = target
                setStatus("Готово")
                if (autoSpeakSwitch.isChecked) speak(translated, target)
            }
            .addOnFailureListener { e ->
                setStatus("Ошибка перевода: ${e.message ?: "модель недоступна, скачайте модели"}")
            }
    }

    private fun translatorFor(source: String, target: String): Translator {
        val key = "$source->$target"
        return translators.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
        }
    }

    // endregion

    // region Model download -----------------------------------------------------------

    private fun onDownloadModels() {
        val (a, b) = pairLanguages()
        setStatus("Скачиваю модели: ${languageLabel(a)} и ${languageLabel(b)}…")
        // Downloading one translator for the pair pulls BOTH language models, covering
        // either direction. Default conditions allow any network; failures (e.g. no
        // internet) are reported without crashing.
        val translator = translatorFor(a, b)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                setStatus("Модели готовы: ${languageLabel(a)} ↔ ${languageLabel(b)}")
            }
            .addOnFailureListener { e ->
                setStatus("Не удалось скачать модели (проверьте интернет): ${e.message ?: "ошибка"}")
            }
    }

    // endregion

    // region TTS ----------------------------------------------------------------------

    private fun onSpeakAgain() {
        val target = lastTargetLang
        if (lastTranslation.isEmpty() || target == null) {
            setStatus("Нет перевода для озвучки"); return
        }
        speak(lastTranslation, target)
    }

    private fun speak(text: String, languageCode: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            setStatus("Озвучка недоступна: TextToSpeech не готов"); return
        }
        val locale = ttsLocale(languageCode)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            setStatus("Язык озвучки недоступен: ${languageLabel(languageCode)}"); return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conv-translation")
    }

    private fun ttsLocale(languageCode: String): Locale = when (languageCode) {
        TranslateLanguage.RUSSIAN -> Locale("ru")
        TranslateLanguage.SPANISH -> Locale("es")
        else -> Locale.ENGLISH
    }

    // endregion

    private fun languageLabel(code: String?): String = when (code) {
        TranslateLanguage.RUSSIAN -> "Русский (ru)"
        TranslateLanguage.ENGLISH -> "Английский (en)"
        TranslateLanguage.SPANISH -> "Испанский (es)"
        null, "und" -> "не определён"
        else -> code
    }

    private fun setStatus(message: String) {
        statusView.text = message
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.apply { stop(); shutdown() }
        tts = null
        translators.values.forEach { it.close() }
        translators.clear()
        super.onDestroy()
    }
}
