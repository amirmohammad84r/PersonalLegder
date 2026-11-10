package com.example.personalledger.domain.ai

import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.goals.GoalProgressCalculator
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.stats.StatsCalculator
import com.example.personalledger.domain.stats.StatsTimeRange
import kotlinx.datetime.LocalDate

/**
 * App-side deterministic aggregations. The LLM must only explain these numbers.
 */
object FinancialInsightCalculator {
    fun build(
        transactions: List<Transaction>,
        goals: List<Goal>,
        allocations: List<GoalAllocation>,
        accountCount: Int,
        today: LocalDate,
        calendar: CalendarSystem,
    ): FinancialInsightSnapshot {
        val unit = CurrencyConfig.displayName
        val calendarLabel = when (calendar) {
            CalendarSystem.JALALI -> "jalali"
            CalendarSystem.GREGORIAN -> "gregorian"
        }
        val currentMonthReport = StatsCalculator.buildReport(
            transactions,
            StatsTimeRange.MONTHLY,
            today,
            calendar,
        )
        val previousMonthAnchor = AppCalendar.plusMonths(today, -1, calendar)
        val previousMonthReport = StatsCalculator.buildReport(
            transactions,
            StatsTimeRange.MONTHLY,
            previousMonthAnchor,
            calendar,
        )
        val weekReport = StatsCalculator.buildReport(
            transactions,
            StatsTimeRange.WEEKLY,
            today,
            calendar,
        )

        return FinancialInsightSnapshot(
            currencyUnit = unit,
            calendarSystem = calendarLabel,
            todayLabel = AppCalendar.formatFull(today, calendar),
            totalTransactionCount = transactions.size,
            accountCount = accountCount,
            activeGoalCount = goals.count { it.status == GoalStatus.ACTIVE },
            currentMonth = periodInsight(
                metricName = "current_month_totals",
                reportIncome = currentMonthReport.totalIncomeRial,
                reportExpense = currentMonthReport.totalExpenseRial,
                rangeLabel = currentMonthReport.periodLabel,
                rangeStart = currentMonthReport.rangeStart,
                rangeEnd = currentMonthReport.rangeEnd,
                recordCount = transactions.count {
                    it.date in currentMonthReport.rangeStart..currentMonthReport.rangeEnd
                },
                calendar = calendar,
                unit = unit,
            ),
            previousMonth = periodInsight(
                metricName = "previous_month_totals",
                reportIncome = previousMonthReport.totalIncomeRial,
                reportExpense = previousMonthReport.totalExpenseRial,
                rangeLabel = previousMonthReport.periodLabel,
                rangeStart = previousMonthReport.rangeStart,
                rangeEnd = previousMonthReport.rangeEnd,
                recordCount = transactions.count {
                    it.date in previousMonthReport.rangeStart..previousMonthReport.rangeEnd
                },
                calendar = calendar,
                unit = unit,
            ),
            currentWeek = periodInsight(
                metricName = "current_week_totals",
                reportIncome = weekReport.totalIncomeRial,
                reportExpense = weekReport.totalExpenseRial,
                rangeLabel = weekReport.periodLabel,
                rangeStart = weekReport.rangeStart,
                rangeEnd = weekReport.rangeEnd,
                recordCount = transactions.count {
                    it.date in weekReport.rangeStart..weekReport.rangeEnd
                },
                calendar = calendar,
                unit = unit,
            ),
            topExpenseCategories = topExpenseCategories(
                transactions = transactions,
                rangeStart = currentMonthReport.rangeStart,
                rangeEnd = currentMonthReport.rangeEnd,
            ),
            activeGoals = goalInsights(goals, allocations),
        )
    }

    private fun periodInsight(
        metricName: String,
        reportIncome: Long,
        reportExpense: Long,
        rangeLabel: String,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        recordCount: Int,
        calendar: CalendarSystem,
        unit: String,
    ): PeriodInsight =
        PeriodInsight(
            metricName = metricName,
            rangeLabel = rangeLabel,
            rangeStartLabel = AppCalendar.formatShort(rangeStart, calendar),
            rangeEndLabel = AppCalendar.formatShort(rangeEnd, calendar),
            calendarSystem = when (calendar) {
                CalendarSystem.JALALI -> "jalali"
                CalendarSystem.GREGORIAN -> "gregorian"
            },
            currencyUnit = unit,
            recordCount = recordCount,
            incomeRial = reportIncome,
            expenseRial = reportExpense,
            netRial = reportIncome - reportExpense,
        )

    private fun topExpenseCategories(
        transactions: List<Transaction>,
        rangeStart: LocalDate,
        rangeEnd: LocalDate,
        limit: Int = 5,
    ): List<RankedCategoryInsight> {
        val sums = linkedMapOf<String, Pair<Long, Int>>()
        for (tx in transactions) {
            if (tx.type != TransactionType.EXPENSE) continue
            if (tx.date !in rangeStart..rangeEnd) continue
            val prev = sums[tx.categoryId] ?: (0L to 0)
            sums[tx.categoryId] = (prev.first + tx.amountRial) to (prev.second + 1)
        }
        return sums.entries
            .sortedByDescending { it.value.first }
            .take(limit)
            .mapIndexed { index, entry ->
                RankedCategoryInsight(
                    rankLabel = "expense_category_rank_${index + 1}",
                    type = TransactionType.EXPENSE.name,
                    totalRial = entry.value.first,
                    transactionCount = entry.value.second,
                )
            }
    }

    private fun goalInsights(
        goals: List<Goal>,
        allocations: List<GoalAllocation>,
    ): List<GoalInsight> {
        val active = goals.filter { it.status == GoalStatus.ACTIVE }
            .sortedBy { it.createdAt }
        return active.mapIndexed { index, goal ->
            val progress = GoalProgressCalculator.progress(
                goal,
                allocations.filter { it.goalId == goal.id },
            )
            GoalInsight(
                rankLabel = "active_goal_${index + 1}",
                targetAmountRial = goal.targetAmountRial,
                allocatedTotalRial = progress.allocatedTotalRial,
                remainingRial = progress.remainingRial,
                progressPercent = progress.progressPercent,
                status = goal.status.name,
            )
        }
    }
}
