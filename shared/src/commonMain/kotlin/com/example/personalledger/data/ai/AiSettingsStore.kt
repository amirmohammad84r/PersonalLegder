package com.example.personalledger.data.ai

import com.example.personalledger.data.secure.SecureCredentialKeys
import com.example.personalledger.data.secure.SecureCredentialStore
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiProviderId
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy user-entered provider/key/model storage.
 * Jasper builds use embedded secrets; this store only migrates/clears old device settings.
 * Chat history is separate ([AiChatHistoryStore]).
 */
class AiSettingsStore(
    private val settings: Settings,
    private val secureStore: SecureCredentialStore,
) {
    private val connectionState = MutableStateFlow<AiConnectionSettings?>(null)

    fun observeConnection(): StateFlow<AiConnectionSettings?> = connectionState.asStateFlow()

    fun readConnection(): AiConnectionSettings? = connectionState.value

    fun publishBuiltInConnection(connection: AiConnectionSettings?) {
        connectionState.value = connection
    }

    fun hasLegacyUserApiKey(): Boolean =
        !secureStore.getSecret(SecureCredentialKeys.AI_API_KEY).isNullOrBlank()

    /**
     * Removes user-entered provider/key/model leftovers from earlier app versions.
     * Does not touch ledger data or chat history.
     */
    fun clearLegacyUserConnectionSecrets() {
        secureStore.removeSecret(SecureCredentialKeys.AI_API_KEY)
        settings.remove(KEY_PROVIDER)
        settings.remove(KEY_MODEL)
        settings.remove(KEY_VALIDATED)
        settings.remove(KEY_VALIDATED_AT)
    }

    /** Test helper / legacy — not used by Jasper chat UI. */
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
        connectionState.value = AiConnectionSettings(
            providerId = providerId,
            modelId = modelId.trim(),
            validated = true,
            validatedAtEpochMs = validatedAtEpochMs,
        )
    }

    fun readApiKey(): String? = secureStore.getSecret(SecureCredentialKeys.AI_API_KEY)

    fun isReadyLegacy(): Boolean {
        val connection = connectionState.value ?: return false
        return connection.validated && hasLegacyUserApiKey()
    }

    fun resetAllSecretsAndSettings() {
        clearLegacyUserConnectionSecrets()
        secureStore.clear()
        connectionState.value = null
    }

    private companion object {
        const val KEY_PROVIDER = "ai_provider_id"
        const val KEY_MODEL = "ai_model_id"
        const val KEY_VALIDATED = "ai_connection_validated"
        const val KEY_VALIDATED_AT = "ai_connection_validated_at"
    }
}
