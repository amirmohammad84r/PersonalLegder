package com.example.personalledger.presentation.screens.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.presentation.components.EmptyState
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.strings.Strings

@Composable
fun GoalsScreen(
    viewModel: GoalsViewModel,
    onCreateGoal: () -> Unit,
    onOpenGoal: (String) -> Unit,
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
        ScreenHeader(
            title = Strings.financialGoals,
            actions = {
                Button(
                    onClick = onCreateGoal,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("+ ${Strings.newGoal}", fontWeight = FontWeight.SemiBold)
                }
            },
        )
        when {
            state.active.isEmpty() && state.archived.isEmpty() -> {
                EmptyState(
                    title = Strings.emptyGoals,
                    subtitle = Strings.emptyGoalsHint,
                    actionLabel = Strings.createFirstGoal,
                    onAction = onCreateGoal,
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.active, key = { it.goal.id }) { item ->
                        LedgerCard(
                            modifier = Modifier
                                .padding(bottom = 10.dp)
                                .clickable { onOpenGoal(item.goal.id) },
                        ) {
                            GoalCardContent(item)
                        }
                    }
                    if (state.archived.isNotEmpty()) {
                        item {
                            TextButton(onClick = viewModel::toggleArchived) {
                                Text(
                                    if (state.showArchived) {
                                        Strings.archivedGoals + " ▴"
                                    } else {
                                        Strings.archivedGoals + " (${state.archived.size}) ▾"
                                    },
                                )
                            }
                        }
                        if (state.showArchived) {
                            items(state.archived, key = { "arch-${it.goal.id}" }) { item ->
                                LedgerCard(
                                    modifier = Modifier
                                        .padding(bottom = 10.dp)
                                        .clickable { onOpenGoal(item.goal.id) },
                                ) {
                                    GoalCardContent(item)
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.restore(item.goal.id) }) {
                                        Text(Strings.restoreGoal)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
        SnackbarHost(snackbar)
    }
}
