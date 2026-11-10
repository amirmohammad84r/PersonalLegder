package com.example.personalledger.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.repository.GoalDraft
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.usecase.SaveGoalUseCase
import com.example.personalledger.presentation.format.toLatinDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalEditorUiState(
    val goalId: String? = null,
    val nameInput: String = "",
    val targetAmountInput: String = "",
    val targetDateEpochDays: Long? = null,
    val iconKey: String? = GoalIcons.options.first().key,
    val nameError: String? = null,
    val amountError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val savedGoalId: String? = null,
    val loading: Boolean = false,
)

class GoalEditorViewModel(
    private val goalId: String?,
    private val goalRepository: GoalRepository,
    private val saveGoal: SaveGoalUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        GoalEditorUiState(goalId = goalId, loading = goalId != null),
    )
    val uiState: StateFlow<GoalEditorUiState> = _uiState.asStateFlow()

    init {
        if (goalId != null) {
            viewModelScope.launch {
                val existing = goalRepository.getById(goalId)
                if (existing == null) {
                    _uiState.update { it.copy(loading = false, error = "هدف پیدا نشد") }
                } else {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            nameInput = existing.name,
                            targetAmountInput = existing.targetAmountRial.toString(),
                            targetDateEpochDays = existing.targetDate?.toEpochDays()?.toLong(),
                            iconKey = existing.iconKey ?: GoalIcons.options.first().key,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(nameInput = value, nameError = null) }
    }

    fun onAmountChange(value: String) {
        val cleaned = value.toLatinDigits().filter { it.isDigit() }
        _uiState.update { it.copy(targetAmountInput = cleaned, amountError = null) }
    }

    fun onDateChange(epochDays: Long?) {
        _uiState.update { it.copy(targetDateEpochDays = epochDays) }
    }

    fun onIconChange(key: String) {
        _uiState.update { it.copy(iconKey = key) }
    }

    fun clearDate() {
        _uiState.update { it.copy(targetDateEpochDays = null) }
    }

    fun save() {
        val current = _uiState.value
        var ok = true
        if (current.nameInput.isBlank()) {
            _uiState.update { it.copy(nameError = "نام هدف را وارد کنید") }
            ok = false
        }
        val amount = current.targetAmountInput.toLongOrNull()
        if (amount == null || amount <= 0L) {
            _uiState.update { it.copy(amountError = "مبلغ هدف معتبر نیست") }
            ok = false
        }
        if (!ok) return

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            runCatching {
                saveGoal(
                    GoalDraft(
                        id = current.goalId,
                        name = current.nameInput,
                        targetAmountRial = amount,
                        targetDateEpochDays = current.targetDateEpochDays,
                        iconKey = current.iconKey,
                    ),
                )
            }.onSuccess { saved ->
                _uiState.update { it.copy(saving = false, savedGoalId = saved.id) }
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }
}
