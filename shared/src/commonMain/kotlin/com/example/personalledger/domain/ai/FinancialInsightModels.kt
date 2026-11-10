package com.example.personalledger.domain.ai

/**
 * Deterministic, aggregate-only financial snapshot for LLM explanation.
 * Never includes account names, titles, SMS, card numbers, notes, or raw category labels.
 */
data class FinancialInsightSnapshot(
    val currencyUnit: String,
    val calendarSystem: String,
    val todayLabel: String,
    val totalTransactionCount: Int,
    val accountCount: Int,
    val activeGoalCount: Int,
    val currentMonth: PeriodInsight?,
    val previousMonth: PeriodInsight?,
    val currentWeek: PeriodInsight?,
    val topExpenseCategories: List<RankedCategoryInsight>,
    val activeGoals: List<GoalInsight>,
) {
    val hasEnoughDataForNumericAnswers: Boolean
        get() = totalTransactionCount > 0
}

data class PeriodInsight(
    val metricName: String,
    val rangeLabel: String,
    val rangeStartLabel: String,
    val rangeEndLabel: String,
    val calendarSystem: String,
    val currencyUnit: String,
    val recordCount: Int,
    val incomeRial: Long,
    val expenseRial: Long,
    val netRial: Long,
)

data class RankedCategoryInsight(
    /** Opaque rank label only — never a user-authored category title. */
    val rankLabel: String,
    val type: String,
    val totalRial: Long,
    val transactionCount: Int,
)

data class GoalInsight(
    val rankLabel: String,
    val targetAmountRial: Long,
    val allocatedTotalRial: Long,
    val remainingRial: Long,
    val progressPercent: Int,
    val status: String,
)
