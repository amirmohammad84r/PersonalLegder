package com.example.personalledger.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalProgress
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.usecase.ArchiveGoalUseCase
import com.example.personalledger.domain.usecase.CompleteGoalUseCase
import com.example.personalledger.domain.usecase.DeleteGoalAllocationUseCase
import com.example.personalledger.domain.usecase.DeleteGoalUseCase
import com.example.personalledger.domain.usecase.ObserveGoalDetailUseCase
import com.example.personalledger.presentation.strings.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingGoalDelete(
    val id: String,
    val message: String,
)

data class GoalDetailUiState(
    val progress: GoalProgress? = null,
    val allocations: List<GoalAllocation> = emptyList(),
    val accountNames: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val message: String? = null,
    val pendingDelete: PendingGoalDelete? = null,
    val pendingAllocationDeleteId: String? = null,
    val confirmComplete: Boolean = false,
    val confirmArchive: Boolean = false,
    val navigatedToCompleted: Boolean = false,
    val closed: Boolean = false,
)

private data class GoalDetailFlags(
    val message: String? = null,
    val pendingDelete: PendingGoalDelete? = null,
    val pendingAllocationDeleteId: String? = null,
    val confirmComplete: Boolean = false,
    val confirmArchive: Boolean = false,
    val navigatedToCompleted: Boolean = false,
    val closed: Boolean = false,
)

class GoalDetailViewModel(
    private val goalId: String,
    observeDetail: ObserveGoalDetailUseCase,
    private val completeGoal: CompleteGoalUseCase,
    private val archiveGoal: ArchiveGoalUseCase,
    private val deleteGoal: DeleteGoalUseCase,
    private val deleteAllocation: DeleteGoalAllocationUseCase,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val pendingDelete = MutableStateFlow<PendingGoalDelete?>(null)
    private val pendingAllocationDeleteId = MutableStateFlow<String?>(null)
    private val confirmComplete = MutableStateFlow(false)
    private val confirmArchive = MutableStateFlow(false)
    private val navigatedToCompleted = MutableStateFlow(false)
    private val closed = MutableStateFlow(false)

    private val flags = combine(
        message,
        pendingDelete,
        pendingAllocationDeleteId,
        confirmComplete,
        confirmArchive,
    ) { msg, pending, allocDel, complete, archive ->
        GoalDetailFlags(
            message = msg,
            pendingDelete = pending,
            pendingAllocationDeleteId = allocDel,
            confirmComplete = complete,
            confirmArchive = archive,
        )
    }.let { base ->
        combine(base, navigatedToCompleted, closed) { f, nav, isClosed ->
            f.copy(navigatedToCompleted = nav, closed = isClosed)
        }
    }

    val uiState: StateFlow<GoalDetailUiState> = combine(
        observeDetail(goalId),
        flags,
    ) { detail, f ->
        GoalDetailUiState(
            progress = detail?.progress,
            allocations = detail?.allocations.orEmpty(),
            accountNames = detail?.accountNames.orEmpty(),
            loading = false,
            notFound = detail == null,
            message = f.message,
            pendingDelete = f.pendingDelete,
            pendingAllocationDeleteId = f.pendingAllocationDeleteId,
            confirmComplete = f.confirmComplete,
            confirmArchive = f.confirmArchive,
            navigatedToCompleted = f.navigatedToCompleted,
            closed = f.closed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalDetailUiState())

    fun clearMessage() {
        message.value = null
    }

    fun requestComplete() {
        confirmComplete.value = true
    }

    fun dismissComplete() {
        confirmComplete.value = false
    }

    fun confirmCompleteAction() {
        confirmComplete.value = false
        viewModelScope.launch {
            runCatching { completeGoal(goalId) }
                .onSuccess { navigatedToCompleted.value = true }
                .onFailure { message.value = it.message }
        }
    }

    fun requestArchive() {
        confirmArchive.value = true
    }

    fun dismissArchive() {
        confirmArchive.value = false
    }

    fun confirmArchiveAction() {
        confirmArchive.value = false
        viewModelScope.launch {
            runCatching { archiveGoal(goalId) }
                .onSuccess { closed.value = true }
                .onFailure { message.value = it.message }
        }
    }

    fun requestDelete() {
        viewModelScope.launch {
            val count = runCatching { deleteGoal.relatedAllocationCount(goalId) }.getOrDefault(0)
            val msg = if (count > 0) {
                Strings.confirmDeleteGoalWithAllocations
            } else {
                Strings.confirmDeleteGoal
            }
            pendingDelete.value = PendingGoalDelete(goalId, msg)
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        pendingDelete.value = null
        viewModelScope.launch {
            runCatching { deleteGoal(goalId) }
                .onSuccess { closed.value = true }
                .onFailure { message.value = it.message }
        }
    }

    fun requestDeleteAllocation(id: String) {
        pendingAllocationDeleteId.value = id
    }

    fun dismissDeleteAllocation() {
        pendingAllocationDeleteId.value = null
    }

    fun confirmDeleteAllocation() {
        val id = pendingAllocationDeleteId.value ?: return
        pendingAllocationDeleteId.value = null
        viewModelScope.launch {
            runCatching { deleteAllocation(id) }
                .onFailure { message.value = it.message }
        }
    }

    fun consumedCompletedNavigation() {
        navigatedToCompleted.value = false
    }

    fun isEditable(progress: GoalProgress?): Boolean =
        progress?.goal?.status == GoalStatus.ACTIVE
}
