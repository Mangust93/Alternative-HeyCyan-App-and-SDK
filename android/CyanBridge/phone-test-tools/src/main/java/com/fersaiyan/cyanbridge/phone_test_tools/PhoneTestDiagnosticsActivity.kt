package com.fersaiyan.cyanbridge.phone_test_tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only manual phone-test checklist for the "no Tasker / no Moonshine" smoke test.
 *
 * Lives in the optional, standalone :phone-test-tools module. It deliberately has NO
 * compile dependency on :app: it builds its UI in code (no XML/binding), reads only
 * public package/SharedPreferences state, and never touches scan/connect/media/BLE/P2P
 * flow or the Moonshine runtime.
 *
 * The single action button opens the chat screen through an explicit [Intent]
 * (by class name) instead of calling app-internal automation code directly, so there
 * is no module -> app cycle and the real glasses/media path stays untouched. If the
 * chat activity cannot be resolved (e.g. it was renamed), it falls back to an on-screen
 * instruction toast.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/com.fersaiyan.cyanbridge.phone_test_tools.PhoneTestDiagnosticsActivity
 */
class PhoneTestDiagnosticsActivity : AppCompatActivity() {

    private companion object {
        const val TASKER_PACKAGE_NAME = "net.dinglisch.android.taskerm"

        // Mirrors of app-side identifiers, inlined to avoid a module -> app dependency.
        // Kept in sync with CommunityPluginPrefs / ChatThreadActivity / MoonshineModelManager.
        const val COMMUNITY_PLUGINS_PREFS = "community_plugins"
        const val KEY_GEMINI_CHATGPT_IMAGE_AUTOMATION = "gemini_chatgpt_image_automation"

        const val CHAT_THREAD_ACTIVITY_CLASS = "com.fersaiyan.cyanbridge.ui.ChatThreadActivity"
        const val EXTRA_ATTACHED_IMAGE_PATH = "attached_image_path"
        const val EXTRA_INITIAL_PROMPT = "initial_prompt"

        const val MOONSHINE_MANAGER_CLASS =
            "com.fersaiyan.cyanbridge.ai.transcription.moonshine.MoonshineModelManager"
    }

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Phone Test / Debug"

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Phone Test / Debug checklist"
            textSize = 20f
            setPadding(0, 0, 0, pad)
        })

        statusView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 13f
            setTextIsSelectable(true)
        }
        root.addView(statusView)

        root.addView(TextView(this).apply {
            text = "Note: real glasses / media test still happens through the existing " +
                "scan → connect → photo/video/audio sync flow. This screen only checks " +
                "the Tasker-free / Moonshine-free fallback state."
            textSize = 12f
            setPadding(0, pad, 0, pad)
        })

        root.addView(Button(this).apply {
            text = "Open chat image fallback test"
            setOnClickListener { runImageFallbackTest() }
        })

        root.addView(Button(this).apply {
            text = "Refresh status"
            setOnClickListener { refreshStatus() }
        })

        val scroll = ScrollView(this).apply {
            addView(root)
            setPadding(0, 0, 0, 0)
        }
        setContentView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
        ))

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        statusView.text = buildStatusReport()
    }

    private fun buildStatusReport(): String {
        val pkgInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val versionName = pkgInfo?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = pkgInfo?.versionCode ?: -1
        val debuggable =
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val taskerInstalled = runCatching {
            packageManager.getPackageInfo(TASKER_PACKAGE_NAME, 0); true
        }.getOrDefault(false)
        val pluginEnabled = isGeminiChatGptImageAutomationEnabled()
        val taskerPathActive = taskerInstalled && pluginEnabled
        val nativeFallbackActive = !taskerPathActive
        val moonshineAvailable = isMoonshineRuntimeAvailable()

        fun yn(value: Boolean) = if (value) "YES" else "NO"

        return buildString {
            appendLine("[APK BUILD]")
            appendLine("  package      : $packageName")
            appendLine("  versionName  : $versionName")
            appendLine("  versionCode  : $versionCode")
            appendLine("  debuggable   : ${yn(debuggable)}")
            appendLine()
            appendLine("[TASKER]")
            appendLine("  installed    : ${yn(taskerInstalled)}")
            appendLine("  plugin on    : ${yn(pluginEnabled)}")
            appendLine("  Tasker path  : ${if (taskerPathActive) "ACTIVE" else "inactive"}")
            appendLine()
            appendLine("[NATIVE AUTOMATION FALLBACK]")
            appendLine("  status       : ${if (nativeFallbackActive) "ACTIVE (Tasker-free)" else "standby (Tasker handles image queries)"}")
            appendLine("  routes image → ChatThreadActivity with attached photo + prompt")
            appendLine()
            appendLine("[MOONSHINE RUNTIME]")
            appendLine("  available    : ${yn(moonshineAvailable)}")
            appendLine("  status       : ${if (moonshineAvailable) "present" else "UNAVAILABLE (expected for this build)"}")
        }
    }

    /**
     * Reads the same SharedPreferences entry that the app's CommunityPluginPrefs writes.
     * Same process + same applicationId means this is the same prefs file; we avoid a
     * compile dependency by inlining the (stable) prefs/key names.
     */
    private fun isGeminiChatGptImageAutomationEnabled(): Boolean {
        return getSharedPreferences(COMMUNITY_PLUGINS_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_GEMINI_CHATGPT_IMAGE_AUTOMATION, false)
    }

    /**
     * Best-effort, dependency-free probe of the app's Moonshine availability flag via
     * reflection. The module must not link the Moonshine runtime, so if the class or
     * method is absent we simply report "unavailable" (the expected state for this build).
     */
    private fun isMoonshineRuntimeAvailable(): Boolean {
        return runCatching {
            val cls = Class.forName(MOONSHINE_MANAGER_CLASS)
            val instance = cls.getField("INSTANCE").get(null)
            cls.getMethod("isRuntimeAvailable").invoke(instance) as Boolean
        }.getOrDefault(false)
    }

    private fun runImageFallbackTest() {
        val imagePath = runCatching { writeTestJpeg() }.getOrNull()
        if (imagePath == null) {
            Toast.makeText(this, "Could not create test image", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent().apply {
            setClassName(packageName, CHAT_THREAD_ACTIVITY_CLASS)
            putExtra(EXTRA_ATTACHED_IMAGE_PATH, imagePath)
            putExtra(EXTRA_INITIAL_PROMPT, "Tell me about this image")
        }
        try {
            Toast.makeText(this, "Opening chat with test image…", Toast.LENGTH_SHORT).show()
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Chat screen not found. Manual check: open the app, start a chat, attach " +
                    "$imagePath and send \"Tell me about this image\".",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /** Generates a small JPEG in the app's own cache dir (no runtime permission required). */
    private fun writeTestJpeg(): String {
        val bitmap = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0, 96, 128))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Fallback test", 240f, 250f, paint)
        val file = File(cacheDir, "phone_test_fallback.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        bitmap.recycle()
        return file.absolutePath
    }
}
