package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.goals.GoalProgressCalculator
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType

/**
 * Deterministic, local-only financial aggregations for AI tools.
 * Amounts stay integer ریال; income and expense are never mixed incorrectly.
 */
object FinancialToolCalculator {
    fun monthlySummary(
        transactions: List<Transaction>,
        range: HalfOpenDateRange,
        scope: ToolTransactionScope,
    ): MonthlyFinancialSummaryResult {
        var income = 0L
        var expense = 0L
        var count = 0
        for (tx in transactions) {
            if (!range.contains(tx.date)) continue
            when (tx.type) {
                TransactionType.INCOME -> {
                    if (scope == ToolTransactionScope.EXPENSE) continue
                    income = SafeMoney.add(income, tx.amountRial)
                    count++
                }
                TransactionType.EXPENSE -> {
                    if (scope == ToolTransactionScope.INCOME) continue
                    expense = SafeMoney.add(expense, tx.amountRial)
                    count++
                }
            }
        }
        return MonthlyFinancialSummaryResult(
            periodStartInclusive = range.startInclusive.toString(),
            periodEndExclusive = range.endExclusive.toString(),
            calendar = FinancialPeriodSupport.calendarLabel(range.calendar),
            periodLabel = range.displayLabel,
            transactionScope = scope.name,
            incomeTotalRial = income,
            expenseTotalRial = expense,
            balanceRial = SafeMoney.subtract(income, expense),
            transactionCount = count,
            isEmpty = count == 0,
        )
    }

    fun averageDailyExpense(
        transactions: List<Transaction>,
        range: HalfOpenDateRange,
        policy: AverageDenominatorPolicy,
    ): AverageDailyExpenseResult {
        var expense = 0L
        val daysWithExpense = linkedSetOf<Int>()
        for (tx in transactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            if (!range.contains(tx.date)) continue
            expense = SafeMoney.add(expense, tx.amountRial)
            daysWithExpense.add(tx.date.toEpochDays().toInt())
        }
        val denominator = when (policy) {
            AverageDenominatorPolicy.CALENDAR_DAYS -> range.calendarDayCount
            AverageDenominatorPolicy.DAYS_WITH_TRANSACTIONS -> daysWithExpense.size
        }
        val average = if (denominator <= 0 || expense == 0L) {
            0L
        } else {
            SafeMoney.averageFloor(expense, denominator)
        }
        return AverageDailyExpenseResult(
            periodStartInclusive = range.startInclusive.toString(),
            periodEndExclusive = range.endExclusive.toString(),
            calendar = FinancialPeriodSupport.calendarLabel(range.calendar),
            periodLabel = range.displayLabel,
            expenseTotalRial = expense,
            denominatorDays = denominator,
            denominatorPolicy = policy.name,
            averageDailyExpenseRial = average,
            roundingPolicy = MoneyRoundingPolicy.FLOOR_TOWARD_ZERO.name,
            isEmpty = expense == 0L,
        )
    }

    fun expenseBreakdown(
        transactions: List<Transaction>,
        categories: List<Category>,
        range: HalfOpenDateRange,
        limit: Int,
    ): ExpenseBreakdownResult {
        val clamped = limit.coerceIn(1, FinancialToolId.MAX_BREAKDOWN_LIMIT)
        val names = categories.associate { it.id to sanitizeLabel(it.name) }
        val totals = linkedMapOf<String, Pair<Long, Int>>()
        var grand = 0L
        var count = 0
        for (tx in transactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            if (!range.contains(tx.date)) continue
            val prev = totals[tx.categoryId] ?: (0L to 0)
            totals[tx.categoryId] = SafeMoney.add(prev.first, tx.amountRial) to (prev.second + 1)
            grand = SafeMoney.add(grand, tx.amountRial)
            count++
        }
        val ranked = totals.entries.sortedByDescending { it.value.first }
        val top = ranked.take(clamped)
        val rest = ranked.drop(clamped)
        val items = mutableListOf<ExpenseBreakdownItem>()
        top.forEach { entry ->
            items += ExpenseBreakdownItem(
                label = names[entry.key] ?: "بدون‌نام",
                totalRial = entry.value.first,
                transactionCount = entry.value.second,
                isOtherBucket = false,
            )
        }
        if (rest.isNotEmpty()) {
            var otherSum = 0L
            var otherCount = 0
            rest.forEach { entry ->
                otherSum = SafeMoney.add(otherSum, entry.value.first)
                otherCount += entry.value.second
            }
            items += ExpenseBreakdownItem(
                label = "سایر",
                totalRial = otherSum,
                transactionCount = otherCount,
                isOtherBucket = true,
            )
        }
        return ExpenseBreakdownResult(
            periodStartInclusive = range.startInclusive.toString(),
            periodEndExclusive = range.endExclusive.toString(),
            calendar = FinancialPeriodSupport.calendarLabel(range.calendar),
            periodLabel = range.displayLabel,
            categories = items,
            expenseTotalRial = grand,
            transactionCount = count,
            limitApplied = clamped,
            isEmpty = count == 0,
        )
    }

