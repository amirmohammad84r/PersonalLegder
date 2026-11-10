package com.example.personalledger.domain.ai

/**
 * Providers with official HTTPS APIs usable from mobile clients.
 * Model catalogs are fetched live with the user's API key when possible.
 */
enum class AiProviderId(
    val storageKey: String,
    val displayName: String,
    /** Short account/API-key guidance shown on the connection form. */
    val accountHint: String,
) {
    OPENAI(
        storageKey = "openai",
        displayName = "OpenAI",
        accountHint = "کلید را از platform.openai.com بسازید و حساب OpenAI فعال داشته باشید.",
    ),
    DEEPSEEK(
        storageKey = "deepseek",
        displayName = "DeepSeek",
        accountHint = "کلید را از platform.deepseek.com بسازید (API سازگار با OpenAI).",
    ),
    GAPGPT(
        storageKey = "gapgpt",
        displayName = "GapGPT",
        accountHint = "کلید را از gapgpt.app/platform-v2/tokens بسازید (API سازگار با OpenAI).",
    ),
    GEMINI(
        storageKey = "gemini",
        displayName = "Google Gemini",
        accountHint = "کلید را از Google AI Studio بسازید.",
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
    /** True when this entry came from the provider's models API. */
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

sealed class AiError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConfigured(message: String = "اتصال دستیار تنظیم نشده است") : AiError(message)
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
