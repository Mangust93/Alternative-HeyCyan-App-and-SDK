package com.fersaiyan.cyanbridge.conversation_translation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

/**
 * V1 on-device dialog translation for the smart-glasses app.
 *
 * Pipeline (no Hermes, no OpenRouter, no official-app code):
 *   Android [SpeechRecognizer]
 *     -> ML Kit Language Identification (only used by the two "Авто" modes)
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

        // SpeechRecognizer.ERROR_SERVER_DISCONNECTED (added API 33). Referenced as a literal
        // so the module still builds/runs on minSdk 24 without a NewApi field access.
        const val ERROR_SERVER_DISCONNECTED = 11

        // Small gap between destroying a recognizer and creating the next one. Recreating
        // immediately after an error/stop can otherwise hit "recognizer busy".
        const val RESTART_COOLDOWN_MS = 300L

        // Single "Кого переводим?" picker. Each entry bundles the language pair AND the
        // direction, so the user never has to reconcile two separate dropdowns.
        const val MODE_AUTO_RU_EN = 0 // Авто: русский ↔ английский (experimental)
        const val MODE_RU_EN = 1      // Я говорю по-русски → английский
        const val MODE_EN_RU = 2      // Собеседник говорит по-английски → русский
        const val MODE_AUTO_RU_ES = 3 // Авто: русский ↔ испанский (experimental)
        const val MODE_RU_ES = 4      // Я говорю по-русски → испанский
        const val MODE_ES_RU = 5      // Собеседник говорит по-испански → русский

        // Language-pair identity, derived from the selected mode (used by model download
        // and the readiness badge).
        const val PAIR_RU_EN = 0
        const val PAIR_RU_ES = 1

        // State labels shown in the prominent indicator.
        const val STATE_READY = "Готово"
        const val STATE_LISTENING = "Слушаю…"
        const val STATE_RECOGNIZING = "Распознаю…"
        const val STATE_TRANSLATING = "Перевожу…"
        const val STATE_SPEAKING = "Озвучиваю…"
        const val STATE_DOWNLOADING = "Скачиваю модели…"
        const val STATE_MODELS_READY = "Модели готовы"
        const val STATE_ERROR = "Ошибка"

        const val AUTO_HINT =
            "Авто-режим экспериментальный. Если английский плохо распознаётся, выберите режим " +
                "«Собеседник говорит по-английски → русский»."
    }

    private lateinit var modeSpinner: Spinner
    private lateinit var autoSpeakSwitch: Switch
    private lateinit var stateView: TextView
    private lateinit var modelsBadgeView: TextView
    private lateinit var autoHintView: TextView
    private lateinit var sourceView: TextView
    private lateinit var detectedView: TextView
    private lateinit var translationView: TextView
    private lateinit var statusView: TextView

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerNeedsCooldown = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var disposed = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // Cache one Translator per source->target pair so we do not re-create them on every
    // phrase. All cached translators are closed in onDestroy().
    private val translators = HashMap<String, Translator>()
    private val modelManager = RemoteModelManager.getInstance()

    private var lastTranslation: String = ""
    private var lastTargetLang: String? = null

    // Which pair (PAIR_RU_EN / PAIR_RU_ES) we have confirmed downloaded models for, or null.
    private var verifiedPair: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Перевод диалога"
        setContentView(buildUi())

        tts = TextToSpeech(this) { status ->
            if (!disposed) {
                ttsReady = status == TextToSpeech.SUCCESS
                if (!ttsReady) report(STATE_ERROR, "Озвучка недоступна: TextToSpeech не инициализирован")
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post { if (!disposed) report(STATE_READY, "Готово") }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { if (!disposed) report(STATE_ERROR, "Ошибка озвучки") }
            }
        })

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            report(STATE_ERROR, "Распознавание речи недоступно на этом устройстве")
        } else {
            report(STATE_READY, "Готово. Скачайте модели выбранной пары, затем нажмите Старт.")
        }
        ensureAudioPermission()
        updateModelsBadge()
        updateAutoHint()
    }

    // region UI -----------------------------------------------------------------------

    private fun buildUi(): View {
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val gap = (8 * density).toInt()
        val bottomSpacer = (96 * density).toInt()

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

        // Prominent state indicator, kept high on the screen so it is always visible.
        root.addView(label("Состояние"))
        stateView = TextView(this).apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            text = STATE_READY
        }
        root.addView(stateView)

        modelsBadgeView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, gap, 0, 0)
            text = "Модели текущей пары: не проверены"
        }
        root.addView(modelsBadgeView)

        root.addView(label("Кого переводим?"))
        modeSpinner = Spinner(this).apply {
            adapter = simpleAdapter(
                listOf(
                    "Авто: русский ↔ английский",
                    "Я говорю по-русски → английский",
                    "Собеседник говорит по-английски → русский",
                    "Авто: русский ↔ испанский",
                    "Я говорю по-русски → испанский",
                    "Собеседник говорит по-испански → русский",
                )
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    updateModelsBadge()
                    updateAutoHint()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        root.addView(modeSpinner)

        autoHintView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#B26A00"))
            setPadding(0, gap, 0, 0)
            text = AUTO_HINT
        }
        root.addView(autoHintView)

        // Primary actions, kept above the read-only output blocks.
        root.addView(actionButton("Скачать модели") { onDownloadModels() })
        root.addView(actionButton("Старт") { onStartListening() })
        root.addView(actionButton("Стоп") { onStopListening() })
        root.addView(actionButton("Озвучить") { onSpeakAgain() })

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

        root.addView(label("Подробности / ошибка"))
        statusView = valueView()
        root.addView(statusView)

        // Keeps the last block clear of the phone's bottom gesture zone even before the
        // window-inset padding is applied.
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                bottomSpacer,
            )
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            addView(root)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        // Apply safe top/bottom (and side) padding so the camera cutout, status bar and the
        // bottom gesture/navigation area never overlap the content.
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scroll)
        return scroll
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    private fun valueView(): TextView = TextView(this).apply {
        textSize = 14f
        setTextIsSelectable(true)
        // Multi-line: never truncate recognized text, translations or error details.
        maxLines = Int.MAX_VALUE
        setHorizontallyScrolling(false)
        text = "—"
    }

    private fun actionButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun simpleAdapter(items: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private fun updateModelsBadge() {
        if (!::modelsBadgeView.isInitialized) return
        val ready = verifiedPair != null && verifiedPair == currentPair()
        modelsBadgeView.text =
            if (ready) "Модели текущей пары: готовы" else "Модели текущей пары: не проверены"
        modelsBadgeView.setTextColor(if (ready) Color.parseColor("#2E7D32") else Color.DKGRAY)
    }

    private fun updateAutoHint() {
        if (!::autoHintView.isInitialized) return
        autoHintView.visibility = if (isAutoMode()) View.VISIBLE else View.GONE
    }

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
            if (ok) {
                report(STATE_READY, "Доступ к микрофону разрешён")
            } else {
                report(STATE_ERROR, "Нет доступа к микрофону — распознавание не запустится")
            }
        }
    }

    // endregion

    // region Mode helpers --------------------------------------------------------------

    private fun selectedMode(): Int =
        if (::modeSpinner.isInitialized) modeSpinner.selectedItemPosition else MODE_AUTO_RU_EN

    private fun isAutoMode(): Boolean = selectedMode() == MODE_AUTO_RU_EN || selectedMode() == MODE_AUTO_RU_ES

    /** Language pair the current mode belongs to. */
    private fun currentPair(): Int = when (selectedMode()) {
        MODE_AUTO_RU_ES, MODE_RU_ES, MODE_ES_RU -> PAIR_RU_ES
        else -> PAIR_RU_EN
    }

    /** The two ML Kit language codes of the current pair (used for model download). */
    private fun pairLanguages(): Pair<String, String> = when (currentPair()) {
        PAIR_RU_ES -> TranslateLanguage.RUSSIAN to TranslateLanguage.SPANISH
        else -> TranslateLanguage.RUSSIAN to TranslateLanguage.ENGLISH
    }

    /**
     * Resolves the (source, target) translation languages for the current phrase. Explicit
     * modes return a fixed direction; the two Auto modes pick the direction from the
     * language detected in [detectedLang]. Returns null (and reports an error) when an Auto
     * mode cannot map the detected language onto its pair.
     */
    private fun resolveDirection(detectedLang: String?): Pair<String, String>? = when (selectedMode()) {
        MODE_RU_EN -> TranslateLanguage.RUSSIAN to TranslateLanguage.ENGLISH
        MODE_EN_RU -> TranslateLanguage.ENGLISH to TranslateLanguage.RUSSIAN
        MODE_RU_ES -> TranslateLanguage.RUSSIAN to TranslateLanguage.SPANISH
        MODE_ES_RU -> TranslateLanguage.SPANISH to TranslateLanguage.RUSSIAN
        else -> {
            val (a, b) = pairLanguages() // a = Russian, b = English or Spanish
            when (detectedLang) {
                a -> a to b
                b -> b to a
                null, "und" -> {
                    report(STATE_ERROR, "Не удалось определить язык фразы. $AUTO_HINT"); null
                }
                else -> {
                    report(STATE_ERROR, "Распознан язык «${languageLabel(detectedLang)}», он не входит в выбранную пару. $AUTO_HINT")
                    null
                }
            }
        }
    }

    /** Speech-recognition language hint for the current mode. */
    private fun recognitionLocale(): String = when (selectedMode()) {
        MODE_EN_RU -> "en-US"
        MODE_ES_RU -> "es-ES"
        // RU→… and both Auto modes bias the recognizer to Russian.
        else -> "ru-RU"
    }

    // endregion

    // region Speech recognition -------------------------------------------------------

    private fun onStartListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            report(STATE_ERROR, "Распознавание речи недоступно на этом устройстве"); return
        }
        if (!ensureAudioPermission()) {
            report(STATE_ERROR, "Запрошен доступ к микрофону — повторите Старт после разрешения"); return
        }
        sourceView.text = "—"
        detectedView.text = "—"
        translationView.text = "—"

        // Always destroy any previous recognizer before creating a new one. After an error
        // the old instance is unusable, and a fresh one avoids "recognizer busy".
        val needsCooldown = speechRecognizer != null || recognizerNeedsCooldown
        releaseSpeechRecognizer(cancel = true)
        recognizerNeedsCooldown = false

        report(STATE_LISTENING, listeningDetail())
        mainHandler.removeCallbacks(beginListeningRunnable)
        mainHandler.postDelayed(beginListeningRunnable, if (needsCooldown) RESTART_COOLDOWN_MS else 0L)
    }

    private val beginListeningRunnable = Runnable { beginListening() }

    private fun beginListening() {
        if (disposed) return
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(recognitionListenerFor(recognizer))
        speechRecognizer = recognizer

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        runCatching { recognizer.startListening(intent) }
            .onFailure {
                releaseSpeechRecognizer(cancel = true)
                recognizerNeedsCooldown = true
                report(STATE_ERROR, "Не удалось запустить распознавание: ${it.message ?: "ошибка"}")
            }
    }

    private fun listeningDetail(): String {
        val base = "Слушаю… говорите фразу (${recognitionLocale()})"
        return if (isAutoMode()) "$base\n$AUTO_HINT" else base
    }

    private fun onStopListening() {
        mainHandler.removeCallbacks(beginListeningRunnable)
        val recognizer = speechRecognizer
        if (recognizer == null) {
            report(STATE_READY, "Распознавание не запущено")
            return
        }
        runCatching { recognizer.stopListening() }
            .onSuccess { report(STATE_READY, "Остановлено") }
            .onFailure { report(STATE_ERROR, "Не удалось остановить распознавание: ${it.message ?: "ошибка"}") }
    }

    private fun releaseSpeechRecognizer(cancel: Boolean) {
        val recognizer = speechRecognizer ?: return
        speechRecognizer = null
        if (cancel) runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }

    private fun recognitionListenerFor(recognizer: SpeechRecognizer) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            if (!disposed && speechRecognizer === recognizer) report(STATE_RECOGNIZING, "Распознаю…")
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            if (disposed || speechRecognizer !== recognizer) return
            // The recognizer is unusable after an error; destroy it so the next Старт builds
            // a clean instance.
            releaseSpeechRecognizer(cancel = true)
            recognizerNeedsCooldown = true
            if (error == ERROR_SERVER_DISCONNECTED) {
                report(STATE_ERROR, "Сервис распознавания отключился. Нажмите Старт ещё раз.")
            } else {
                report(STATE_ERROR, "Ошибка распознавания: ${speechErrorText(error)}")
            }
        }

        override fun onResults(results: Bundle?) {
            if (disposed || speechRecognizer !== recognizer) return
            releaseSpeechRecognizer(cancel = false)
            recognizerNeedsCooldown = true
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isEmpty()) {
                report(STATE_ERROR, "Пустой результат распознавания, попробуйте ещё раз"); return
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
        if (disposed) return
        // Explicit modes do not need language identification — translate directly. This
        // keeps EN→RU / ES→RU reliable even when the recognizer mis-tags the language.
        if (!isAutoMode()) {
            detectedView.text = "—"
            val direction = resolveDirection(null) ?: return
            translate(text, direction.first, direction.second)
            return
        }

        report(STATE_TRANSLATING, "Определяю язык…")
        val client = LanguageIdentification.getClient()
        client.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                if (disposed) return@addOnSuccessListener
                detectedView.text = languageLabel(langCode)
                val direction = resolveDirection(langCode) ?: return@addOnSuccessListener
                translate(text, direction.first, direction.second)
            }
            .addOnFailureListener { e ->
                if (disposed) return@addOnFailureListener
                detectedView.text = "—"
                report(STATE_ERROR, "Не удалось определить язык: ${e.message ?: "ошибка ML Kit"}. $AUTO_HINT")
            }
            .addOnCompleteListener { client.close() }
    }

    private fun translate(text: String, source: String, target: String) {
        if (disposed) return

        // Guard: ML Kit translate() does not auto-download; check both models are present
        // and give a clear instruction instead of a raw failure when they are missing.
        modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { downloaded ->
                if (disposed) return@addOnSuccessListener
                val codes = downloaded.map { it.language }.toSet()
                if (!codes.contains(source) || !codes.contains(target)) {
                    report(STATE_ERROR, "Сначала скачайте языковые модели (кнопка «Скачать модели»)")
                    return@addOnSuccessListener
                }
                // Models for this pair are confirmed present.
                verifiedPair = currentPair()
                updateModelsBadge()
                runTranslator(text, source, target)
            }
            .addOnFailureListener {
                if (disposed) return@addOnFailureListener
                // If we cannot read the model list, attempt translation; it will surface a
                // clear message on its own failure path.
                runTranslator(text, source, target)
            }
    }

    private fun runTranslator(text: String, source: String, target: String) {
        if (disposed) return
        report(STATE_TRANSLATING, "Перевожу ${languageLabel(source)} → ${languageLabel(target)}…")
        val translator = translatorFor(source, target)
        translator.translate(text)
            .addOnSuccessListener { translated ->
                if (disposed) return@addOnSuccessListener
                translationView.text = translated
                lastTranslation = translated
                lastTargetLang = target
                report(STATE_READY, "Готово")
                if (autoSpeakSwitch.isChecked) speak(translated, target)
            }
            .addOnFailureListener { e ->
                if (disposed) return@addOnFailureListener
                report(STATE_ERROR, "Ошибка перевода: ${e.message ?: "модель недоступна, скачайте модели"}")
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
        if (disposed) return
        val (a, b) = pairLanguages()
        val pairAtRequest = currentPair()
        report(STATE_DOWNLOADING, "Скачиваю модели: ${languageLabel(a)} и ${languageLabel(b)}…")
        // Downloading one translator for the pair pulls BOTH language models, covering
        // either direction. Default conditions allow any network; failures (e.g. no
        // internet) are reported without crashing.
        val translator = translatorFor(a, b)
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                if (disposed) return@addOnSuccessListener
                verifiedPair = pairAtRequest
                updateModelsBadge()
                report(STATE_MODELS_READY, "Модели готовы: ${languageLabel(a)} ↔ ${languageLabel(b)}")
            }
            .addOnFailureListener { e ->
                if (disposed) return@addOnFailureListener
                report(STATE_ERROR, "Не удалось скачать модели. Проверьте интернет. (${e.message ?: "ошибка"})")
            }
    }

    // endregion

    // region TTS ----------------------------------------------------------------------

    private fun onSpeakAgain() {
        val target = lastTargetLang
        if (lastTranslation.isEmpty() || target == null) {
            report(STATE_ERROR, "Нет перевода для озвучки"); return
        }
        speak(lastTranslation, target)
    }

    private fun speak(text: String, languageCode: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            report(STATE_ERROR, "Озвучка недоступна: TextToSpeech не готов"); return
        }
        val locale = ttsLocale(languageCode)
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            report(STATE_ERROR, "Язык озвучки недоступен: ${languageLabel(languageCode)}"); return
        }
        report(STATE_SPEAKING, "Озвучиваю…")
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "conv-translation")
    }

    private fun ttsLocale(languageCode: String): Locale = when (languageCode) {
        TranslateLanguage.RUSSIAN -> Locale("ru", "RU")
        TranslateLanguage.SPANISH -> Locale("es", "ES")
        else -> Locale.US
    }

    // endregion

    private fun languageLabel(code: String?): String = when (code) {
        TranslateLanguage.RUSSIAN -> "Русский (ru)"
        TranslateLanguage.ENGLISH -> "Английский (en)"
        TranslateLanguage.SPANISH -> "Испанский (es)"
        null, "und" -> "не определён"
        else -> code
    }

    /** Updates the prominent state label and the detailed status line together. */
    private fun report(state: String, detail: String) {
        if (::stateView.isInitialized) stateView.text = state
        if (::statusView.isInitialized) statusView.text = detail
    }

    override fun onDestroy() {
        disposed = true
        mainHandler.removeCallbacks(beginListeningRunnable)
        releaseSpeechRecognizer(cancel = true)
        tts?.apply { stop(); shutdown() }
        tts = null
        translators.values.forEach { it.close() }
        translators.clear()
        super.onDestroy()
    }
}
