package com.fersaiyan.cyanbridge.photo_question_tools

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Mock / server placeholder for the "ask a question about a photo" flow.
 *
 * This is the single swappable seam of the feature. Right now [respond] produces a
 * deterministic local placeholder answer after a short simulated delay, so the rest of
 * the screen (pick photo -> type question -> show answer) can be exercised end to end
 * with no backend. To go live, replace the body of [computePlaceholderAnswer] with a
 * real integration that reads [Request.imageUri] and submits it with [Request.question];
 * nothing else in [PhotoQuestionActivity] has to change.
 *
 * The work runs on a single background thread; the result is delivered back on the main
 * thread via [Handler]. [cancel]/[shutdown] make in-flight callbacks no-ops so the
 * Activity can stop a request when it is closing without leaking or crashing.
 */
class PhotoQuestionResponder {

    data class Request(
        /** Content Uri supplied by the picker; retained for a future local/server responder. */
        val imageUri: Uri,
        /** Display name of the picked image, used only to make the placeholder concrete. */
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
     * unless the request was cancelled/superseded before it finished.
     */
    fun respond(request: Request, onResult: (String) -> Unit) {
        val requestGeneration = generation
        executor.execute {
            // Simulated network latency. A real server call would block here instead.
            runCatching { Thread.sleep(SIMULATED_LATENCY_MS) }
            val answer = computePlaceholderAnswer(request)
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

    private fun computePlaceholderAnswer(request: Request): String = buildString {
        appendLine("[ЗАГЛУШКА / placeholder ответа]")
        appendLine()
        appendLine("Фото: ${request.imageName}")
        appendLine("Вопрос: ${request.question}")
        appendLine()
        append(
            "Это локальный mock-ответ. Реального распознавания пока нет — здесь " +
                "позже будет ответ сервера (PhotoQuestionResponder.computePlaceholderAnswer " +
                "заменяется на сетевой вызов).",
        )
    }

    private companion object {
        const val SIMULATED_LATENCY_MS = 700L
    }
}
