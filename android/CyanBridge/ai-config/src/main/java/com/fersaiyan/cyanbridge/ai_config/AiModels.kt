package com.fersaiyan.cyanbridge.ai_config

/**
 * The fixed local list of selectable AI models, shared across modules.
 *
 * Deliberately hard-coded — there is no network call to discover models. This is the
 * single source of truth that replaces the per-module list that used to live inside
 * :ai-user-shell's settings store. The first entry ([DEFAULT_MODEL_ID]) is the default.
 *
 * The exact ids and their order are preserved from V1.4 so previously saved selections
 * keep resolving to the same model.
 */
object AiModels {

    /** All selectable models, in display order. The first entry is the default. */
    val OPTIONS: List<AiModelOption> = listOf(
        AiModelOption("google/gemini-2.0-flash-001", "Gemini 2.0 Flash"),
        AiModelOption("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash (preview)"),
        AiModelOption("openai/gpt-4.1-mini", "GPT-4.1 mini"),
        AiModelOption("openai/gpt-4o-mini", "GPT-4o mini"),
        AiModelOption("qwen/qwen2.5-vl-72b-instruct", "Qwen2.5-VL 72B Instruct"),
    )

    /** Convenience: just the model ids, in display order. */
    val MODEL_IDS: List<String> = OPTIONS.map { it.id }

    /** Default model id used when nothing valid is stored. */
    val DEFAULT_MODEL_ID: String = OPTIONS.first().id

    /** True when [modelId] is one of the known fixed models. */
    fun isKnown(modelId: String?): Boolean = modelId != null && modelId in MODEL_IDS
}
