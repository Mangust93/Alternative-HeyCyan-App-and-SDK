package com.fersaiyan.cyanbridge.ui.home.v2

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.ui.SettingsActivity
import com.fersaiyan.cyanbridge.ui.tools.ToolsActivity

/**
 * Профиль — settings and system entry points (Module F).
 *
 * Reuses existing screens wherever possible: AI Settings and Models open the existing settings
 * and local-models configuration activities; Diagnostics opens the existing Tools screen;
 * Permissions opens the OS app-details page; About shows app/version info. This screen adds no
 * new settings logic of its own.
 */
class ProfileScreen(private val activity: AppCompatActivity) {

    fun build(): View {
        val ctx = activity
        val pad = ctx.v2dp(16)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        column.addView(ctx.v2ScreenTitle("Профиль"))
        column.addView(ctx.v2Subtitle("Настройки, модели и системные разделы"))

        column.addView(ctx.v2Card(
            title = "AI Settings",
            description = "Провайдеры, агент, память и приватность",
            onClick = { V2Nav.openLocal(activity, SettingsActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "Models",
            description = "Настройка локальных моделей",
            onClick = { V2Nav.openLocal(activity, LocalModelsConfigureActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "Permissions",
            description = "Системные разрешения приложения",
            onClick = { openAppPermissions() },
        ))
        column.addView(ctx.v2Card(
            title = "Diagnostics",
            description = "Инструменты и диагностика",
            onClick = { V2Nav.openLocal(activity, ToolsActivity::class.java) },
        ))
        column.addView(ctx.v2Card(
            title = "About",
            description = "О приложении CyanBridge",
            onClick = { showAbout() },
        ))

        return ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            isFillViewport = true
            addView(column)
        }
    }

    private fun openAppPermissions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", activity.packageName, null))
        V2Nav.openIntent(activity, intent, failMessage = "Не удалось открыть разрешения")
    }

    private fun showAbout() {
        val version = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull() ?: "—"
        AlertDialog.Builder(activity)
            .setTitle("CyanBridge")
            .setMessage(
                "Приложение для умных очков HeyCyan.\n\n" +
                    "Версия: $version",
            )
            .setPositiveButton("Закрыть", null)
            .show()
    }
}
