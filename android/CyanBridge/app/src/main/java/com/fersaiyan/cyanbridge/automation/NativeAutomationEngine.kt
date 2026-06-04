package com.fersaiyan.cyanbridge.automation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.fersaiyan.cyanbridge.ui.ChatThreadActivity
import java.io.File

object NativeAutomationEngine {

    private const val TAG = "NativeAutomation"

    fun handle(context: Context, event: AutomationEvent) {
        when (event) {
            is AutomationEvent.ImageReadyEvent -> handleImageReady(context, event)
            is AutomationEvent.SyncThresholdReachedEvent -> handleSyncThreshold(context, event)
        }
    }

    private fun handleImageReady(context: Context, event: AutomationEvent.ImageReadyEvent) {
        val file = File(event.imagePath)
        if (!file.exists()) {
            Log.w(TAG, "ImageReadyEvent: file not found: ${event.imagePath}")
            return
        }
        Log.i(TAG, "ImageReadyEvent: opening chat for ${event.imagePath} (source=${event.sourceTag})")
        val intent = Intent(context, ChatThreadActivity::class.java).apply {
            putExtra(ChatThreadActivity.EXTRA_ATTACHED_IMAGE_PATH, event.imagePath)
            putExtra(ChatThreadActivity.EXTRA_INITIAL_PROMPT, "Tell me about this image")
        }
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun handleSyncThreshold(context: Context, event: AutomationEvent.SyncThresholdReachedEvent) {
        Log.i(TAG, "SyncThresholdReachedEvent: loopCount=${event.loopCount} — not yet handled natively")
    }
}
