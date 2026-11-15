package com.example.personalledger.domain.ai

/**
 * Built-in AI gateway. End-users do not pick a provider; the app talks to Jasper only.
 */
enum class AiProviderId(
    val storageKey: String,
    val displayName: String,
) {
    JASPER(
        storageKey = "jasper",
        displayName = "Jasper",
    ),
    ;

    companion object {
        fun fromStorage(raw: String?): AiProviderId? =
            entries.firstOrNull { it.storageKey == raw || it.name.equals(raw, ignoreCase = true) }
    }
}

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val description: String? = null,
    val fromLiveCatalog: Boolean,
)

data class AiConnectionSettings(
    val providerId: AiProviderId,
    val modelId: String,
    val validated: Boolean,
    val validatedAtEpochMs: Long?,
)

data class AiChatMessage(
    val id: String,
    val role: AiChatRole,
    val content: String,
    val createdAtEpochMs: Long,
)

enum class AiChatRole {
    USER,
    ASSISTANT,
}

data class AiChatRequest(
    val providerId: AiProviderId,
    val modelId: String,
    val apiKey: String,
    val systemPrompt: String,
    val userPrompt: String,
    val history: List<AiChatMessage> = emptyList(),
)

data class AiChatResult(
    val text: String,
)

/**
 * How this app talks to a provider for tool orchestration.
 */
enum class AiToolCallingMode {
    /** OpenAI-compatible tools + tool_calls (used for Jasper when path is OpenAI-compatible). */
    OPENAI_COMPATIBLE_TOOLS,
    /** Strict JSON plan validated in-app (never executed raw). */
    STRUCTURED_JSON_PLAN,
}

data class AiToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: kotlinx.serialization.json.JsonObject,
)

sealed class AiCompletionMessage {
    data class System(val content: String) : AiCompletionMessage()
    data class User(val content: String) : AiCompletionMessage()
    data class Assistant(
        val content: String?,
        val toolCalls: List<AiProposedToolCall> = emptyList(),
    ) : AiCompletionMessage()
    data class ToolResult(
        val toolCallId: String,
        val content: String,
    ) : AiCompletionMessage()
}

data class AiProposedToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class AiCompletionRequest(
    val providerId: AiProviderId,
    val modelId: String,
    val apiKey: String,
    val messages: List<AiCompletionMessage>,
    val tools: List<AiToolDefinition>? = null,
    val enableTools: Boolean = false,
    val disableThinkingForTools: Boolean = false,
)

data class AiCompletionResult(
    val text: String?,
    val toolCalls: List<AiProposedToolCall> = emptyList(),
    val rawAssistantContent: String? = null,
)

sealed class AiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured(message: String = "دستیار هوشمند پیکربندی نشده است") : AiError(message)
    class InvalidConfiguration(message: String) : AiError(message)
    class Authentication(message: String) : AiError(message)
    class RateLimited(message: String) : AiError(message)
    class QuotaExceeded(message: String) : AiError(message)
    class InvalidModel(message: String) : AiError(message)
    class Timeout(message: String = "زمان پاسخ‌گویی به پایان رسید") : AiError(message)
    class Network(message: String = "اتصال شبکه برقرار نشد", cause: Throwable? = null) :
        AiError(message, cause)
    class Cancelled(message: String = "درخواست لغو شد") : AiError(message)
    class Provider(message: String) : AiError(message)
    class ResetIncomplete(message: String) : AiError(message)
}
