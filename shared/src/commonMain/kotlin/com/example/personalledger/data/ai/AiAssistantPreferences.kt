package com.example.personalledger.data.ai

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getBooleanFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AI-specific prefs. History is NOT sent to Jasper unless the user consents here
 * and planning explicitly requests recent chat context.
 */
class AiAssistantPreferences(
    private val settings: Settings,
) {
    private val historyConsentKey = "ai_chat_history_consent"

    fun readHistoryConsent(): Boolean =
        settings.getBoolean(historyConsentKey, false)

    fun writeHistoryConsent(enabled: Boolean) {
        settings.putBoolean(historyConsentKey, enabled)
    }

    @OptIn(ExperimentalSettingsApi::class)
    fun observeHistoryConsent(): Flow<Boolean> {
        val observable = settings as? ObservableSettings
            ?: return flow { emit(readHistoryConsent()) }
        return observable.getBooleanFlow(historyConsentKey, false)
    }
}
