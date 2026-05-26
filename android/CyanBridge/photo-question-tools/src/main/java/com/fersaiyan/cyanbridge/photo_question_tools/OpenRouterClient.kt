package com.fersaiyan.cyanbridge.photo_question_tools

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Minimal, dependency-free OpenRouter chat/completions client used by the OpenRouter
 * Direct answer mode.
 *
 * Deliberately built on only HttpURLConnection + org.json + android.util.Base64 — no
 * OkHttp/Retrofit/Ktor — to keep this optional module's footprint tiny and avoid bumping
 * shared HTTP libraries app-wide.
 *
 * It posts the question together with the picked image (as a base64 data URL) and returns
 * choices[0].message.content. Every failure surfaces as a [PhotoQuestionException] whose
 * message is already a user-readable Russian string, so the Activity can show it verbatim
 * without crashing. The user's API key is only ever placed in the Authorization header; it
 * is never logged and never echoed back in an error.
 */
internal object OpenRouterClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    data class Params(
        val apiKey: String,
        val modelId: String,
        val question: String,
        val imageBytes: ByteArray,
        val mimeType: String,
    )

    /**
     * Perform the call on the calling (background) thread and return the answer text.
     * Throws [PhotoQuestionException] with a display-ready message on any failure.
     */
    fun requestAnswer(params: Params): String {
        val body = buildRequestBody(params)
        val connection = openConnection(params.apiKey)
        try {
            writeBody(connection, body)
            val status = readStatus(connection)
            val payload = readPayload(connection, status)
            if (status != HttpURLConnection.HTTP_OK) {
                throw PhotoQuestionException(mapHttpError(status, payload, params.apiKey))
            }
            return parseAnswer(payload, params.apiKey)
        } catch (e: SocketTimeoutException) {
            throw PhotoQuestionException("Превышено время ожидания ответа OpenRouter. Попробуйте ещё раз.")
        } catch (e: UnknownHostException) {
            throw PhotoQuestionException("Нет подключения к интернету или сервер недоступен.")
        } catch (e: IOException) {
            val detail = redactSecret(e.message ?: "неизвестно", params.apiKey)
            throw PhotoQuestionException("Сетевая ошибка при обращении к OpenRouter: $detail")
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(params: Params): ByteArray {
        val dataUrl = buildString {
            append("data:")
            append(params.mimeType)
            append(";base64,")
            append(Base64.encodeToString(params.imageBytes, Base64.NO_WRAP))
        }

        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", params.question))
            .put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", dataUrl)),
            )

        val messages = JSONArray().put(
            JSONObject().put("role", "user").put("content", content),
        )

        return JSONObject()
            .put("model", params.modelId)
            .put("messages", messages)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    private fun openConnection(apiKey: String): HttpURLConnection {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        require(connection is HttpsURLConnection) { "OpenRouter endpoint must be HTTPS" }
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        return connection
    }

    private fun writeBody(connection: HttpURLConnection, body: ByteArray) {
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }
    }

    private fun readStatus(connection: HttpURLConnection): Int =
        // responseCode triggers the request; IOException here means connect/transport failure.
        connection.responseCode

    private fun readPayload(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_RESPONSE_BYTES) {
                    throw PhotoQuestionException("Ответ OpenRouter слишком большой и был отклонён.")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }.orEmpty()
    }

    private fun parseAnswer(payload: String, apiKey: String): String {
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: throw PhotoQuestionException("Не удалось разобрать ответ OpenRouter (некорректный JSON).")

        // An HTTP 200 can still carry an { "error": ... } object for some failures.
        root.optJSONObject("error")?.let { error ->
            val message = redactSecret(
                error.optString("message").ifBlank { "неизвестная ошибка" },
                apiKey,
            )
            throw PhotoQuestionException("OpenRouter вернул ошибку: $message")
        }

        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw PhotoQuestionException("Ответ OpenRouter не содержит choices.")
        }
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw PhotoQuestionException("Ответ OpenRouter не содержит message.")

        val content = extractContent(message)
        if (content.isBlank()) {
            throw PhotoQuestionException(
                "Модель не вернула текстовый ответ. Возможно, выбранная модель не " +
                    "поддерживает изображения — попробуйте другую model id.",
            )
        }
        return content.trim()
    }

    /** content is usually a String, but some models return an array of typed parts. */
    private fun extractContent(message: JSONObject): String {
        message.optString("content").takeIf { it.isNotBlank() }?.let { return it }
        val parts = message.optJSONArray("content") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val text = part.optString("text")
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
        }
    }

    private fun mapHttpError(status: Int, payload: String, apiKey: String): String {
        val detail = extractErrorMessage(payload)?.let { redactSecret(it, apiKey) }
        val base = when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                "Неверный или отсутствующий API ключ (401)."
            HttpURLConnection.HTTP_PAYMENT_REQUIRED ->
                "Недостаточно средств или достигнут лимит ключа (402)."
            HttpURLConnection.HTTP_BAD_REQUEST ->
                "Запрос отклонён (400). Возможно, модель не поддерживает изображения " +
                    "или неверный model id."
            429 ->
                "Слишком много запросов, попробуйте позже (429)."
            in 500..599 ->
                "Ошибка на стороне OpenRouter ($status). Попробуйте позже."
            else ->
                "OpenRouter вернул ошибку HTTP $status."
        }
        return if (detail.isNullOrBlank()) base else "$base\n$detail"
    }

    private fun extractErrorMessage(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun redactSecret(text: String, apiKey: String): String =
        if (apiKey.isBlank()) text else text.replace(apiKey, "[ключ скрыт]")
}

/** Carries a user-ready (Russian) message; thrown by [OpenRouterClient]. */
internal class PhotoQuestionException(message: String) : Exception(message)
