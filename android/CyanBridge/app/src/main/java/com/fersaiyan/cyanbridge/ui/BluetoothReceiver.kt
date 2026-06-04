package com.fersaiyan.cyanbridge.ui
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import org.greenrobot.eventbus.EventBus

/**
 * @author hzy ,
 * @date 2020/8/3,
 *
 *
 * "Programs should be written for other people to read,
 * and only incidentally for machines to execute"
 */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val connectState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (connectState == BluetoothAdapter.STATE_OFF) {
                    Log.i("qc" ,"Bluetooth is off --> ")
                    BleOperateManager.getInstance().setBluetoothTurnOff(false)
                    BleOperateManager.getInstance().disconnect()
                    EventBus.getDefault().post(BluetoothEvent(false))
                } else if (connectState == BluetoothAdapter.STATE_ON) {
                    Log.i("qc" ,"Bluetooth is on --> ")
                    BleOperateManager.getInstance().setBluetoothTurnOff(true)

                    // Route through AutoPairManager so user-initiated disconnect suppression is respected.
                    AutoPairManager.requestConnect(context, reason = "bt_state_on")
                }
            }
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {

            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                // If the phone connects to the glasses over classic BT (audio),
                // opportunistically (re)connect the BLE control channel too.
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    val saved = DeviceManager.getInstance().deviceAddress
                    val name = try { device.name } catch (_: SecurityException) { null }
                    val looksLikeGlasses = name?.contains("HeyCyan", ignoreCase = true) == true ||
                        name?.contains("Cyan", ignoreCase = true) == true ||
                        name?.startsWith("O_") == true ||
                        name?.startsWith("Q_") == true

                    if (!saved.isNullOrBlank() && saved.equals(device.address, ignoreCase = true)) {
                        AutoPairManager.requestConnectToMac(context, device.address, reason = "acl_connected_saved")
                    } else if (looksLikeGlasses) {
                        AutoPairManager.requestConnectToMac(context, device.address, reason = "acl_connected_name")
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                // BLE reconnect loop will handle this; just trigger an immediate attempt.
                AutoPairManager.requestConnect(context, reason = "acl_disconnected")
            }

            BluetoothDevice.ACTION_FOUND -> {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    // Only attempt pairing for the known glasses device.
                    val saved = DeviceManager.getInstance().deviceAddress
                    if (!saved.isNullOrBlank() && saved.equals(device.address, ignoreCase = true)) {
                        if (device.bondState != BluetoothDevice.BOND_BONDED) {
                            BleOperateManager.getInstance().createBondBluetoothJieLi(device)
                        }
                    }
                }
            }
        }
    }

}

/**
 * Compile-safe holder for auto-reconnect suppression state.
 *
 * Automatic BLE reconnect is referenced by callers without an implementation in this
 * source tree. Keep user disconnect intent persistent while leaving scan/connect behavior
 * untouched until a concrete reconnect implementation is available.
 */
object AutoPairManager {
    private const val TAG = "AutoPairManager"
    private const val PREFS_NAME = "auto_pair_manager"
    private const val KEY_SUPPRESSED = "auto_reconnect_suppressed"
    private const val KEY_REASON = "auto_reconnect_suppression_reason"

    fun start(context: Context) {
        Log.i(TAG, "Auto reconnect unavailable; suppression=${isAutoReconnectSuppressed(context)}")
    }

    fun setAutoReconnectSuppressed(suppressed: Boolean, reason: String? = null) {
        setAutoReconnectSuppressed(MyApplication.CONTEXT, suppressed, reason)
    }

    fun isAutoReconnectSuppressed(): Boolean {
        return isAutoReconnectSuppressed(MyApplication.CONTEXT)
    }

    fun getLastSuppressionReason(): String? {
        return getLastSuppressionReason(MyApplication.CONTEXT)
    }

    fun clearAutoReconnectSuppression() {
        clearAutoReconnectSuppression(MyApplication.CONTEXT)
    }

    fun setAutoReconnectSuppressed(context: Context, suppressed: Boolean, reason: String? = null) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SUPPRESSED, suppressed)
            .apply {
                if (reason.isNullOrBlank()) {
                    remove(KEY_REASON)
                } else {
                    putString(KEY_REASON, reason)
                }
            }
            .apply()
    }

    fun isAutoReconnectSuppressed(context: Context): Boolean {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SUPPRESSED, false)
    }

    fun getLastSuppressionReason(context: Context): String? {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REASON, null)
    }

    fun clearAutoReconnectSuppression(context: Context) {
        setAutoReconnectSuppressed(context, suppressed = false)
    }

    fun requestConnect(context: Context, reason: String) {
        logUnavailableRequest(context, reason, macAddress = null)
    }

    fun requestConnectToMac(context: Context, macAddress: String, reason: String) {
        logUnavailableRequest(context, reason, macAddress)
    }

    private fun logUnavailableRequest(context: Context, reason: String, macAddress: String?) {
        if (isAutoReconnectSuppressed(context)) {
            Log.i(TAG, "Skipping reconnect request ($reason): user suppression active")
            return
        }
        val target = macAddress?.let { " target=$it" }.orEmpty()
        Log.w(TAG, "Ignoring reconnect request ($reason): auto reconnect unavailable$target")
    }
}
