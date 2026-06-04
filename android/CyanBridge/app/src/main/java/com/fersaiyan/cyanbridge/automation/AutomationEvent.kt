package com.fersaiyan.cyanbridge.automation

sealed class AutomationEvent {

    data class ImageReadyEvent(
        val imagePath: String,
        val sourceTag: String,
    ) : AutomationEvent()

    data class SyncThresholdReachedEvent(
        val loopCount: Int,
    ) : AutomationEvent()
}
