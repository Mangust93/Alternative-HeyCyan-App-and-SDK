package com.fersaiyan.cyanbridge.ui

import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

/**
 * Module C — Permission Guard for device search.
 *
 * Makes the "Поиск устройств" flow safe: device search is only ever started
 * when the Android runtime permissions required for BLE scanning are present,
 * so a missing permission can no longer crash the scan with a SecurityException.
 *
 * This guard intentionally only gates *starting* the search. It does not touch
 * the SDK, the BLE protocol, or any existing search/connect logic.
 */
object DeviceSearchPermissionGuard {

    private const val MSG_TITLE = "Не хватает разрешений для поиска очков"
    private const val MSG_DETAIL = "Разрешите Bluetooth / Геолокацию и повторите поиск"

    /**
     * Version-aware list of runtime permissions required to scan for glasses.
     *
     * Android 12+ (S) uses the nearby-devices model and needs BLUETOOTH_SCAN /
     * BLUETOOTH_CONNECT. On older Android, BLE scanning is gated behind location,
     * so we preserve the existing location requirement there.
     */
    fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Permission.BLUETOOTH_SCAN, Permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Permission.ACCESS_FINE_LOCATION)
        }
    }

    /** True when every permission required to start a device search is granted. */
    fun hasAll(activity: FragmentActivity): Boolean {
        return XXPermissions.isGranted(activity, requiredPermissions())
    }

    /**
     * Runs [onGranted] only when all required permissions are present.
     *
     * If permissions are missing they are requested at runtime. If the user
     * denies them, the search is NOT started and a clear Russian message is
     * shown; when the denial is permanent we also offer a path to the system
     * app-settings so the user can enable them manually.
     */
    fun ensure(activity: FragmentActivity, onGranted: () -> Unit) {
        if (hasAll(activity)) {
            onGranted()
            return
        }

        val request = XXPermissions.with(activity)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            request.permission(Permission.BLUETOOTH_SCAN)
            request.permission(Permission.BLUETOOTH_CONNECT)
        } else {
            request.permission(Permission.ACCESS_FINE_LOCATION)
        }
        request.request(object : OnPermissionCallback {
            override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                if (all) {
                    onGranted()
                } else {
                    // Partial grant: not enough to scan safely, so do not start.
                    showMissing(activity, permissions, never = false)
                }
            }

            override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                showMissing(activity, permissions, never)
            }
        })
    }

    private fun showMissing(
        activity: FragmentActivity,
        permissions: MutableList<String>,
        never: Boolean
    ) {
        Toast.makeText(activity, MSG_TITLE, Toast.LENGTH_LONG).show()
        if (never) {
            // Permanently denied: guide the user to the app settings screen.
            AlertDialog.Builder(activity)
                .setTitle(MSG_TITLE)
                .setMessage("$MSG_DETAIL\n\nОткройте настройки приложения и включите разрешения вручную.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    XXPermissions.startPermissionActivity(activity, permissions)
                }
                .setNegativeButton("Отмена", null)
                .show()
        } else {
            Toast.makeText(activity, MSG_DETAIL, Toast.LENGTH_LONG).show()
        }
    }
}