    fun comparePeriods(
        transactions: List<Transaction>,
        first: HalfOpenDateRange,
        second: HalfOpenDateRange,
        metric: CompareMetric,
    ): CompareFinancialPeriodsResult {
        val firstValue = metricValue(transactions, first, metric)
        val secondValue = metricValue(transactions, second, metric)
        val diff = SafeMoney.subtract(secondValue, firstValue)
        val (percent, note) = if (firstValue == 0L) {
            null to "percent_change_undefined_because_baseline_is_zero"
        } else {
            val pct = (diff.toDouble() / firstValue.toDouble()) * 100.0
            pct to null
        }
        return CompareFinancialPeriodsResult(
            calendar = FinancialPeriodSupport.calendarLabel(first.calendar),
            metric = metric.name,
            first = PeriodCompareSide(
                periodStartInclusive = first.startInclusive.toString(),
                periodEndExclusive = first.endExclusive.toString(),
                periodLabel = first.displayLabel,
                valueRial = firstValue,
            ),
            second = PeriodCompareSide(
                periodStartInclusive = second.startInclusive.toString(),
                periodEndExclusive = second.endExclusive.toString(),
                periodLabel = second.displayLabel,
                valueRial = secondValue,
            ),
            absoluteDifferenceRial = diff,
            percentChange = percent,
            percentChangeNote = note,
        )
    }

    fun listActiveGoals(goals: List<Goal>): ListActiveGoalsResult {
        val active = goals
            .filter { it.status == GoalStatus.ACTIVE }
            .sortedBy { it.createdAt }
            .map {
                ActiveGoalSummary(
                    goalId = it.id,
                    name = sanitizeLabel(it.name),
                    status = it.status.name,
                )
            }
        return ListActiveGoalsResult(goals = active, count = active.size)
    }

    fun goalProgress(
        goals: List<Goal>,
        allocations: List<GoalAllocation>,
        goalId: String,
    ): GoalProgressToolResult {
        val goal = goals.firstOrNull { it.id == goalId }
            ?: throw FinancialToolValidationException("Goal id is not in the allowlisted set.")
        val progress = GoalProgressCalculator.progress(
            goal,
            allocations.filter { it.goalId == goal.id },
        )
        return GoalProgressToolResult(
            goalId = goal.id,
            name = sanitizeLabel(goal.name),
            status = goal.status.name,
            targetAmountRial = goal.targetAmountRial,
            allocatedTotalRial = progress.allocatedTotalRial,
            remainingRial = progress.remainingRial,
            progressPercent = progress.progressPercent,
            isTargetReached = progress.isTargetReached,
        )
    }

    /**
     * Labels are untrusted data (possible prompt injection). Strip control chars;
     * callers must still treat them as data, never instructions.
     */
    fun sanitizeLabel(raw: String): String =
        raw.trim()
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .take(80)
            .ifBlank { "بدون‌نام" }

    private fun metricValue(
        transactions: List<Transaction>,
        range: HalfOpenDateRange,
        metric: CompareMetric,
    ): Long {
        var income = 0L
        var expense = 0L
        for (tx in transactions) {
            if (!range.contains(tx.date)) continue
            when (tx.type) {
                TransactionType.INCOME -> income = SafeMoney.add(income, tx.amountRial)
                TransactionType.EXPENSE -> expense = SafeMoney.add(expense, tx.amountRial)
            }
        }
        return when (metric) {
            CompareMetric.INCOME -> income
            CompareMetric.EXPENSE -> expense
            CompareMetric.BALANCE -> SafeMoney.subtract(income, expense)
        }
    }
}
