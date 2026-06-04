package com.fersaiyan.cyanbridge.photo_question_tools

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.fersaiyan.cyanbridge.ai_config.AiSettingsStore
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryFeatureType
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryItem
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryStatus
import com.fersaiyan.cyanbridge.ai_history_core.AiRequestHistoryStore
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Answer engine for the "ask a question about a photo" flow, with two interchangeable
 * modes selected from [PhotoQuestionSettings]:
 *
 *   - [PhotoQuestionSettings.Mode.MOCK] — a deterministic local placeholder. This is the
 *     default and the offline fallback; it needs no key, no network and no permission.
 *   - [PhotoQuestionSettings.Mode.OPENROUTER_DIRECT] — reads the picked image, base64s it
 *     and calls OpenRouter directly via [OpenRouterClient]. The API key and model come from
 *     the shared :ai-config store (AiSettingsStore), the same source the "Настройки AI"
 *     screen writes; this class only consumes them and never logs the key.
 *
 * This class is the single swappable seam of the feature. A future Hermes/server mode
 * would be added as one more branch in [computeAnswer] (and one more [PhotoQuestionSettings.Mode]
 * entry) — nothing in [PhotoQuestionActivity]'s send path has to change.
 *
 * After every outcome — a Mock or OpenRouter success, an error, or a missing-key
 * CONFIG_MISSING — it appends a record to the shared local history (:ai-history-core) via
 * [recordHistory]. That record never contains the API key or the image bytes.
 *
 * Work runs on a single background thread; the result is delivered on the main thread via
 * [Handler]. [cancel]/[shutdown] bump a generation counter so a result from a superseded
 * request becomes a no-op, letting the Activity stop a request as it closes without leaking
 * or crashing.
 */
class PhotoQuestionResponder(context: Context) {

    private val appContext: Context = context.applicationContext
    private val settings = PhotoQuestionSettings(appContext)

    // OpenRouter Direct mode reads the API key and model from the shared :ai-config store
    // (managed on "Настройки AI"), not from this module's own settings.
    private val aiSettings = AiSettingsStore(appContext)

    // Every answer (success / error / missing-config) is recorded in the shared local
    // history. The history item never carries the API key nor the image bytes — only the
    // question text, the answer/error text and small metadata (see [recordHistory]).
    private val historyStore = AiRequestHistoryStore(appContext)

    data class Request(
        val imageUri: Uri,
        /** Display name of the picked image, used to make the mock answer concrete. */
        val imageName: String,
        val question: String,
    )

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Bumped on cancel/shutdown so a result from a superseded request is dropped. */
    @Volatile
    private var generation = 0

    /**
     * Submit [request]. [onResult] is always called on the main thread, exactly once,
     * unless the request was cancelled/superseded before it finished. The result string is
     * either the answer or a user-readable error message — callers just display it.
     */
    fun respond(request: Request, onResult: (String) -> Unit) {
        val requestGeneration = generation
        executor.execute {
            val answer = computeAnswer(request)
            mainHandler.post {
                if (requestGeneration == generation) onResult(answer)
            }
        }
    }

    /** Drop any in-flight result without tearing down the executor. */
    fun cancel() {
        generation += 1
    }

    /** Cancel and release the background thread. Call from the Activity's onDestroy. */
    fun shutdown() {
        generation += 1
        executor.shutdownNow()
    }

    private fun computeAnswer(request: Request): String = when (settings.mode) {
        PhotoQuestionSettings.Mode.MOCK -> {
            runCatching { Thread.sleep(SIMULATED_LATENCY_MS) }
            val answer = computePlaceholderAnswer(request)
            recordHistory(
                request = request,
                status = AiRequestHistoryStatus.SUCCESS,
                answer = answer,
                modelId = null,
                provider = PROVIDER_MOCK,
                errorMessage = null,
            )
            answer
        }
        PhotoQuestionSettings.Mode.OPENROUTER_DIRECT -> computeOpenRouterAnswer(request)
    }

