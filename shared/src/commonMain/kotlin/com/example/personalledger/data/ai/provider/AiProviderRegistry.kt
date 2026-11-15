package com.example.personalledger.data.ai.provider

import com.example.personalledger.data.ai.jasper.JasperEmbeddedSecrets
import com.example.personalledger.data.ai.jasper.JasperEndpoints
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.domain.ai.AiToolCallingMode
import io.ktor.client.HttpClient

/**
 * Single built-in gateway: Jasper at [JasperEndpoints.BASE_URL].
 * Legacy OpenAI / DeepSeek / GapGPT / Gemini user-selected providers are removed from the product.
 */
class AiProviderRegistry(
    client: HttpClient,
) {
    private val jasperAdapter: AiProviderAdapter = OpenAiCompatibleAdapter(
        client = client,
        providerId = AiProviderId.JASPER,
        modelsUrl = JasperEndpoints.modelsUrl(JasperEmbeddedSecrets.modelsPath),
        chatUrl = JasperEndpoints.chatUrl(JasperEmbeddedSecrets.chatPath),
        modelPredicate = { true },
        emptyAnswerMessage = "پاسخ خالی از Jasper دریافت شد.",
        // Jasper exposes an OpenAI-compatible chat surface; tool_calls follow that shape when enabled.
        toolCallingMode = AiToolCallingMode.OPENAI_COMPATIBLE_TOOLS,
    )

    fun supportedProviders(): List<AiProviderId> = listOf(AiProviderId.JASPER)

    fun toolCallingMode(providerId: AiProviderId = AiProviderId.JASPER): AiToolCallingMode =
        requireAdapter(providerId).toolCallingMode

    fun requireAdapter(providerId: AiProviderId = AiProviderId.JASPER): AiProviderAdapter {
        if (providerId != AiProviderId.JASPER) {
            throw AiError.InvalidConfiguration(
                "فقط درگاه Jasper پشتیبانی می‌شود.",
            )
        }
        return jasperAdapter
    }
}
