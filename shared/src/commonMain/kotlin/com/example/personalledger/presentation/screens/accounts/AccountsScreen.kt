package com.example.personalledger.presentation.screens.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.presentation.components.CancelSaveActions
import com.example.personalledger.presentation.components.ConfirmDeleteDialog
import com.example.personalledger.presentation.components.ItemOverflowMenu
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.strings.Strings

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onBack: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }

    if (state.loading) {
        LoadingBox()
        return
    }

    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        ScreenHeader(title = Strings.accounts, onBack = onBack)
        LedgerCard {
            Text(
                text = Strings.totalBalance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = MoneyFormatter.format(state.totalBalanceRial),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.account.id }) { item ->
                LedgerCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemOverflowMenu(
                            onEdit = { viewModel.openEdit(item.account) },
                            onDelete = { viewModel.requestDelete(item.account.id) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.account.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${Strings.balance}: ${MoneyFormatter.format(item.balanceRial)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::openCreate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(Strings.addAccount)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        SnackbarHost(snackbar)
    }

    if (state.editor.visible) {
        AlertDialog(
            onDismissRequest = viewModel::closeEditor,
            title = {
                Text(if (state.editor.editing == null) Strings.addAccount else Strings.editAccount)
            },
            text = {
                Column {
                    LedgerTextField(
                        value = state.editor.nameInput,
                        onValueChange = viewModel::onNameChange,
                        label = Strings.accountName,
                        isError = state.editor.nameError != null,
                        supportingText = state.editor.nameError,
                    )
                    Spacer(Modifier.height(10.dp))
                    LedgerTextField(
                        value = state.editor.initialBalanceInput,
                        onValueChange = viewModel::onInitialBalanceChange,
                        label = Strings.initialBalance,
                        keyboardType = KeyboardType.Number,
                    )
                }
            },
            confirmButton = {
                CancelSaveActions(
                    onCancel = viewModel::closeEditor,
                    onSave = viewModel::save,
                    saving = state.editor.saving,
                )
            },
        )
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            message = pending.message,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
}
