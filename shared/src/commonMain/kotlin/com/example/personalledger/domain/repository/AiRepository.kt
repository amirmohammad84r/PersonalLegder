package com.example.personalledger.domain.repository

import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId
import kotlinx.coroutines.flow.Flow

interface AiAssistantRepository {
    fun observeConnection(): Flow<AiConnectionSettings?>
    fun observeChatHistory(): Flow<List<AiChatMessage>>
    fun isReady(): Boolean
    fun readConnection(): AiConnectionSettings?
    fun hasApiKey(): Boolean

    fun supportedProviders(): List<AiProviderId>

    suspend fun listModels(providerId: AiProviderId, apiKey: String): List<AiModelInfo>

    /**
     * Tests auth + that [modelId] exists for the key. Saves only on success.
     * Failed tests must not wipe a previous successful connection.
     */
    suspend fun testAndSaveConnection(
        providerId: AiProviderId,
        modelId: String,
        apiKey: String,
    ): AiConnectionSettings

    suspend fun ask(
        question: String,
        systemPrompt: String,
        userPrompt: String,
    ): AiChatResult

    fun appendChatMessage(message: AiChatMessage)
    fun clearChatHistory()

    /** Clears secure key, provider/model, validation, and chat history. */
    fun resetAssistant()
}
