package com.example.personalledger.presentation.screens.transactionedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.sms.FieldConfidence
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.AppDatePickerDialog
import com.example.personalledger.presentation.components.ConfirmDeleteDialog
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.CancelSaveActions
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.components.TypeSegmentedControl
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.ledgerOutlinedFieldColors
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    viewModel: TransactionEditViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onDeleteRequest: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calendar = LocalCalendarSystem.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.form.saved) {
        if (state.form.saved) onBack()
    }

    if (state.loading) {
        LoadingBox()
        return
    }

    when (state.step) {
        AddEntryStep.ChooseMode -> ChooseModeContent(
            onManual = viewModel::chooseManual,
            onSms = viewModel::chooseSms,
            onBack = onBack,
        )
        AddEntryStep.SmsPaste -> SmsPasteContent(
            state = state,
            onTextChange = viewModel::onSmsTextChange,
            onClear = viewModel::clearSmsText,
            onParse = viewModel::parseSms,
            onManual = viewModel::chooseManual,
            onBack = viewModel::backToModeChoice,
        )
        AddEntryStep.ManualForm,
        AddEntryStep.SmsReview,
        -> FormContent(
            state = state,
            showSmsBadges = state.step == AddEntryStep.SmsReview,
            onBack = {
                when (state.step) {
                    AddEntryStep.SmsReview -> viewModel.backToSmsPaste()
                    AddEntryStep.ManualForm -> if (state.form.id == null) {
                        viewModel.backToModeChoice()
                    } else {
                        onBack()
                    }
                    else -> onBack()
                }
            },
            onDeleteClick = { showDeleteConfirm = true },
            showDelete = state.form.id != null && onDeleteRequest != null,
            onTypeChange = viewModel::onTypeChange,
            onAmountChange = viewModel::onAmountChange,
            onTitleChange = viewModel::onTitleChange,
            onCategoryChange = viewModel::onCategoryChange,
            onAccountChange = viewModel::onAccountChange,
            onDateClick = { showDatePicker = true },
            onSave = viewModel::save,
        )
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = LocalDate.fromEpochDays(state.form.dateEpochDays.toInt()),
            calendar = calendar,
            onConfirm = { date -> viewModel.onDateChange(date.toEpochDays().toLong()) },
            onDismiss = { showDatePicker = false },
        )
    }

    if (showDeleteConfirm && state.form.id != null) {
        ConfirmDeleteDialog(
            message = Strings.confirmDeleteTransaction,
            onConfirm = {
                showDeleteConfirm = false
                onDeleteRequest?.invoke()
                onDeleted()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun ChooseModeContent(
    onManual: () -> Unit,
    onSms: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
        ScreenHeader(title = Strings.addTransaction, onBack = onBack)
        Spacer(Modifier.height(16.dp))
        Text(Strings.chooseEntryMode, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(text = Strings.entryManual, onClick = onManual)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSms,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(Strings.entryFromSms)
        }
    }
}

@Composable
private fun SmsPasteContent(
    state: TransactionEditUiState,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onParse: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(title = Strings.entryFromSms, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.smsText,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth().height(180.dp),
            label = { Text(Strings.smsPasteTitle) },
            placeholder = { Text(Strings.smsPasteHint) },
            shape = MaterialTheme.shapes.medium,
            colors = ledgerOutlinedFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onClear) { Text(Strings.smsClear) }
        if (state.smsError != null) {
            Text(state.smsError, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(
            text = if (state.parsing) Strings.smsParsing else Strings.smsReview,
            onClick = onParse,
            loading = state.parsing,
            enabled = state.smsText.isNotBlank() && !state.parsing,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onManual,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(Strings.smsGoManual)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormContent(
    state: TransactionEditUiState,
    showSmsBadges: Boolean,
    onBack: () -> Unit,
    onDeleteClick: () -> Unit,
    showDelete: Boolean,
    onTypeChange: (com.example.personalledger.domain.model.TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onDateClick: () -> Unit,
    onSave: () -> Unit,
) {
    val form = state.form
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(
            title = when {
                form.id != null -> Strings.editTransaction
                showSmsBadges -> Strings.smsConfirmTitle
                else -> Strings.addTransaction
            },
            onBack = onBack,
            actions = {
                if (showDelete) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.semantics { contentDescription = Strings.delete },
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        if (showSmsBadges) {
            state.duplicateWarning?.let {
                LedgerCard {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        TypeSegmentedControl(selected = form.type, onSelected = onTypeChange)
        if (showSmsBadges) ConfidenceHint(state.fieldStatus.type)
        Spacer(Modifier.height(14.dp))
        LedgerTextField(
            value = form.amountInput,
            onValueChange = onAmountChange,
            label = Strings.amount,
            keyboardType = KeyboardType.Number,
            isError = form.amountError != null,
            supportingText = form.amountError,
        )
        if (showSmsBadges) {
            ConfidenceHint(state.fieldStatus.amount)
            form.detectedUnitLabel?.let {
                Text(
                    "${Strings.detectedUnit}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConfidenceHint(state.fieldStatus.unit)
            }
            form.balanceAfterLabel?.let {
                Text(
                    "${Strings.balanceAfter}: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LedgerTextField(
            value = form.title,
            onValueChange = onTitleChange,
            label = Strings.title,
            isError = form.titleError != null,
            supportingText = form.titleError,
        )
        if (showSmsBadges) ConfidenceHint(state.fieldStatus.title)
        Spacer(Modifier.height(10.dp))
        DropdownField(
            label = Strings.category,
            value = state.categories.find { it.id == form.categoryId }?.name.orEmpty(),
            options = state.categories.map { it.id to it.name },
            isError = form.categoryError != null,
            supportingText = form.categoryError
                ?: if (state.categories.isEmpty()) {
                    "ابتدا یک دسته‌بندی بسازید"
                } else if (showSmsBadges) {
                    Strings.categoryChooseHint
                } else {
                    null
                },
            onSelect = onCategoryChange,
        )
        Spacer(Modifier.height(10.dp))
        DropdownField(
            label = Strings.account,
            value = state.accounts.find { it.id == form.accountId }?.name.orEmpty(),
            options = state.accounts.map { it.id to it.name },
            isError = form.accountError != null,
            supportingText = form.accountError
                ?: form.accountHint?.let { "${Strings.accountHint}: $it" }
                ?: if (showSmsBadges) Strings.accountChooseHint else null,
            onSelect = onAccountChange,
        )
        if (showSmsBadges) ConfidenceHint(state.fieldStatus.account)
        Spacer(Modifier.height(10.dp))
        LedgerTextField(
            value = DateFormatter.format(
                LocalDate.fromEpochDays(form.dateEpochDays.toInt()),
                LocalCalendarSystem.current,
            ),
            onValueChange = {},
            label = Strings.date,
            readOnly = true,
            onClick = onDateClick,
            trailingIcon = {
                Icon(Icons.Default.DateRange, contentDescription = Strings.date)
            },
        )
        if (showSmsBadges) ConfidenceHint(state.fieldStatus.date)
        if (form.error != null &&
            form.amountError == null &&
            form.titleError == null &&
            form.categoryError == null &&
            form.accountError == null
        ) {
            Spacer(Modifier.height(8.dp))
            Text(form.error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        CancelSaveActions(
            onCancel = onBack,
            onSave = onSave,
            saving = form.saving,
            saveEnabled = state.canSave,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfidenceHint(confidence: FieldConfidence) {
    val label = when (confidence) {
        FieldConfidence.DETECTED -> Strings.badgeDetected
        FieldConfidence.NEEDS_REVIEW -> Strings.badgeNeedsReview
        FieldConfidence.UNCERTAIN -> Strings.badgeUncertain
        FieldConfidence.MISSING -> Strings.badgeMissing
    }
    val color = when (confidence) {
        FieldConfidence.DETECTED -> MaterialTheme.colorScheme.primary
        FieldConfidence.NEEDS_REVIEW,
        FieldConfidence.UNCERTAIN,
        -> MaterialTheme.colorScheme.error
        FieldConfidence.MISSING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value.ifBlank { Strings.select },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .clickable { expanded = true },
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            shape = MaterialTheme.shapes.medium,
            colors = ledgerOutlinedFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
