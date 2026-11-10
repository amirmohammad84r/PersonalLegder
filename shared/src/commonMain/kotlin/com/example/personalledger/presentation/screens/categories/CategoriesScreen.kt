package com.example.personalledger.presentation.screens.categories

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
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.presentation.components.ConfirmDeleteDialog
import com.example.personalledger.presentation.components.FilterChipsRow
import com.example.personalledger.presentation.components.ItemOverflowMenu
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.CancelSaveActions
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.components.TypeSegmentedControl
import com.example.personalledger.presentation.strings.Strings

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
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
        ScreenHeader(title = Strings.categories, onBack = onBack)
        FilterChipsRow(selected = state.typeFilter, onSelected = viewModel::onFilterChange)
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.items, key = { it.id }) { category ->
                LedgerCard(modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemOverflowMenu(
                            onEdit = { viewModel.openEdit(category) },
                            onDelete = { pendingDeleteId = category.id },
                        )
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = if (category.type == TransactionType.INCOME) Strings.income else Strings.expense,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = viewModel::openCreate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(Strings.addCategory)
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
                Text(
                    if (state.editor.editing == null) Strings.addCategory else Strings.editCategory,
                )
            },
            text = {
                Column {
                    if (state.editor.editing == null) {
                        TypeSegmentedControl(
                            selected = state.editor.editorType,
                            onSelected = viewModel::onEditorTypeChange,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    LedgerTextField(
                        value = state.editor.nameInput,
                        onValueChange = viewModel::onNameChange,
                        label = Strings.categoryName,
                        isError = state.editor.nameError != null,
                        supportingText = state.editor.nameError,
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

    pendingDeleteId?.let { id ->
        ConfirmDeleteDialog(
            message = Strings.confirmDeleteCategory,
            onConfirm = {
                viewModel.delete(id)
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}
