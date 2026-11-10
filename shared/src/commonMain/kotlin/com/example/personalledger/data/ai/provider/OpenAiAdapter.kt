package com.example.personalledger.data.ai.provider

/**
 * Backward-compatible helpers for OpenAI chat model filtering used by tests.
 * Runtime OpenAI traffic goes through [OpenAiCompatibleAdapter].
 */
object OpenAiAdapter {
    fun isChatCapableModel(id: String): Boolean =
        OpenAiCompatibleModelFilters.isOpenAiChatCapable(id)
}
