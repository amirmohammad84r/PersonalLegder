package com.example.personalledger.presentation.screens.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.PrimaryButton
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings

@Composable
fun GoalCompletedScreen(
    viewModel: GoalDetailViewModel,
    onBackToGoals: () -> Unit,
    onArchive: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = state.progress

    if (state.loading || progress == null) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        GoalIconBadge(progress.goal.iconKey)
        Spacer(Modifier.height(20.dp))
        Text(
            Strings.goalReachedTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "100%".toPersianDigits(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            progress.goal.name,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            MoneyFormatter.format(progress.goal.targetAmountRial),
            style = MaterialTheme.typography.bodyLarge,
        )
        progress.goal.targetDate?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                DateFormatter.formatFull(it, LocalCalendarSystem.current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = Strings.backToGoals, onClick = onBackToGoals)
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onArchive,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(Strings.archiveGoal)
        }
        Spacer(Modifier.height(24.dp))
    }
}
