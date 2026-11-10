package com.example.personalledger.domain.stats

import kotlinx.datetime.LocalDate

enum class StatsTypeFilter {
    ALL,
    INCOME,
    EXPENSE,
}

enum class StatsTimeRange {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

data class StatsBucket(
    val index: Int,
    val axisLabel: String,
    val tooltipLabel: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val incomeRial: Long,
    val expenseRial: Long,
) {
    val hasData: Boolean get() = incomeRial > 0L || expenseRial > 0L
}

data class StatsReport(
    val periodLabel: String,
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val totalIncomeRial: Long,
    val totalExpenseRial: Long,
    val buckets: List<StatsBucket>,
    val hasAnyTransactionInApp: Boolean,
) {
    val hasDataInPeriod: Boolean
        get() = totalIncomeRial > 0L || totalExpenseRial > 0L
}
