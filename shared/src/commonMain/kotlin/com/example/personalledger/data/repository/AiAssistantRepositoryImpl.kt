package com.example.personalledger.data.repository

import com.example.personalledger.data.ai.AiChatHistoryStore
import com.example.personalledger.data.ai.AiSettingsStore
import com.example.personalledger.data.ai.provider.AiProviderRegistry
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRequest
import com.example.personalledger.domain.ai.AiChatResult
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.domain.nowInstant
import com.example.personalledger.domain.repository.AiAssistantRepository
import kotlinx.coroutines.flow.Flow

class AiAssistantRepositoryImpl(
    private val settingsStore: AiSettingsStore,
    private val historyStore: AiChatHistoryStore,
    private val providers: AiProviderRegistry,
) : AiAssistantRepository {
    override fun observeConnection(): Flow<AiConnectionSettings?> = settingsStore.observeConnection()

    override fun observeChatHistory(): Flow<List<AiChatMessage>> = historyStore.observe()

    override fun isReady(): Boolean = settingsStore.isReady()

    override fun readConnection(): AiConnectionSettings? = settingsStore.readConnection()

    override fun hasApiKey(): Boolean = settingsStore.hasApiKey()

    override fun supportedProviders(): List<AiProviderId> = providers.supportedProviders()

    override suspend fun listModels(providerId: AiProviderId, apiKey: String): List<AiModelInfo> {
        val adapter = providers.requireAdapter(providerId)
        return adapter.listModels(apiKey.trim())
    }

    override suspend fun testAndSaveConnection(
        providerId: AiProviderId,
        modelId: String,
        apiKey: String,
    ): AiConnectionSettings {
        val trimmedKey = apiKey.trim()
        val trimmedModel = modelId.trim()
        if (trimmedKey.isEmpty()) throw AiError.Authentication("کلید API را وارد کنید.")
        if (trimmedModel.isEmpty()) throw AiError.InvalidModel("یک مدل معتبر انتخاب کنید.")

        val adapter = providers.requireAdapter(providerId)
        val models = adapter.listModels(trimmedKey)
        val match = models.firstOrNull { it.id == trimmedModel }
            ?: throw AiError.InvalidModel(
                "مدل انتخاب‌شده در فهرست معتبر این ارائه‌دهنده نیست. دوباره انتخاب کنید.",
            )

        // Save only after successful validation — previous connection remains if we throw above.
        settingsStore.saveValidatedConnection(
            providerId = providerId,
            modelId = match.id,
            apiKey = trimmedKey,
            validatedAtEpochMs = nowInstant().toEpochMilliseconds(),
        )
        return settingsStore.readConnection()
            ?: throw AiError.Provider("ذخیرهٔ اتصال انجام نشد.")
    }

    override suspend fun ask(
        question: String,
        systemPrompt: String,
        userPrompt: String,
    ): AiChatResult {
        val connection = settingsStore.readConnection()
            ?: throw AiError.NotConfigured()
        if (!connection.validated) throw AiError.NotConfigured("اتصال هنوز تأیید نشده است.")
        val apiKey = settingsStore.readApiKey()
            ?: throw AiError.NotConfigured("کلید API یافت نشد.")
        if (connection.modelId.isBlank()) {
            throw AiError.InvalidModel("مدل ذخیره‌شده نامعتبر است. تنظیمات را اصلاح کنید.")
        }
        val adapter = try {
            providers.requireAdapter(connection.providerId)
        } catch (error: AiError) {
            throw error
        }
        val history = historyStore.read()
        return adapter.chat(
            AiChatRequest(
                providerId = connection.providerId,
                modelId = connection.modelId,
                apiKey = apiKey,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                history = history,
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
        historyStore.clear()
        settingsStore.resetAllSecretsAndSettings()
    }
}
