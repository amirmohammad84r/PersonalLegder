package com.example.personalledger.presentation.screens.goals

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.todayLocalDate
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.AppDatePickerDialog
import com.example.personalledger.presentation.components.LedgerTextField
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.strings.Strings
import kotlinx.datetime.LocalDate

@Composable
fun GoalEditorScreen(
    viewModel: GoalEditorViewModel,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calendar = LocalCalendarSystem.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.savedGoalId) {
        state.savedGoalId?.let(onSaved)
    }

    if (state.loading) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(
            title = if (state.goalId == null) Strings.createGoal else Strings.editGoal,
            onBack = onBack,
        )
        Spacer(Modifier.height(12.dp))
        LedgerTextField(
            value = state.nameInput,
            onValueChange = viewModel::onNameChange,
            label = Strings.goalName,
            isError = state.nameError != null,
            supportingText = state.nameError,
        )
        Spacer(Modifier.height(12.dp))
        LedgerTextField(
            value = state.targetAmountInput,
            onValueChange = viewModel::onAmountChange,
            label = Strings.goalTargetAmount,
            keyboardType = KeyboardType.Number,
            isError = state.amountError != null,
            supportingText = state.amountError,
        )
        Spacer(Modifier.height(12.dp))
        Text(Strings.goalTargetDate, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = state.targetDateEpochDays?.let {
                        DateFormatter.formatFull(
                            LocalDate.fromEpochDays(it.toInt()),
                            calendar,
                        )
                    } ?: Strings.select,
                )
            }
            if (state.targetDateEpochDays != null) {
                TextButton(onClick = viewModel::clearDate) {
                    Text(Strings.cancel)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(Strings.goalChooseIcon, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GoalIcons.options.forEach { option ->
                val selected = state.iconKey == option.key
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable { viewModel.onIconChange(option.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    GoalIconBadge(option.key)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = if (state.goalId == null) Strings.createGoal else Strings.save,
            onClick = viewModel::save,
            loading = state.saving,
        )
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showDatePicker) {
        AppDatePickerDialog(
            initialDate = state.targetDateEpochDays?.let { LocalDate.fromEpochDays(it.toInt()) }
                ?: todayLocalDate(),
            calendar = calendar,
            onConfirm = { date -> viewModel.onDateChange(date.toEpochDays().toLong()) },
            onDismiss = { showDatePicker = false },
        )
    }
}
