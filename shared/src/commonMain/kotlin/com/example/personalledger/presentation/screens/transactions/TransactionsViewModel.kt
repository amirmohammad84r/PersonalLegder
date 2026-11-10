package com.example.personalledger.presentation.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.usecase.DeleteTransactionUseCase
import com.example.personalledger.domain.usecase.ObserveTransactionsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TransactionListItem(
    val transaction: Transaction,
    val categoryName: String,
)

data class TransactionsUiState(
    val query: String = "",
    val typeFilter: TransactionType? = null,
    val items: List<TransactionListItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val categoryRepository: CategoryRepository,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val filtered = combine(query, typeFilter) { q, t -> q to t }
        .flatMapLatest { (q, t) -> observeTransactions(q, t) }

    val uiState: StateFlow<TransactionsUiState> = combine(
        filtered,
        categoryRepository.observeAll(),
        query,
        typeFilter,
        message,
    ) { txs, categories, q, t, msg ->
        val map = categories.associateBy { it.id }
        TransactionsUiState(
            query = q,
            typeFilter = t,
            items = txs.map { tx ->
                TransactionListItem(
                    transaction = tx,
                    categoryName = map[tx.categoryId]?.name ?: "—",
                )
            },
            loading = false,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: TransactionType?) {
        typeFilter.value = value
    }

    fun clearMessage() {
        message.value = null
    }

    fun delete(id: String) {
        viewModelScope.launch {
            try {
                deleteTransaction(id)
            } catch (e: DomainException) {
                message.value = e.message
            } catch (e: Exception) {
                message.value = e.message
            }
        }
    }
}
