package com.fersaiyan.cyanbridge.automation.runtime

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Executes [AutomationAction]s locally (Module G).
 *
 * Threading contract:
 *  - [executeNetwork] performs blocking HTTP/Webhook I/O and MUST be called off the main thread.
 *  - [executeUi] starts Activities (intent/app/settings/share) and MUST be called on the main
 *    thread (it needs an [Activity] to launch from).
 *  - [isNetwork] lets the caller route an action to the correct path.
 *
 * Every path returns an [AutomationActionResult] instead of throwing, so a malformed action or a
 * missing target degrades to a readable error and never crashes the app.
 *
 * This module owns only these self-contained actions — it does not touch the SDK, BLE, P2P,
 * device-sync, media sync, OpenRouter logic or any V2 AI feature.
 */
object AutomationExecutor {

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val PREVIEW_LIMIT = 600

    /** A user-facing Open Settings preset: a stable [key] mapped to a system settings action. */
    data class SettingsPreset(val key: String, val title: String)

    val settingsPresets: List<SettingsPreset> = listOf(
        SettingsPreset("wifi", "Wi-Fi"),
        SettingsPreset("bluetooth", "Bluetooth"),
        SettingsPreset("vpn", "VPN"),
        SettingsPreset("app", "Это приложение"),
        SettingsPreset("notifications", "Уведомления"),
        SettingsPreset("battery", "Батарея"),
        SettingsPreset("accessibility", "Спец. возможности"),
    )

    fun isNetwork(type: AutomationActionType): Boolean =
        type == AutomationActionType.HTTP_REQUEST || type == AutomationActionType.WEBHOOK_POST

    // --- Network actions (call off the main thread) ------------------------------------------

    fun executeNetwork(action: AutomationAction): AutomationActionResult = when (action.type) {
        AutomationActionType.HTTP_REQUEST -> runHttp(
            method = action.method.ifBlank { "GET" }.uppercase(),
            url = action.url,
            headerLines = action.headers,
            body = action.body,
            title = "HTTP",
        )
        AutomationActionType.WEBHOOK_POST -> runHttp(
            method = "POST",
            url = action.url,
            headerLines = "Content-Type: application/json",
            body = action.body,
            title = "Webhook",
        )
        else -> AutomationActionResult.error("Ошибка", "Действие не является сетевым")
    }

    private fun runHttp(
        method: String,
        url: String,
        headerLines: String,
        body: String,
        title: String,
    ): AutomationActionResult {
        if (url.isBlank()) return AutomationActionResult.error(title, "URL не указан")
        val parsed = runCatching { URL(url) }.getOrNull()
            ?: return AutomationActionResult.error(title, "Некорректный URL")
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            return AutomationActionResult.error(title, "Поддерживаются только http/https")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (parsed.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            parseHeaderLines(headerLines).forEach { (k, v) ->
                connection.setRequestProperty(k, v)
            }

            val sendsBody = method == "POST" || method == "PUT" || method == "DELETE"
            if (sendsBody && body.isNotEmpty()) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val preview = text.take(PREVIEW_LIMIT)

            if (code in 200..399) {
                AutomationActionResult.ok(title, "Успех", statusCode = code, preview = preview)
            } else {
                AutomationActionResult.error(title, "HTTP ошибка", statusCode = code, preview = preview)
            }
        } catch (t: Throwable) {
            AutomationActionResult.error(title, t.message ?: "Сетевая ошибка")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun parseHeaderLines(raw: String): List<Pair<String, String>> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(":") }
            .map { line ->
                val idx = line.indexOf(':')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .filter { it.first.isNotEmpty() }

    // --- UI actions (call on the main thread) ------------------------------------------------

    fun executeUi(activity: Activity, action: AutomationAction): AutomationActionResult =
        when (action.type) {
            AutomationActionType.ANDROID_INTENT -> runIntent(activity, action)
            AutomationActionType.OPEN_APP -> runOpenApp(activity, action.packageName.trim())
            AutomationActionType.OPEN_SETTINGS -> runOpenSettings(activity, action.settingsTarget)
            AutomationActionType.SHARE_TEXT -> runShareText(activity, action.text)
            else -> AutomationActionResult.error("Ошибка", "Действие не открывает экран")
        }

    private fun runIntent(activity: Activity, action: AutomationAction): AutomationActionResult {
        val title = "Android Intent"
        if (action.intentAction.isBlank() && action.dataUri.isBlank()) {
            return AutomationActionResult.error(title, "Укажите action или data URI")
        }
        return try {
            val intent = Intent()
            if (action.intentAction.isNotBlank()) intent.action = action.intentAction
            if (action.packageName.isNotBlank()) intent.setPackage(action.packageName.trim())
            if (action.dataUri.isNotBlank()) intent.data = Uri.parse(action.dataUri.trim())
            parseKeyValueLines(action.extras).forEach { (k, v) -> intent.putExtra(k, v) }

            if (intent.resolveActivity(activity.packageManager) == null) {
                return AutomationActionResult.error(title, "Нет приложения для этого Intent")
            }
            activity.startActivity(intent)
            AutomationActionResult.ok(title, "Intent запущен")
        } catch (t: Throwable) {
            AutomationActionResult.error(title, t.message ?: "Не удалось запустить Intent")
        }
    }

    private fun runOpenApp(activity: Activity, packageName: String): AutomationActionResult {
        val title = "Открыть приложение"
        if (packageName.isBlank()) return AutomationActionResult.error(title, "Не указан пакет")
        val launch = activity.packageManager.getLaunchIntentForPackage(packageName)
            ?: return AutomationActionResult.error(title, "Приложение не найдено: $packageName")
        return try {
            activity.startActivity(launch)
            AutomationActionResult.ok(title, "Открыто: $packageName")
        } catch (t: Throwable) {
            AutomationActionResult.error(title, t.message ?: "Не удалось открыть приложение")
        }
    }

    private fun runOpenSettings(activity: Activity, target: String): AutomationActionResult {
        val title = "Открыть настройки"
        val intent = settingsIntent(activity, target)
            ?: return AutomationActionResult.error(title, "Неизвестный раздел настроек")
        return try {
            activity.startActivity(intent)
            AutomationActionResult.ok(title, "Открыт раздел: $target")
        } catch (t: Throwable) {
            // Fall back to the top-level settings screen if a specific one is unavailable.
            runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                .map { AutomationActionResult.ok(title, "Открыты общие настройки") }
                .getOrElse { AutomationActionResult.error(title, "Не удалось открыть настройки") }
        }
    }

    private fun settingsIntent(activity: Activity, target: String): Intent? {
        val appUri = Uri.fromParts("package", activity.packageName, null)
        return when (target) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "vpn" -> Intent(Settings.ACTION_VPN_SETTINGS)
            "app" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(appUri)
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> null
        }
    }

    private fun runShareText(activity: Activity, text: String): AutomationActionResult {
        val title = "Поделиться текстом"
        if (text.isBlank()) return AutomationActionResult.error(title, "Текст пустой")
        return try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            activity.startActivity(Intent.createChooser(send, "Поделиться"))
            AutomationActionResult.ok(title, "Открыто меню «Поделиться»")
        } catch (t: Throwable) {
            AutomationActionResult.error(title, t.message ?: "Не удалось поделиться")
        }
    }

    private fun parseKeyValueLines(raw: String): List<Pair<String, String>> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains("=") }
            .map { line ->
                val idx = line.indexOf('=')
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .filter { it.first.isNotEmpty() }
}
