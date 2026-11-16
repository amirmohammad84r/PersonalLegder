package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Local-only snapshot used to execute allowlisted tools. Never sent to the model. */
data class ToolExecutionContext(
    val today: LocalDate,
    val preferredCalendar: CalendarSystem,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val goals: List<Goal>,
    val allocations: List<GoalAllocation>,
    /**
     * Filled by orchestrator after a successful property search in this ask.
     * Used by estimate_home_purchase_timeline so the model cannot invent prices.
     */
    var lastPropertySearchJson: String? = null,
    var lastPropertyPriceMinRial: Long? = null,
    var lastPropertyPriceMaxRial: Long? = null,
    var propertySearchRetrievedAtEpochMs: Long? = null,
)

data class ProposedToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ValidatedToolCall(
    val id: String,
    val toolId: FinancialToolId,
    val argumentsJson: String,
)

sealed class ToolExecutionOutcome {
    data class Success(
        val toolCallId: String,
        val toolName: String,
        /** Compact structured JSON aggregate for the model (no raw txs). */
        val resultJson: String,
    ) : ToolExecutionOutcome()

    data class ClarificationNeeded(
        val toolCallId: String,
        val toolName: String,
        val message: String,
    ) : ToolExecutionOutcome()

    data class Rejected(
        val toolCallId: String,
        val toolName: String,
        val reason: String,
    ) : ToolExecutionOutcome()
}

@Serializable
data class MonthlyFinancialSummaryResult(
    val periodStartInclusive: String,
    val periodEndExclusive: String,
    val calendar: String,
    val periodLabel: String,
    val transactionScope: String,
    val incomeTotalRial: Long,
    val expenseTotalRial: Long,
    val balanceRial: Long,
    val transactionCount: Int,
    val isEmpty: Boolean,
    val currencyUnit: String = "ریال",
)

@Serializable
data class AverageDailyExpenseResult(
    val periodStartInclusive: String,
    val periodEndExclusive: String,
    val calendar: String,
    val periodLabel: String,
    val expenseTotalRial: Long,
    val denominatorDays: Int,
    val denominatorPolicy: String,
    val averageDailyExpenseRial: Long,
    val roundingPolicy: String,
    val isEmpty: Boolean,
    val currencyUnit: String = "ریال",
)

@Serializable
data class ExpenseBreakdownItem(
    val label: String,
    val totalRial: Long,
    val transactionCount: Int,
    val isOtherBucket: Boolean = false,
)

@Serializable
data class ExpenseBreakdownResult(
    val periodStartInclusive: String,
    val periodEndExclusive: String,
    val calendar: String,
    val periodLabel: String,
    val categories: List<ExpenseBreakdownItem>,
    val expenseTotalRial: Long,
    val transactionCount: Int,
    val limitApplied: Int,
    val isEmpty: Boolean,
    val currencyUnit: String = "ریال",
)

@Serializable
data class PeriodCompareSide(
    val periodStartInclusive: String,
    val periodEndExclusive: String,
    val periodLabel: String,
    val valueRial: Long,
)

@Serializable
data class CompareFinancialPeriodsResult(
    val calendar: String,
    val metric: String,
    val first: PeriodCompareSide,
    val second: PeriodCompareSide,
    val absoluteDifferenceRial: Long,
    val percentChange: Double? = null,
    val percentChangeNote: String? = null,
    val currencyUnit: String = "ریال",
)

@Serializable
data class ActiveGoalSummary(
    val goalId: String,
    val name: String,
    val status: String,
)

@Serializable
data class ListActiveGoalsResult(
    val goals: List<ActiveGoalSummary>,
    val count: Int,
)

@Serializable
data class GoalProgressToolResult(
    val goalId: String,
    val name: String,
    val status: String,
    val targetAmountRial: Long,
    val allocatedTotalRial: Long,
    val remainingRial: Long,
    val progressPercent: Int,
    val isTargetReached: Boolean,
    val currencyUnit: String = "ریال",
)

@Serializable
data class IncomeSavingsSummaryResult(
    val periodStartInclusive: String,
    val periodEndExclusive: String,
    val calendar: String,
    val periodLabel: String,
    val monthsCovered: Int,
    val incomeTotalRial: Long,
    val expenseTotalRial: Long,
    /** Income − expense over the range. Not proven disposable savings. */
    val netBalanceRial: Long,
    val averageMonthlyIncomeRial: Long,
    val averageMonthlyExpenseRial: Long,
    val averageMonthlyNetBalanceRial: Long,
    val netBalanceLabel: String = "تراز درآمد و هزینه (پس‌انداز اثبات‌شده نیست)",
    val transactionCount: Int,
    val isEmpty: Boolean,
    val currencyUnit: String = "ریال",
)
