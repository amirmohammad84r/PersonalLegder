package com.example.personalledger.presentation.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.AccountBalance
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.usecase.DeleteAccountUseCase
import com.example.personalledger.domain.usecase.ObserveAccountsUseCase
import com.example.personalledger.domain.usecase.SaveAccountUseCase
import com.example.personalledger.presentation.format.toLatinDigits
import com.example.personalledger.presentation.strings.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountEditorState(
    val visible: Boolean = false,
    val editing: Account? = null,
    val nameInput: String = "",
    val initialBalanceInput: String = "0",
    val nameError: String? = null,
    val saving: Boolean = false,
)

data class PendingAccountDelete(
    val id: String,
    val message: String,
)

data class AccountsUiState(
    val items: List<AccountBalance> = emptyList(),
    val totalBalanceRial: Long = 0L,
    val editor: AccountEditorState = AccountEditorState(),
    val pendingDelete: PendingAccountDelete? = null,
    val message: String? = null,
    val loading: Boolean = true,
)

class AccountsViewModel(
    observeAccounts: ObserveAccountsUseCase,
    private val saveAccount: SaveAccountUseCase,
    private val deleteAccount: DeleteAccountUseCase,
) : ViewModel() {
    private val editor = MutableStateFlow(AccountEditorState())
    private val pendingDelete = MutableStateFlow<PendingAccountDelete?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(
        observeAccounts(),
        editor,
        pendingDelete,
        message,
    ) { items, ed, pending, msg ->
        AccountsUiState(
            items = items,
            totalBalanceRial = items.sumOf { it.balanceRial },
            editor = ed,
            pendingDelete = pending,
            message = msg,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun openCreate() {
        editor.value = AccountEditorState(visible = true)
    }

    fun openEdit(account: Account) {
        editor.value = AccountEditorState(
            visible = true,
            editing = account,
            nameInput = account.name,
            initialBalanceInput = account.initialBalanceRial.toString(),
        )
    }

    fun closeEditor() {
        editor.update { it.copy(visible = false) }
    }

    fun onNameChange(value: String) {
        editor.update { it.copy(nameInput = value, nameError = null) }
    }

    fun onInitialBalanceChange(value: String) {
        val cleaned = value.toLatinDigits().filter { it.isDigit() || it == '-' }
        editor.update { it.copy(initialBalanceInput = cleaned) }
    }

    fun clearMessage() {
        message.value = null
    }

    fun save() {
        val current = editor.value
        if (current.saving) return
        editor.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                saveAccount(
                    id = current.editing?.id,
                    name = current.nameInput,
                    initialBalanceRial = current.initialBalanceInput.toLongOrNull() ?: 0L,
                )
                editor.value = AccountEditorState(visible = false)
            } catch (e: DomainException.Validation) {
                editor.update { it.copy(saving = false, nameError = e.message) }
            } catch (e: Exception) {
                editor.update { it.copy(saving = false) }
                message.value = e.message
            }
        }
    }

    fun requestDelete(id: String) {
        viewModelScope.launch {
            val txCount = runCatching { deleteAccount.relatedTransactionCount(id) }.getOrDefault(0)
            val goalAllocCount =
                runCatching { deleteAccount.relatedGoalAllocationCount(id) }.getOrDefault(0)
            val msg = when {
                txCount > 0 || goalAllocCount > 0 -> {
                    buildString {
                        append("اگر این حساب را پاک کنید")
                        if (txCount > 0) append("، $txCount تراکنش")
                        if (goalAllocCount > 0) append(" و $goalAllocCount مبلغ اختصاص‌یافته به اهداف")
                        append(" هم حذف می‌شود. ادامه می‌دهید؟")
                    }
                }
                else -> Strings.confirmDeleteAccount
            }
            pendingDelete.value = PendingAccountDelete(id = id, message = msg)
        }
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val pending = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            try {
                deleteAccount(pending.id)
            } catch (e: DomainException) {
                message.value = e.message
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }
}
