package com.example.personalledger.presentation.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.repository.GoalAllocationDraft
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.todayLocalDate
import com.example.personalledger.domain.usecase.ObserveAccountsUseCase
import com.example.personalledger.domain.usecase.SaveGoalAllocationUseCase
import com.example.personalledger.presentation.format.toLatinDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalAllocationAccountOption(
    val account: Account,
    val availableRial: Long,
)

data class GoalAllocationUiState(
    val goalId: String,
    val allocationId: String? = null,
    val amountInput: String = "",
    val accountId: String? = null,
    val dateEpochDays: Long = todayLocalDate().toEpochDays().toLong(),
    val noteInput: String = "",
    val accounts: List<GoalAllocationAccountOption> = emptyList(),
    val amountError: String? = null,
    val accountError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loading: Boolean = false,
)

class GoalAllocationViewModel(
    private val goalId: String,
    private val allocationId: String?,
    private val allocationRepository: GoalAllocationRepository,
    observeAccounts: ObserveAccountsUseCase,
    private val saveAllocation: SaveGoalAllocationUseCase,
) : ViewModel() {
    private val form = MutableStateFlow(
        GoalAllocationUiState(
            goalId = goalId,
            allocationId = allocationId,
            loading = allocationId != null,
        ),
    )

    val uiState: StateFlow<GoalAllocationUiState> = combine(
        form,
        observeAccounts(),
        allocationRepository.observeAll(),
    ) { f, accountBalances, allAllocs ->
        val options = accountBalances.map { ab ->
            val addBack = allAllocs
                .find { it.id == f.allocationId && it.accountId == ab.account.id }
                ?.amountRial
                ?: 0L
            GoalAllocationAccountOption(
                account = ab.account,
                availableRial = ab.balanceRial + addBack,
            )
        }
        val autoAccount = f.accountId ?: options.firstOrNull()?.account?.id
        f.copy(
            accounts = options,
            accountId = autoAccount,
            loading = f.loading,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        form.value,
    )

    init {
        if (allocationId != null) {
            viewModelScope.launch {
                val existing = allocationRepository.getById(allocationId)
                if (existing == null) {
                    form.update { it.copy(loading = false, error = "مبلغ پیدا نشد") }
                } else {
                    form.update {
                        it.copy(
                            loading = false,
                            amountInput = existing.amountRial.toString(),
                            accountId = existing.accountId,
                            dateEpochDays = existing.date.toEpochDays().toLong(),
                            noteInput = existing.note.orEmpty(),
                        )
                    }
                }
            }
        } else {
            form.update { it.copy(loading = false) }
        }
    }

    fun onAmountChange(value: String) {
        val cleaned = value.toLatinDigits().filter { it.isDigit() }
        form.update { it.copy(amountInput = cleaned, amountError = null) }
    }

    fun onAccountChange(accountId: String) {
        form.update { it.copy(accountId = accountId, accountError = null) }
    }

    fun onDateChange(epochDays: Long) {
        form.update { it.copy(dateEpochDays = epochDays) }
    }

    fun onNoteChange(value: String) {
        form.update { it.copy(noteInput = value) }
    }

    fun save() {
        val current = uiState.value
        var ok = true
        val amount = current.amountInput.toLongOrNull()
        if (amount == null || amount <= 0L) {
            form.update { it.copy(amountError = "مبلغ معتبر نیست") }
            ok = false
        }
        if (current.accountId.isNullOrBlank()) {
            form.update { it.copy(accountError = "حساب را انتخاب کنید") }
            ok = false
        }
        if (!ok) return

        // Persist selected account into form so edits keep it
        form.update { it.copy(accountId = current.accountId, saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                saveAllocation(
                    GoalAllocationDraft(
                        id = current.allocationId,
                        goalId = current.goalId,
                        accountId = current.accountId,
                        amountRial = amount,
                        dateEpochDays = current.dateEpochDays,
                        note = current.noteInput,
                    ),
                )
            }.onSuccess {
                form.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                form.update { it.copy(saving = false, error = e.message) }
            }
        }
    }
}
