package com.example.personalledger.data.repository

import com.example.personalledger.data.ai.AiChatHistoryStore
import com.example.personalledger.data.ai.AiSettingsStore
import com.example.personalledger.data.ai.jasper.JasperEmbeddedSecrets
import com.example.personalledger.data.ai.provider.AiProviderRegistry
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiCompletionRequest
import com.example.personalledger.domain.ai.AiCompletionResult
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.domain.ai.AiToolCallingMode
import com.example.personalledger.domain.repository.AiAssistantRepository
import kotlinx.coroutines.flow.Flow

class AiAssistantRepositoryImpl(
    private val settingsStore: AiSettingsStore,
    private val historyStore: AiChatHistoryStore,
    private val providers: AiProviderRegistry,
) : AiAssistantRepository {

    init {
        // Drop legacy user-entered keys/providers from earlier builds.
        settingsStore.clearLegacyUserConnectionSecrets()
        settingsStore.publishBuiltInConnection(builtInConnectionOrNull())
    }

    override fun observeConnection(): Flow<AiConnectionSettings?> = settingsStore.observeConnection()

    override fun observeChatHistory(): Flow<List<AiChatMessage>> = historyStore.observe()

    override fun isReady(): Boolean =
        JasperEmbeddedSecrets.isConfigured && JasperEmbeddedSecrets.apiKeyOrNull() != null

    override fun readConnection(): AiConnectionSettings? = builtInConnectionOrNull()

    override fun statusMessage(): String =
        when {
            !JasperEmbeddedSecrets.isConfigured ->
                "دستیار پیکربندی نشده است. برای ساخت، فایل محلی secrets را تکمیل کنید."
            JasperEmbeddedSecrets.apiKeyOrNull() == null ->
                "کلید Jasper در این ساخت موجود نیست."
            else -> "آماده برای گفت‌وگو"
        }

    override fun toolCallingMode(): AiToolCallingMode = providers.toolCallingMode()

    override fun readRecentChatForProvider(limit: Int): List<AiChatMessage> =
        historyStore.read().takeLast(limit.coerceAtLeast(0))

    override suspend fun complete(request: AiCompletionRequest): AiCompletionResult {
        if (!isReady()) throw AiError.NotConfigured(statusMessage())
        val apiKey = JasperEmbeddedSecrets.apiKeyOrNull()
            ?: throw AiError.NotConfigured("کلید Jasper در این ساخت موجود نیست.")
        val modelId = JasperEmbeddedSecrets.modelId
        if (modelId.isBlank()) {
            throw AiError.InvalidModel("شناسهٔ مدل Jasper تنظیم نشده است.")
        }
        val adapter = providers.requireAdapter(AiProviderId.JASPER)
        return adapter.complete(
            request.copy(
                providerId = AiProviderId.JASPER,
                modelId = modelId,
                apiKey = apiKey,
            ),
        )
    }

    override fun appendChatMessage(message: AiChatMessage) {
        historyStore.append(message)
    }

    override fun clearChatHistory() {
        historyStore.clear()
    }

    override fun resetAssistant() {
        // Product rule: reset clears AI history/cache only — not embedded build secrets.
        historyStore.clear()
    }

    private fun builtInConnectionOrNull(): AiConnectionSettings? {
        if (!isReady()) return null
        return AiConnectionSettings(
            providerId = AiProviderId.JASPER,
            modelId = JasperEmbeddedSecrets.modelId,
            validated = true,
            validatedAtEpochMs = null,
        )
    }
}
