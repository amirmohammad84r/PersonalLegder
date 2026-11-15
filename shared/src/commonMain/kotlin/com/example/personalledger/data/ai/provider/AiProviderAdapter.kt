package com.example.personalledger.data.ai.provider

import com.example.personalledger.domain.ai.AiChatRequest
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiCompletionRequest
import com.example.personalledger.domain.ai.AiCompletionResult
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.domain.ai.AiToolCallingMode

interface AiProviderAdapter {
    val providerId: AiProviderId

    val toolCallingMode: AiToolCallingMode

    /** Live catalog when the gateway supports listing with the same key. */
    suspend fun listModels(apiKey: String): List<AiModelInfo>

    suspend fun chat(request: AiChatRequest): AiChatResult

    /** Orchestration entry: optional native tools or plain multi-turn messages. */
    suspend fun complete(request: AiCompletionRequest): AiCompletionResult
}