    private fun computeOpenRouterAnswer(request: Request): String {
        // Key and model come from the shared :ai-config store. No key => do not build or
        // send any OpenRouter request; surface a message pointing at "Настройки AI" and
        // record a CONFIG_MISSING entry (the key itself is never written to history).
        val apiKey = aiSettings.getApiKey().orEmpty()
        if (apiKey.isEmpty()) {
            val message = "Добавьте OpenRouter API key в Настройки AI"
            recordHistory(
                request = request,
                status = AiRequestHistoryStatus.CONFIG_MISSING,
                answer = null,
                modelId = null,
                provider = PROVIDER_OPENROUTER,
                errorMessage = message,
            )
            return message
        }
        // Always a valid id: the user's selection if valid, otherwise the shared default
        // (google/gemini-2.0-flash-001), so there is no "model not set" failure here.
        val modelId = aiSettings.getEffectiveModelId()

        val image = runCatching { readImageBytes(request.imageUri) }
            .getOrElse { error ->
                val message = when (error) {
                    is PhotoQuestionException -> error.message ?: "Не удалось прочитать фото."
                    else -> "Не удалось прочитать фото: ${error.message ?: "неизвестная ошибка"}"
                }
                recordHistory(
                    request = request,
                    status = AiRequestHistoryStatus.ERROR,
                    answer = null,
                    modelId = modelId,
                    provider = PROVIDER_OPENROUTER,
                    errorMessage = message,
                )
                return message
            }

        return runCatching {
            OpenRouterClient.requestAnswer(
                OpenRouterClient.Params(
                    apiKey = apiKey,
                    modelId = modelId,
                    question = request.question,
                    imageBytes = image.bytes,
                    mimeType = image.mimeType,
                ),
            )
        }.fold(
            onSuccess = { answer ->
                recordHistory(
                    request = request,
                    status = AiRequestHistoryStatus.SUCCESS,
                    answer = answer,
                    modelId = modelId,
                    provider = PROVIDER_OPENROUTER,
                    errorMessage = null,
                )
                answer
            },
            onFailure = { error ->
                val message = when (error) {
                    is PhotoQuestionException -> error.message ?: "Ошибка запроса к OpenRouter."
                    else -> "Не удалось выполнить запрос к OpenRouter."
                }
                recordHistory(
                    request = request,
                    status = AiRequestHistoryStatus.ERROR,
                    answer = null,
                    modelId = modelId,
                    provider = PROVIDER_OPENROUTER,
                    errorMessage = message,
                )
                message
            },
        )
    }

    /**
     * Append one record to the shared local history. Deliberately stores only safe, small
     * fields: never the API key and never the image bytes/base64 — [AiRequestHistoryItem.imageUri]
     * is left null and only the picked image's display name is kept as a label. Any failure
     * here is swallowed so history bookkeeping can never break the user-facing answer.
     */
    private fun recordHistory(
        request: Request,
        status: AiRequestHistoryStatus,
        answer: String?,
        modelId: String?,
        provider: String,
        errorMessage: String?,
    ) {
        runCatching {
            historyStore.saveHistoryItem(
                AiRequestHistoryItem(
                    id = UUID.randomUUID().toString(),
                    timestampMillis = System.currentTimeMillis(),
                    featureType = AiRequestHistoryFeatureType.PHOTO_QUESTION,
                    question = request.question,
                    answer = answer,
                    modelId = modelId,
                    provider = provider,
                    // Never persist the content Uri or any image bytes; a human-readable
                    // label (the picked file name) is enough and carries no binary data.
                    imageUri = null,
                    imageLabel = request.imageName.ifBlank { null },
                    status = status,
                    errorMessage = errorMessage,
                ),
            )
        }
    }

    private data class ImageData(val bytes: ByteArray, val mimeType: String)

    /**
     * Read the picked image into memory, capped at [MAX_IMAGE_BYTES]. Throws a
     * [PhotoQuestionException] (with a user-ready message) when the image is missing,
     * unreadable or too large — never an OOM from slurping a huge file.
     */
    private fun readImageBytes(uri: Uri): ImageData {
        val mimeType = appContext.contentResolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: DEFAULT_MIME
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw PhotoQuestionException("Не удалось открыть выбранное фото.")

        input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_IMAGE_BYTES) {
                    throw PhotoQuestionException(
                        "Фото слишком большое (более ${MAX_IMAGE_BYTES / (1024 * 1024)} МБ). " +
                            "Выберите изображение меньшего размера.",
                    )
                }
                out.write(buffer, 0, read)
            }
            if (total == 0) throw PhotoQuestionException("Выбранное фото пустое.")
            return ImageData(out.toByteArray(), mimeType)
        }
    }

    private fun computePlaceholderAnswer(request: Request): String = buildString {
        appendLine("[ЗАГЛУШКА / placeholder ответа]")
        appendLine()
        appendLine("Фото: ${request.imageName}")
        appendLine("Вопрос: ${request.question}")
        appendLine()
        append(
            "Это локальный mock-ответ (режим Mock). Реального распознавания нет. Для " +
                "настоящего ответа включите режим OpenRouter Direct в настройках и укажите " +
                "свой API ключ и model id.",
        )
    }

    private companion object {
        const val SIMULATED_LATENCY_MS = 700L
        const val DEFAULT_MIME = "image/jpeg"

        /** First-version cap on the raw image; base64 inflates this by ~33% in the request. */
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024

        /** History provider labels for the two answer modes. */
        const val PROVIDER_MOCK = "mock"
        const val PROVIDER_OPENROUTER = "openrouter"
    }
}
