package com.example.personalledger.domain.repository

import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiCompletionRequest
import com.example.personalledger.domain.ai.AiCompletionResult
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiToolCallingMode
import kotlinx.coroutines.flow.Flow

interface AiAssistantRepository {
    fun observeConnection(): Flow<AiConnectionSettings?>
    fun observeChatHistory(): Flow<List<AiChatMessage>>
    fun isReady(): Boolean
    fun readConnection(): AiConnectionSettings?
    fun statusMessage(): String

    fun toolCallingMode(): AiToolCallingMode

    /** Recent user/assistant chat turns only (no ledger payloads). */
    fun readRecentChatForProvider(limit: Int): List<AiChatMessage>

    /** Multi-turn completion with optional native tools or plain messages. */
    suspend fun complete(request: AiCompletionRequest): AiCompletionResult

    fun appendChatMessage(message: AiChatMessage)

    /** Clears chat history / AI cache only — never clears embedded Jasper build secrets. */
    fun clearChatHistory()

    /** Alias for [clearChatHistory] used by reset use case. */
    fun resetAssistant()
}
