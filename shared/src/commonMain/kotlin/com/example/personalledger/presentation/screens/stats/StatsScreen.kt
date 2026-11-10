package com.example.personalledger.presentation.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.domain.stats.StatsTimeRange
import com.example.personalledger.domain.stats.StatsTypeFilter
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.format.MoneyFormatter
import com.example.personalledger.presentation.format.toPersianDigits
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.LedgerColors

@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        val msg = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearError()
    }

    if (state.loading) {
        LoadingBox()
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(title = Strings.stats)
        Spacer(Modifier.height(8.dp))

        SegmentRow(
            options = listOf(
                StatsTypeFilter.ALL to Strings.filterAll,
                StatsTypeFilter.INCOME to Strings.income,
                StatsTypeFilter.EXPENSE to Strings.expense,
            ),
            selected = state.typeFilter,
            onSelect = viewModel::onTypeFilterChange,
        )
        Spacer(Modifier.height(10.dp))
        SegmentRow(
            options = listOf(
                StatsTimeRange.DAILY to Strings.statsDaily,
                StatsTimeRange.WEEKLY to Strings.statsWeekly,
                StatsTimeRange.MONTHLY to Strings.statsMonthly,
                StatsTimeRange.YEARLY to Strings.statsYearly,
            ),
            selected = state.timeRange,
            onSelect = viewModel::onTimeRangeChange,
        )
        Spacer(Modifier.height(12.dp))

        PeriodNavigator(
            label = state.periodLabel.toPersianDigits(),
            onPrevious = viewModel::previousPeriod,
            onNext = viewModel::nextPeriod,
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SummaryMiniCard(
                title = Strings.totalIncome,
                amountRial = state.totalIncomeRial,
                modifier = Modifier.weight(1f),
            )
            SummaryMiniCard(
                title = Strings.totalExpense,
                amountRial = state.totalExpenseRial,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        LedgerCard {
            when {
                !state.hasAnyTransactionInApp -> {
                    ChartEmptyPlaceholder(
                        title = Strings.statsEmptyAppTitle,
                        subtitle = Strings.statsEmptyAppHint,
                    )
                }
                !state.hasDataInPeriod -> {
                    ChartEmptyPlaceholder(
                        title = Strings.statsEmptyPeriodTitle,
                        subtitle = Strings.statsEmptyPeriodHint,
                    )
                }
                !state.hasVisibleSeriesData -> {
                    ChartEmptyPlaceholder(
                        title = Strings.statsEmptyFilterTitle,
                        subtitle = Strings.statsEmptyFilterHint,
                    )
                }
                else -> {
                    ChartLegend(typeFilter = state.typeFilter)
                    Spacer(Modifier.height(8.dp))
                    StatsLineChart(
                        buckets = state.buckets,
                        typeFilter = state.typeFilter,
                        selectedIndex = state.selectedIndex,
                        incomeColor = LedgerColors.IncomeGreen,
                        expenseColor = LedgerColors.ExpenseRose,
                        gridColor = MaterialTheme.colorScheme.outline,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        markerColor = MaterialTheme.colorScheme.primary,
                        onSelect = viewModel::selectBucket,
                        modifier = Modifier.fillMaxWidth(),
                        verticalXLabels = state.timeRange == StatsTimeRange.YEARLY ||
                            state.timeRange == StatsTimeRange.WEEKLY,
                    )
                    state.selectedIndex?.let { idx ->
                        val bucket = state.buckets.getOrNull(idx) ?: return@let
                        Spacer(Modifier.height(10.dp))
                        TooltipCard(
                            label = bucket.tooltipLabel.toPersianDigits(),
                            typeFilter = state.typeFilter,
                            incomeRial = bucket.incomeRial,
                            expenseRial = bucket.expenseRial,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        SnackbarHost(snackbar)
    }
}

@Composable
private fun <T> SegmentRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = Strings.statsPreviousPeriod,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = Strings.statsNextPeriod,
            )
        }
    }
}

@Composable
private fun SummaryMiniCard(
    title: String,
    amountRial: Long,
    modifier: Modifier = Modifier,
) {
    LedgerCard(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = MoneyFormatter.format(amountRial),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChartLegend(typeFilter: StatsTypeFilter) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (typeFilter != StatsTypeFilter.EXPENSE) {
            LegendItem(color = LedgerColors.IncomeGreen, label = Strings.income)
        }
        if (typeFilter != StatsTypeFilter.INCOME) {
            LegendItem(color = LedgerColors.ExpenseRose, label = Strings.expense)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TooltipCard(
    label: String,
    typeFilter: StatsTypeFilter,
    incomeRial: Long,
    expenseRial: Long,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        when (typeFilter) {
            StatsTypeFilter.ALL -> {
                Text(
                    text = "${Strings.income}: ${MoneyFormatter.format(incomeRial)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = "${Strings.expense}: ${MoneyFormatter.format(expenseRial)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            StatsTypeFilter.INCOME -> {
                Text(
                    text = MoneyFormatter.format(incomeRial),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            StatsTypeFilter.EXPENSE -> {
                Text(
                    text = MoneyFormatter.format(expenseRial),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ChartEmptyPlaceholder(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
