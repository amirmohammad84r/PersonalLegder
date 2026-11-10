package com.example.personalledger.presentation.screens.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.components.AddIconButton
import com.example.personalledger.presentation.components.ConfirmDeleteDialog
import com.example.personalledger.presentation.components.EmptyState
import com.example.personalledger.presentation.components.FilterChipsRow
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.components.SearchField
import com.example.personalledger.presentation.components.TransactionRow
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.strings.Strings

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onAddTransaction: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        ScreenHeader(
            title = Strings.transactions,
            onBack = onBack,
            actions = { AddIconButton(onAddTransaction, Strings.addTransaction) },
        )
        SearchField(value = state.query, onValueChange = viewModel::onQueryChange)
        Spacer(Modifier.height(10.dp))
        FilterChipsRow(selected = state.typeFilter, onSelected = viewModel::onFilterChange)
        Spacer(Modifier.height(12.dp))
        when {
            state.items.isEmpty() && state.query.isNotBlank() -> {
                EmptyState(title = Strings.emptySearch)
            }
            state.items.isEmpty() -> {
                EmptyState(
                    title = Strings.emptyTransactions,
                    subtitle = Strings.emptyTransactionsHint,
                    actionLabel = Strings.addTransaction,
                    onAction = onAddTransaction,
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.items, key = { it.transaction.id }) { item ->
                        LedgerCard(modifier = Modifier.padding(bottom = 10.dp)) {
                            TransactionRow(
                                title = item.transaction.title,
                                subtitle = "${item.categoryName} · ${DateFormatter.format(item.transaction.date, LocalCalendarSystem.current)}",
                                amountText = MoneyFormatter.formatSigned(
                                    item.transaction.amountRial,
                                    income = item.transaction.type == TransactionType.INCOME,
                                ),
                                type = item.transaction.type,
                                onClick = { onOpenTransaction(item.transaction.id) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)
    }

    pendingDeleteId?.let { id ->
        ConfirmDeleteDialog(
            message = Strings.confirmDeleteTransaction,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}
