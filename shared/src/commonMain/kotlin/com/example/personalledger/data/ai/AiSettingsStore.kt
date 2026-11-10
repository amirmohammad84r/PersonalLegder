package com.example.personalledger.data.ai

import com.example.personalledger.data.secure.SecureCredentialKeys
import com.example.personalledger.data.secure.SecureCredentialStore
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiProviderId
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Non-secret AI connection metadata in multiplatform-settings.
 * API key lives only in [SecureCredentialStore].
 */
class AiSettingsStore(
    private val settings: Settings,
    private val secureStore: SecureCredentialStore,
) {
    private val connectionState = MutableStateFlow(readConnectionFromDisk())

    fun observeConnection(): StateFlow<AiConnectionSettings?> = connectionState.asStateFlow()

    fun readConnection(): AiConnectionSettings? = connectionState.value ?: readConnectionFromDisk()

    fun hasApiKey(): Boolean = !secureStore.getSecret(SecureCredentialKeys.AI_API_KEY).isNullOrBlank()

    fun readApiKey(): String? = secureStore.getSecret(SecureCredentialKeys.AI_API_KEY)

    fun isReady(): Boolean {
        val connection = readConnection() ?: return false
        return connection.validated && hasApiKey()
    }

    /**
     * Persists a newly verified connection. Replaces previous key/settings only after success.
     */
    fun saveValidatedConnection(
        providerId: AiProviderId,
        modelId: String,
        apiKey: String,
        validatedAtEpochMs: Long,
    ) {
        require(modelId.isNotBlank()) { "modelId blank" }
        require(apiKey.isNotBlank()) { "apiKey blank" }
        secureStore.putSecret(SecureCredentialKeys.AI_API_KEY, apiKey)
        settings.putString(KEY_PROVIDER, providerId.storageKey)
        settings.putString(KEY_MODEL, modelId.trim())
        settings.putBoolean(KEY_VALIDATED, true)
        settings.putLong(KEY_VALIDATED_AT, validatedAtEpochMs)
        connectionState.value = readConnectionFromDisk()
    }

    fun clearConnection() {
        secureStore.removeSecret(SecureCredentialKeys.AI_API_KEY)
        settings.remove(KEY_PROVIDER)
        settings.remove(KEY_MODEL)
        settings.remove(KEY_VALIDATED)
        settings.remove(KEY_VALIDATED_AT)
        connectionState.value = null
    }

    /**
     * Full AI reset including secure key. Throws [AiError.ResetIncomplete] if remnants remain.
     */
    fun resetAllSecretsAndSettings() {
        clearConnection()
        secureStore.clear()
        val leftoverKey = secureStore.getSecret(SecureCredentialKeys.AI_API_KEY)
        val leftoverProvider = settings.getStringOrNull(KEY_PROVIDER)
        if (!leftoverKey.isNullOrBlank() || !leftoverProvider.isNullOrBlank()) {
            throw AiError.ResetIncomplete("پاک‌سازی اتصال کامل نشد. دوباره تلاش کنید.")
        }
        connectionState.value = null
    }

    private fun readConnectionFromDisk(): AiConnectionSettings? {
        val provider = AiProviderId.fromStorage(settings.getStringOrNull(KEY_PROVIDER)) ?: return null
        val modelId = settings.getStringOrNull(KEY_MODEL)?.trim().orEmpty()
        if (modelId.isEmpty()) return null
        val validated = settings.getBoolean(KEY_VALIDATED, false)
        val validatedAt = if (settings.hasKey(KEY_VALIDATED_AT)) {
            settings.getLong(KEY_VALIDATED_AT, 0L)
        } else {
            null
        }
        return AiConnectionSettings(
            providerId = provider,
            modelId = modelId,
            validated = validated,
            validatedAtEpochMs = validatedAt,
        )
    }

    private companion object {
        const val KEY_PROVIDER = "ai_provider_id"
        const val KEY_MODEL = "ai_model_id"
        const val KEY_VALIDATED = "ai_connection_validated"
        const val KEY_VALIDATED_AT = "ai_connection_validated_at"
    }
}
