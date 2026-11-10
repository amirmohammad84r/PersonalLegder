package com.example.personalledger.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiConnectionSettings
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiModelInfo
import com.example.personalledger.domain.ai.AiProviderId
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

enum class AiPane {
    SETUP,
    CHAT,
}

enum class ConnectionTestStatus {
    IDLE,
    TESTING,
    SUCCESS,
    ERROR,
}

data class AiSetupFormState(
    val providerId: AiProviderId = AiProviderId.OPENAI,
    val modelId: String = "",
    val modelQuery: String = "",
    val apiKeyInput: String = "",
    val apiKeyVisible: Boolean = false,
    val models: List<AiModelInfo> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val modelsLive: Boolean = false,
    val showModelPicker: Boolean = false,
    val testStatus: ConnectionTestStatus = ConnectionTestStatus.IDLE,
    val testMessage: String? = null,
    val fieldError: String? = null,
) {
    val selectedModel: AiModelInfo?
        get() = models.firstOrNull { it.id == modelId }

    val filteredModels: List<AiModelInfo>
        get() {
            val q = modelQuery.trim()
            if (q.isEmpty()) return models
            return models.filter {
                it.id.contains(q, ignoreCase = true) ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    (it.description?.contains(q, ignoreCase = true) == true)
            }
        }
}

data class AiSessionState(
    val setup: AiSetupFormState = AiSetupFormState(),
    val draft: String = "",
    val asking: Boolean = false,
    val chatError: String? = null,
    val showResetConfirm: Boolean = false,
    val resetError: String? = null,
    val forceSetup: Boolean = false,
)

data class AiUiState(
    val loading: Boolean = true,
    val pane: AiPane = AiPane.SETUP,
    val connection: AiConnectionSettings? = null,
    val setup: AiSetupFormState = AiSetupFormState(),
    val messages: List<AiChatMessage> = emptyList(),
    val draft: String = "",
    val asking: Boolean = false,
    val chatError: String? = null,
    val showResetConfirm: Boolean = false,
    val resetError: String? = null,
    val supportedProviders: List<AiProviderId> = AiProviderId.entries,
)

