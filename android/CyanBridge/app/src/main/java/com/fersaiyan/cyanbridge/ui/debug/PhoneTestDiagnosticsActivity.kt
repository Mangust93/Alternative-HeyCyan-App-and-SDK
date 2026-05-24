package com.fersaiyan.cyanbridge.ui.debug

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.ai.transcription.moonshine.MoonshineModelManager
import com.fersaiyan.cyanbridge.automation.AutomationEvent
import com.fersaiyan.cyanbridge.automation.NativeAutomationEngine
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import java.io.File
import java.io.FileOutputStream

/**
 * Read-only manual phone-test checklist for the "no Tasker / no Moonshine" smoke test.
 *
 * Isolated on purpose: it builds its UI in code (no XML/binding), only reads public
 * status helpers, and never touches scan/connect/media/BLE/P2P flow. The single action
 * button reuses the existing [NativeAutomationEngine] fallback with a self-generated
 * JPEG in the app cache, so the real glasses/media path stays untouched.
 *
 * Launch via:
 *   adb shell am start -n com.fersaiyan.cyanbridge/.ui.debug.PhoneTestDiagnosticsActivity
 */
class PhoneTestDiagnosticsActivity : AppCompatActivity() {

    private companion object {
        const val TASKER_PACKAGE_NAME = "net.dinglisch.android.taskerm"
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
            typeface = android.graphics.Typeface.MONOSPACE
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
        val pluginEnabled = CommunityPluginPrefs.isGeminiChatGptImageAutomationEnabled(this)
        val taskerPathActive = taskerInstalled && pluginEnabled
        val nativeFallbackActive = !taskerPathActive
        val moonshineAvailable = MoonshineModelManager.isRuntimeAvailable()

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

    private fun runImageFallbackTest() {
        val imagePath = runCatching { writeTestJpeg() }.getOrNull()
        if (imagePath == null) {
            Toast.makeText(this, "Could not create test image", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "Opening chat with test image…", Toast.LENGTH_SHORT).show()
        NativeAutomationEngine.handle(
            this,
            AutomationEvent.ImageReadyEvent(imagePath = imagePath, sourceTag = "diagnostics_test"),
        )
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
