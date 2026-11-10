package com.example.personalledger.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.GoalProgress
import com.example.personalledger.domain.usecase.ObserveActiveGoalsUseCase
import com.example.personalledger.domain.usecase.ObserveArchivedGoalsUseCase
import com.example.personalledger.domain.usecase.RestoreGoalUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val active: List<GoalProgress> = emptyList(),
    val archived: List<GoalProgress> = emptyList(),
    val showArchived: Boolean = false,
    val loading: Boolean = true,
    val message: String? = null,
)

class GoalsViewModel(
    observeActive: ObserveActiveGoalsUseCase,
    observeArchived: ObserveArchivedGoalsUseCase,
    private val restoreGoal: RestoreGoalUseCase,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        observeActive(),
        observeArchived(),
        showArchived,
        message,
    ) { active, archived, show, msg ->
        GoalsUiState(
            active = active,
            archived = archived,
            showArchived = show,
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun toggleArchived() {
        showArchived.value = !showArchived.value
    }

    fun restore(id: String) {
        viewModelScope.launch {
            runCatching { restoreGoal(id) }
                .onFailure { message.value = it.message }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