class AiViewModel(
    private val aiRepository: AiAssistantRepository,
    private val askAi: AskAiAssistantUseCase,
    private val resetAi: ResetAiAssistantUseCase,
    private val displayPreferences: DisplayPreferences,
) : ViewModel() {
    private val sessionState = MutableStateFlow(AiSessionState())

    private var askJob: Job? = null
    private var modelsJob: Job? = null

    val uiState: StateFlow<AiUiState> = combine(
        aiRepository.observeConnection(),
        aiRepository.observeChatHistory(),
        sessionState,
    ) { connection, messages, session ->
        val ready = connection != null && connection.validated && aiRepository.hasApiKey()
        val pane = when {
            session.forceSetup || !ready -> AiPane.SETUP
            else -> AiPane.CHAT
        }
        AiUiState(
            loading = false,
            pane = pane,
            connection = connection,
            setup = session.setup,
            messages = messages,
            draft = session.draft,
            asking = session.asking,
            chatError = session.chatError,
            showResetConfirm = session.showResetConfirm,
            resetError = session.resetError,
            supportedProviders = aiRepository.supportedProviders(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiUiState(loading = true),
    )

    init {
        val existing = aiRepository.readConnection()
        if (existing != null) {
            sessionState.update { state ->
                state.copy(
                    setup = state.setup.copy(
                        providerId = existing.providerId,
                        modelId = existing.modelId,
                    ),
                )
            }
        }
    }

    fun selectProvider(providerId: AiProviderId) {
        if (providerId == sessionState.value.setup.providerId) return
        modelsJob?.cancel()
        updateSetup {
            it.copy(
                providerId = providerId,
                modelId = "",
                modelQuery = "",
                models = emptyList(),
                modelsError = null,
                modelsLive = false,
                modelsLoading = false,
                showModelPicker = false,
                testStatus = ConnectionTestStatus.IDLE,
                testMessage = null,
                fieldError = null,
            )
        }
    }

    fun updateModelQuery(query: String) {
        updateSetup { it.copy(modelQuery = query) }
    }

    fun openModelPicker() {
        updateSetup {
            it.copy(
                showModelPicker = true,
                modelQuery = "",
                fieldError = null,
            )
        }
        val form = sessionState.value.setup
        if (form.models.isEmpty() && !form.modelsLoading && form.apiKeyInput.trim().length >= 8) {
            loadModels()
        }
    }

    fun dismissModelPicker() {
        updateSetup {
            it.copy(
                showModelPicker = false,
                modelQuery = "",
            )
        }
    }

    fun selectModel(modelId: String) {
        val allowed = sessionState.value.setup.models.any { it.id == modelId }
        if (!allowed) {
            updateSetup { it.copy(fieldError = Strings.aiModelMustSelectFromList) }
            return
        }
        updateSetup {
            it.copy(
                modelId = modelId,
                showModelPicker = false,
                modelQuery = "",
                fieldError = null,
                testStatus = ConnectionTestStatus.IDLE,
                testMessage = null,
            )
        }
    }

    fun updateApiKey(value: String) {
        updateSetup {
            it.copy(
                apiKeyInput = value,
                fieldError = null,
                testStatus = ConnectionTestStatus.IDLE,
                testMessage = null,
            )
        }
    }

    fun toggleApiKeyVisibility() {
        updateSetup { it.copy(apiKeyVisible = !it.apiKeyVisible) }
    }

    fun loadModels() {
        val form = sessionState.value.setup
        val key = form.apiKeyInput.trim()
        if (key.length < 8) {
            updateSetup {
                it.copy(
                    fieldError = Strings.aiApiKeyRequired,
                    modelsError = Strings.aiModelsNeedKey,
                )
            }
            return
        }
        modelsJob?.cancel()
        modelsJob = viewModelScope.launch {
            updateSetup {
                it.copy(modelsLoading = true, modelsError = null, fieldError = null)
            }
            try {
                val models = aiRepository.listModels(form.providerId, key)
                updateSetup {
                    it.copy(
                        models = models,
                        modelsLoading = false,
                        modelsLive = models.any { m -> m.fromLiveCatalog },
                        modelsError = if (models.isEmpty()) Strings.aiNoModels else null,
                        modelId = it.modelId.takeIf { id -> models.any { m -> m.id == id } }.orEmpty(),
                    )
                }
            } catch (cancelled: CancellationException) {
                updateSetup { it.copy(modelsLoading = false) }
                throw cancelled
            } catch (error: Throwable) {
                updateSetup {
                    it.copy(
                        modelsLoading = false,
                        models = emptyList(),
                        modelsLive = false,
                        modelsError = userMessage(error),
                        modelId = "",
                    )
                }
            }
        }
    }

    fun testAndSaveConnection() {
        val form = sessionState.value.setup
        val key = form.apiKeyInput.trim()
        val modelId = form.modelId.trim()
        when {
            key.length < 8 -> {
                updateSetup { it.copy(fieldError = Strings.aiApiKeyRequired) }
                return
            }
            modelId.isEmpty() || form.models.none { it.id == modelId } -> {
                updateSetup { it.copy(fieldError = Strings.aiModelMustSelectFromList) }
                return
            }
        }
        viewModelScope.launch {
            updateSetup {
                it.copy(
                    testStatus = ConnectionTestStatus.TESTING,
                    testMessage = Strings.aiTestingConnection,
                    fieldError = null,
                )
            }
            try {
                aiRepository.testAndSaveConnection(
                    providerId = form.providerId,
                    modelId = modelId,
                    apiKey = key,
                )
                sessionState.update { state ->
                    state.copy(
                        forceSetup = false,
                        setup = state.setup.copy(
                            testStatus = ConnectionTestStatus.SUCCESS,
                            testMessage = Strings.aiConnectionSuccess,
                            apiKeyInput = "",
                            apiKeyVisible = false,
                            showModelPicker = false,
                        ),
                    )
                }
            } catch (error: Throwable) {
                // Failed test must not wipe a previously saved valid connection.
                updateSetup {
                    it.copy(
                        testStatus = ConnectionTestStatus.ERROR,
                        testMessage = userMessage(error),
                    )
                }
            }
        }
    }

    fun openConnectionSettings() {
        val connection = aiRepository.readConnection()
        sessionState.update { state ->
            state.copy(
                forceSetup = true,
                setup = state.setup.copy(
                    providerId = connection?.providerId ?: AiProviderId.OPENAI,
                    modelId = connection?.modelId.orEmpty(),
                    apiKeyInput = "",
                    apiKeyVisible = false,
                    testStatus = ConnectionTestStatus.IDLE,
                    testMessage = null,
                    fieldError = null,
                    models = emptyList(),
                    modelsError = null,
                    modelsLive = false,
                    showModelPicker = false,
                    modelQuery = "",
                ),
            )
        }
    }

    fun closeConnectionSettingsIfConnected() {
        if (aiRepository.isReady()) {
            sessionState.update {
                it.copy(
                    forceSetup = false,
                    setup = it.setup.copy(
                        apiKeyInput = "",
                        apiKeyVisible = false,
                        showModelPicker = false,
                        modelQuery = "",
                    ),
                )
            }
        }
    }

    fun updateDraft(value: String) {
        sessionState.update { it.copy(draft = value) }
    }

    fun sendQuestion() {
        val question = sessionState.value.draft.trim()
        if (question.isEmpty() || sessionState.value.asking) return
        if (!aiRepository.isReady()) {
            sessionState.update { it.copy(forceSetup = true) }
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
                val forceSetup = error is AiError.NotConfigured ||
                    error is AiError.Authentication ||
                    error is AiError.InvalidModel ||
                    error is AiError.InvalidConfiguration
                sessionState.update {
                    it.copy(
                        chatError = userMessage(error),
                        forceSetup = it.forceSetup || forceSetup,
                    )
                }
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
                sessionState.value = AiSessionState(forceSetup = true)
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

    private fun updateSetup(transform: (AiSetupFormState) -> AiSetupFormState) {
        sessionState.update { it.copy(setup = transform(it.setup)) }
    }

    private fun userMessage(error: Throwable): String =
        when (error) {
            is AiError.Authentication ->
                error.message ?: "احراز هویت ناموفق بود. کلید API را بررسی کنید."
            is AiError -> error.message ?: Strings.errorGeneric
            else -> Strings.errorGeneric
        }
}
