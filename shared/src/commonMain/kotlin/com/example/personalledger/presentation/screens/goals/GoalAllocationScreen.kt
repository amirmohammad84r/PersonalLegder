package com.example.personalledger.presentation.screens.goals

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.AppDatePickerDialog
import com.example.personalledger.presentation.components.ErrorBox
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.ledgerOutlinedFieldColors
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalAllocationScreen(
    viewModel: GoalAllocationViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calendar = LocalCalendarSystem.current
    var showDatePicker by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    if (state.loading) {
        LoadingBox()
        return
    }
    if (state.error != null && state.allocationId != null && state.amountInput.isEmpty()) {
        ErrorBox(state.error!!)
        return
    }

    val selectedAccountLabel = state.accounts
        .find { it.account.id == state.accountId }
        ?.let { "${it.account.name} · ${MoneyFormatter.format(it.availableRial)}" }
        .orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(
            title = if (state.allocationId == null) {
                Strings.addAmountToGoalTitle
            } else {
                Strings.editGoal
            },
            onBack = onBack,
        )
        Spacer(Modifier.height(8.dp))
        LedgerTextField(
            value = state.amountInput,
            onValueChange = viewModel::onAmountChange,
            label = Strings.amount,
            keyboardType = KeyboardType.Number,
            isError = state.amountError != null,
            supportingText = state.amountError,
        )
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = it },
        ) {
            OutlinedTextField(
                value = selectedAccountLabel.ifBlank { Strings.select },
                onValueChange = {},
                readOnly = true,
                label = { Text(Strings.goalAllocationAccount) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(accountExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .clickable { accountExpanded = true },
                isError = state.accountError != null,
                supportingText = state.accountError?.let { { Text(it) } },
                shape = MaterialTheme.shapes.medium,
                colors = ledgerOutlinedFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = accountExpanded,
                onDismissRequest = { accountExpanded = false },
            ) {
                state.accounts.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "${option.account.name} · ${MoneyFormatter.format(option.availableRial)}",
                            )
                        },
                        onClick = {
                            viewModel.onAccountChange(option.account.id)
                            accountExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(Strings.goalAllocationDate, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Text(
                DateFormatter.format(
                    LocalDate.fromEpochDays(state.dateEpochDays.toInt()),
                    calendar,
                ),
            )
        }
        Spacer(Modifier.height(12.dp))
        LedgerTextField(
            value = state.noteInput,
            onValueChange = viewModel::onNoteChange,
            label = Strings.goalNote,
            singleLine = false,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            Strings.goalAllocationDisclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = Strings.registerGoalAmount,
            onClick = viewModel::save,
            loading = state.saving,
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = LocalDate.fromEpochDays(state.dateEpochDays.toInt()),
            calendar = calendar,
            onConfirm = { date -> viewModel.onDateChange(date.toEpochDays().toLong()) },
            onDismiss = { showDatePicker = false },
        )
    }
}
