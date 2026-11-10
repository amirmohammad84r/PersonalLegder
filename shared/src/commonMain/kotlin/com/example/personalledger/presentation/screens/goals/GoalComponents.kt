package com.example.personalledger.presentation.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.personalledger.domain.model.GoalProgress
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.format.DateFormatter
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.LedgerColors

@Composable
fun GoalProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(track),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .background(fill),
        )
    }
}

@Composable
fun GoalStatusBadge(status: GoalStatus) {
    val (label, bg, fg) = when (status) {
        GoalStatus.ACTIVE -> Triple(
            Strings.goalStatusActive,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        GoalStatus.COMPLETED -> Triple(
            Strings.goalStatusCompleted,
            LedgerColors.IncomeGreen.copy(alpha = 0.2f),
            LedgerColors.IncomeGreen,
        )
        GoalStatus.ARCHIVED -> Triple(
            Strings.goalStatusArchived,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = bg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun GoalIconBadge(iconKey: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = GoalIcons.iconFor(iconKey),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun GoalCardContent(progress: GoalProgress) {
    val goal = progress.goal
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            GoalStatusBadge(goal.status)
            Spacer(Modifier.height(8.dp))
            Text(
                text = goal.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormatter.format(goal.targetAmountRial),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${Strings.goalRemaining}: ${MoneyFormatter.format(progress.remainingRial)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress.excessRial > 0L) {
                Text(
                    text = "${Strings.goalExcess}: ${MoneyFormatter.format(progress.excessRial)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LedgerColors.IncomeGreen,
                )
            }
            Spacer(Modifier.height(10.dp))
            GoalProgressBar(progress.progressFraction)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${progress.progressPercent}%".toPersianDigits(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                goal.targetDate?.let { date ->
                    Text(
                        text = "${Strings.untilDatePrefix} ${DateFormatter.formatFull(date, LocalCalendarSystem.current)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        GoalIconBadge(goal.iconKey)
    }
}
