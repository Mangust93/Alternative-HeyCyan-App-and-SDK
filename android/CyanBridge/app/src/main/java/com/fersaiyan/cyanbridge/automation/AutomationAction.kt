package com.fersaiyan.cyanbridge.automation

sealed class AutomationAction {

    data class OpenChatWithImageAction(
        val imagePath: String,
        val initialPrompt: String = "Tell me about this image",
    ) : AutomationAction()

    object TriggerP2pSyncAction : AutomationAction()
}
