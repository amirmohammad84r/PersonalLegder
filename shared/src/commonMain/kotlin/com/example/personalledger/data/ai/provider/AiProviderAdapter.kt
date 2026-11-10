package com.example.personalledger.data.ai.provider

import com.example.personalledger.domain.ai.AiChatRequest
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId

interface AiProviderAdapter {
    val providerId: AiProviderId

    /** Live catalog when the official API supports listing with the same key. */
    suspend fun listModels(apiKey: String): List<AiModelInfo>

    suspend fun chat(request: AiChatRequest): AiChatResult
}

/**
 * Central HTTPS endpoints from official provider docs.
 * Keep provider-specific URLs here so adapters stay thin.
 */
object AiEndpoints {
    // OpenAI: https://platform.openai.com/docs/api-reference
    const val OPENAI_BASE = "https://api.openai.com/v1"
    const val OPENAI_MODELS = "$OPENAI_BASE/models"
    const val OPENAI_CHAT = "$OPENAI_BASE/chat/completions"

    // DeepSeek OpenAI-compatible: https://api-docs.deepseek.com/
    const val DEEPSEEK_BASE = "https://api.deepseek.com"
    const val DEEPSEEK_MODELS = "$DEEPSEEK_BASE/models"
    const val DEEPSEEK_CHAT = "$DEEPSEEK_BASE/chat/completions"

    // GapGPT OpenAI-compatible: https://gapgpt.app/llms.txt / platform-v2 docs
    const val GAPGPT_BASE = "https://api.gapgpt.app/v1"
    const val GAPGPT_MODELS = "$GAPGPT_BASE/models"
    const val GAPGPT_CHAT = "$GAPGPT_BASE/chat/completions"

    // Google Gemini: https://ai.google.dev/api
    const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val GEMINI_MODELS = "$GEMINI_BASE/models"

    fun geminiGenerateContent(modelId: String): String =
        "$GEMINI_BASE/models/${normalizeGeminiModelId(modelId)}:generateContent"

    fun normalizeGeminiModelId(modelId: String): String =
        modelId.removePrefix("models/").trim()
}
