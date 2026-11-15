package com.example.personalledger.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.repository.AiAssistantRepository
import com.example.personalledger.domain.usecase.AskAiAssistantUseCase
import com.example.personalledger.domain.usecase.ResetAiAssistantUseCase
import com.example.personalledger.presentation.strings.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSessionState(
    val draft: String = "",
    val asking: Boolean = false,
    val chatError: String? = null,
    val showResetConfirm: Boolean = false,
    val resetError: String? = null,
)

data class AiUiState(
    val loading: Boolean = true,
    val ready: Boolean = false,
    val statusMessage: String = "",
    val connection: AiConnectionSettings? = null,
    val messages: List<AiChatMessage> = emptyList(),
    val draft: String = "",
    val asking: Boolean = false,
    val chatError: String? = null,
    val showResetConfirm: Boolean = false,
    val resetError: String? = null,
)

class AiViewModel(
    private val aiRepository: AiAssistantRepository,
    private val askAi: AskAiAssistantUseCase,
    private val resetAi: ResetAiAssistantUseCase,
    private val displayPreferences: DisplayPreferences,
) : ViewModel() {
    private val sessionState = MutableStateFlow(AiSessionState())
    private var askJob: Job? = null

    val uiState: StateFlow<AiUiState> = combine(
        aiRepository.observeConnection(),
        aiRepository.observeChatHistory(),
        sessionState,
    ) { connection, messages, session ->
        AiUiState(
            loading = false,
            ready = aiRepository.isReady(),
            statusMessage = aiRepository.statusMessage(),
            connection = connection,
            messages = messages,
            draft = session.draft,
            asking = session.asking,
            chatError = session.chatError,
            showResetConfirm = session.showResetConfirm,
            resetError = session.resetError,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiUiState(loading = true),
    )

    fun updateDraft(value: String) {
        sessionState.update { it.copy(draft = value) }
    }

    fun sendQuestion() {
        val question = sessionState.value.draft.trim()
        if (question.isEmpty() || sessionState.value.asking) return
        if (!aiRepository.isReady()) {
            sessionState.update {
                it.copy(chatError = aiRepository.statusMessage())
            }
            return
        }
        askJob?.cancel()
        askJob = viewModelScope.launch {
            sessionState.update { it.copy(asking = true, chatError = null, draft = "") }
            try {
                askAi(question, displayPreferences.readCalendar())
            } catch (cancelled: CancellationException) {
                sessionState.update { it.copy(chatError = Strings.aiCancelled) }
                throw cancelled
            } catch (error: Throwable) {
                sessionState.update { it.copy(chatError = userMessage(error)) }
            } finally {
                sessionState.update { it.copy(asking = false) }
            }
        }
    }

    fun cancelAsk() {
        askJob?.cancel()
        askJob = null
        sessionState.update { it.copy(asking = false, chatError = Strings.aiCancelled) }
    }

    fun clearChatError() {
        sessionState.update { it.copy(chatError = null) }
    }

    fun requestReset() {
        sessionState.update { it.copy(showResetConfirm = true) }
    }

    fun dismissReset() {
        sessionState.update { it.copy(showResetConfirm = false) }
    }

    fun confirmReset() {
        viewModelScope.launch {
            try {
                resetAi()
                sessionState.value = AiSessionState()
            } catch (error: Throwable) {
                sessionState.update {
                    it.copy(
                        showResetConfirm = false,
                        resetError = userMessage(error),
                    )
                }
            }
        }
    }

    private fun userMessage(error: Throwable): String =
        when (error) {
            is AiError.Authentication ->
                error.message ?: "احراز هویت ناموفق بود. اتصال Jasper را بررسی کنید."
            is AiError -> error.message ?: Strings.errorGeneric
            else -> Strings.errorGeneric
        }
}
