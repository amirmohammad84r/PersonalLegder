package com.example.personalledger.presentation.screens.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.ConfirmDeleteDialog
import com.example.personalledger.presentation.components.ErrorBox
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings

@Composable
fun GoalDetailScreen(
    viewModel: GoalDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onAddAmount: (String) -> Unit,
    onEditAllocation: (goalId: String, allocationId: String) -> Unit,
    onCompleted: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessage()
    }
    LaunchedEffect(state.navigatedToCompleted, state.progress?.goal?.id) {
        val id = state.progress?.goal?.id
        if (state.navigatedToCompleted && id != null) {
            viewModel.consumedCompletedNavigation()
            onCompleted(id)
        }
    }
    LaunchedEffect(state.closed) {
        if (state.closed) onBack()
    }

    when {
        state.loading -> LoadingBox()
        state.notFound || state.progress == null -> ErrorBox(Strings.errorGeneric, onRetry = onBack)
        else -> {
            val progress = checkNotNull(state.progress)
            val goal = progress.goal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(ScreenPadding),
            ) {
                ScreenHeader(title = Strings.goalDetails, onBack = onBack)
                Spacer(Modifier.height(8.dp))
                LedgerCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            GoalStatusBadge(goal.status)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                goal.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                MoneyFormatter.format(goal.targetAmountRial),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${Strings.goalAllocated}: ${MoneyFormatter.format(progress.allocatedTotalRial)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${Strings.goalRemaining}: ${MoneyFormatter.format(progress.remainingRial)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (progress.excessRial > 0L) {
                                Text(
                                    "${Strings.goalExcess}: ${MoneyFormatter.format(progress.excessRial)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            goal.targetDate?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${Strings.untilDatePrefix} ${DateFormatter.formatFull(it, LocalCalendarSystem.current)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            GoalProgressBar(progress.progressFraction)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${progress.progressPercent}%".toPersianDigits(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        GoalIconBadge(goal.iconKey)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    Strings.goalAllocationDisclaimer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (goal.status == GoalStatus.ACTIVE) {
                    PrimaryButton(
                        text = Strings.addAmountToGoal,
                        onClick = { onAddAmount(goal.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (goal.status != GoalStatus.ARCHIVED) {
                        TextButton(onClick = { onEdit(goal.id) }) {
                            Text(Strings.editGoal)
                        }
                    }
                    if (goal.status == GoalStatus.ACTIVE) {
                        TextButton(onClick = viewModel::requestComplete) {
                            Text(Strings.completeGoal)
                        }
                    }
                    TextButton(onClick = viewModel::requestArchive) {
                        Text(Strings.archiveGoal)
                    }
                    TextButton(onClick = viewModel::requestDelete) {
                        Text(Strings.delete)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    Strings.goalAllocationHistory,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                if (state.allocations.isEmpty()) {
                    Text(
                        Strings.emptyGoalsHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.allocations.forEach { alloc ->
                        LedgerCard(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clickable {
                                    if (goal.status == GoalStatus.ACTIVE) {
                                        onEditAllocation(goal.id, alloc.id)
                                    }
                                },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        MoneyFormatter.format(alloc.amountRial),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            state.accountNames[alloc.accountId],
                                            DateFormatter.format(alloc.date, LocalCalendarSystem.current),
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    alloc.note?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (goal.status == GoalStatus.ACTIVE) {
                                    TextButton(
                                        onClick = { viewModel.requestDeleteAllocation(alloc.id) },
                                    ) {
                                        Text(Strings.delete)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                SnackbarHost(snackbar)
            }
        }
    }

    state.pendingDelete?.let { pending ->
        ConfirmDeleteDialog(
            message = pending.message,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
    state.pendingAllocationDeleteId?.let {
        ConfirmDeleteDialog(
            message = Strings.confirmDeleteGoalAllocation,
            onConfirm = viewModel::confirmDeleteAllocation,
            onDismiss = viewModel::dismissDeleteAllocation,
        )
    }
    if (state.confirmComplete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissComplete,
            title = { Text(Strings.completeGoal) },
            text = { Text(Strings.confirmCompleteGoal) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCompleteAction) { Text(Strings.completeGoal) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissComplete) { Text(Strings.cancel) }
            },
        )
    }
    if (state.confirmArchive) {
        AlertDialog(
            onDismissRequest = viewModel::dismissArchive,
            title = { Text(Strings.archiveGoal) },
            text = { Text(Strings.confirmArchiveGoal) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmArchiveAction) { Text(Strings.archiveGoal) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissArchive) { Text(Strings.cancel) }
            },
        )
    }
}
