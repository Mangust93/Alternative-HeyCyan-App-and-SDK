package com.fersaiyan.cyanbridge.ui.home.v2

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.fersaiyan.cyanbridge.MainActivity

/**
 * Navigation helpers shared by the V2 product screens (Module F).
 *
 * Design rule: the V2 UI is *weakly coupled* to features that may live in optional modules.
 * Such features are opened only through a package-scoped Intent action checked with
 * `resolveActivity` first, so a toggled-out module degrades to a Toast instead of crashing —
 * the V2 layer never imports those modules' Activity classes. In-app screens that live in the
 * core app are opened directly by class.
 *
 * None of these helpers touch the SDK, BLE, P2P or device-sync internals. [openDeviceSync]
 * simply opens the existing, working [MainActivity] flow unchanged.
 */
object V2Nav {

    /** Opens the existing, working device sync/search screen ([MainActivity]) unchanged. */
    fun openDeviceSync(activity: Activity) {
        runCatching { activity.startActivity(Intent(activity, MainActivity::class.java)) }
            .onFailure { toast(activity, "Не удалось открыть раздел") }
    }

    /** Opens an in-app Activity that lives in the core app module. */
    fun openLocal(activity: Activity, target: Class<*>) {
        runCatching { activity.startActivity(Intent(activity, target)) }
            .onFailure { toast(activity, "Не удалось открыть раздел") }
    }

    /**
     * Opens a feature screen that may live in an optional feature module, via a package-scoped
     * Intent [action] only. Checks `resolveActivity` first so the call degrades to a friendly
     * Toast when the module is toggled out of the build.
     */
    fun openFeature(activity: Activity, action: String, unavailableMessage: String = "Модуль не включён") {
        val intent = Intent(action)
            .setPackage(activity.packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
        if (!resolves(activity, intent)) {
            toast(activity, unavailableMessage)
            return
        }
        runCatching { activity.startActivity(intent) }
            .onFailure { toast(activity, "Не удалось открыть функцию") }
    }

    /** Fires an arbitrary intent (e.g. a system settings screen), guarded against crashes. */
    fun openIntent(activity: Activity, intent: Intent, failMessage: String = "Не удалось открыть") {
        runCatching { activity.startActivity(intent) }
            .onFailure { toast(activity, failMessage) }
    }

    private fun resolves(activity: Activity, intent: Intent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ) != null
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }

    private fun toast(activity: Activity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
