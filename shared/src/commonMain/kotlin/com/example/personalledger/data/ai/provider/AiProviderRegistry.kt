package com.example.personalledger.data.ai.provider

import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiProviderId
import io.ktor.client.HttpClient

class AiProviderRegistry(
    client: HttpClient,
) {
    private val adapters: Map<AiProviderId, AiProviderAdapter> = mapOf(
        AiProviderId.OPENAI to OpenAiCompatibleAdapter(
            client = client,
            providerId = AiProviderId.OPENAI,
            modelsUrl = AiEndpoints.OPENAI_MODELS,
            chatUrl = AiEndpoints.OPENAI_CHAT,
            modelPredicate = OpenAiCompatibleModelFilters::openAiChat,
            emptyAnswerMessage = "پاسخ خالی از OpenAI دریافت شد.",
        ),
        AiProviderId.DEEPSEEK to OpenAiCompatibleAdapter(
            client = client,
            providerId = AiProviderId.DEEPSEEK,
            modelsUrl = AiEndpoints.DEEPSEEK_MODELS,
            chatUrl = AiEndpoints.DEEPSEEK_CHAT,
            modelPredicate = OpenAiCompatibleModelFilters::deepSeekChat,
            emptyAnswerMessage = "پاسخ خالی از DeepSeek دریافت شد.",
        ),
        AiProviderId.GAPGPT to OpenAiCompatibleAdapter(
            client = client,
            providerId = AiProviderId.GAPGPT,
            modelsUrl = AiEndpoints.GAPGPT_MODELS,
            chatUrl = AiEndpoints.GAPGPT_CHAT,
            modelPredicate = OpenAiCompatibleModelFilters::gapGptChat,
            emptyAnswerMessage = "پاسخ خالی از GapGPT دریافت شد.",
        ),
        AiProviderId.GEMINI to GeminiAdapter(client),
    )

    fun supportedProviders(): List<AiProviderId> = AiProviderId.entries

    fun requireAdapter(providerId: AiProviderId): AiProviderAdapter =
        adapters[providerId]
            ?: throw AiError.InvalidConfiguration(
                "ارائه‌دهندهٔ انتخاب‌شده دیگر پشتیبانی نمی‌شود. تنظیمات را اصلاح کنید.",
            )
}
