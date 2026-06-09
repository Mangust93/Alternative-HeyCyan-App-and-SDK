package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.ui.home.HomeV2Activity

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isOnboardingCompleted()) {
            // Navigation reframe (Module E): land on the product Home Screen V2 instead of
            // dropping straight into the device sync/diagnostics screen. Device sync is still
            // reachable from Home's "Синхронизация устройств" card (the unchanged MainActivity).
            startActivity(Intent(this, HomeV2Activity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            startActivity(Intent(this, BatteryOptimizationGuideActivity::class.java))
            finish()
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        val prefs = getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false)
    }
}
