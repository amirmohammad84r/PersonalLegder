package com.example.personalledger.data.ai

import com.example.personalledger.data.local.PlatformFileStore
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Local-only chat history. Never stores API keys.
 */
class AiChatHistoryStore(
    private val fileStore: PlatformFileStore,
) {
    private val state = MutableStateFlow(load())

    fun observe(): StateFlow<List<AiChatMessage>> = state.asStateFlow()

    fun read(): List<AiChatMessage> = state.value

    fun append(message: AiChatMessage) {
        val next = (state.value + message).takeLast(MAX_MESSAGES)
        persist(next)
    }

    fun replaceAll(messages: List<AiChatMessage>) {
        persist(messages.takeLast(MAX_MESSAGES))
    }

    fun clear() {
        persist(emptyList())
        runCatching { fileStore.writeText(FILE_NAME, "") }
    }

    private fun persist(messages: List<AiChatMessage>) {
        state.value = messages
        val dto = AiChatHistoryDto(
            messages = messages.map {
                AiChatMessageDto(
                    id = it.id,
                    role = it.role.name,
                    content = it.content,
                    createdAtEpochMs = it.createdAtEpochMs,
                )
            },
        )
        fileStore.writeText(FILE_NAME, aiJson.encodeToString(AiChatHistoryDto.serializer(), dto))
    }

    private fun load(): List<AiChatMessage> {
        val raw = fileStore.readText(FILE_NAME)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            aiJson.decodeFromString(AiChatHistoryDto.serializer(), raw).messages.mapNotNull { dto ->
                val role = runCatching { AiChatRole.valueOf(dto.role) }.getOrNull() ?: return@mapNotNull null
                AiChatMessage(
                    id = dto.id,
                    role = role,
                    content = dto.content,
                    createdAtEpochMs = dto.createdAtEpochMs,
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val FILE_NAME = "ai_chat_history.json"
        const val MAX_MESSAGES = 40
    }
}

@Serializable
private data class AiChatHistoryDto(
    val messages: List<AiChatMessageDto> = emptyList(),
)

@Serializable
private data class AiChatMessageDto(
    val id: String,
    val role: String,
    val content: String,
    val createdAtEpochMs: Long,
)
