package com.fersaiyan.cyanbridge.photo_question_tools

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Answer engine for the "ask a question about a photo" flow, with two interchangeable
 * modes selected from [PhotoQuestionSettings]:
 *
 *   - [PhotoQuestionSettings.Mode.MOCK] — a deterministic local placeholder. This is the
 *     default and the offline fallback; it needs no key, no network and no permission.
 *   - [PhotoQuestionSettings.Mode.OPENROUTER_DIRECT] — reads the picked image, base64s it
 *     and calls OpenRouter directly with the user's own key/model via [OpenRouterClient].
 *
 * This class is the single swappable seam of the feature. A future Hermes/server mode
 * would be added as one more branch in [computeAnswer] (and one more [PhotoQuestionSettings.Mode]
 * entry) — nothing in [PhotoQuestionActivity]'s send path has to change.
 *
 * Work runs on a single background thread; the result is delivered on the main thread via
 * [Handler]. [cancel]/[shutdown] bump a generation counter so a result from a superseded
 * request becomes a no-op, letting the Activity stop a request as it closes without leaking
 * or crashing.
 */
class PhotoQuestionResponder(context: Context) {

    private val appContext: Context = context.applicationContext
    private val settings = PhotoQuestionSettings(appContext)

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
            computePlaceholderAnswer(request)
        }
        PhotoQuestionSettings.Mode.OPENROUTER_DIRECT -> computeOpenRouterAnswer(request)
    }

    private fun computeOpenRouterAnswer(request: Request): String {
        val apiKey = settings.apiKey
        val modelId = settings.modelId
        if (apiKey.isEmpty()) {
            return "Не задан OpenRouter API ключ. Откройте настройки и сохраните ключ."
        }
        if (modelId.isEmpty()) {
            return "Не задан model id. Откройте настройки и укажите модель."
        }

        val image = runCatching { readImageBytes(request.imageUri) }
            .getOrElse { error ->
                return when (error) {
                    is PhotoQuestionException -> error.message ?: "Не удалось прочитать фото."
                    else -> "Не удалось прочитать фото: ${error.message ?: "неизвестная ошибка"}"
                }
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
        }.getOrElse { error ->
            when (error) {
                is PhotoQuestionException -> error.message ?: "Ошибка запроса к OpenRouter."
                else -> "Ошибка запроса к OpenRouter: ${error.message ?: "неизвестная ошибка"}"
            }
        }
    }

    private data class ImageData(val bytes: ByteArray, val mimeType: String)

    /**
     * Read the picked image into memory, capped at [MAX_IMAGE_BYTES]. Throws a
     * [PhotoQuestionException] (with a user-ready message) when the image is missing,
     * unreadable or too large — never an OOM from slurping a huge file.
     */
    private fun readImageBytes(uri: Uri): ImageData {
        val mimeType = appContext.contentResolver.getType(uri) ?: DEFAULT_MIME
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
    }
}
