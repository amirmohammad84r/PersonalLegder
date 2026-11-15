package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.ai.tools.FinancialToolValidator.int
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireCalendar
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireGoalId
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireMetric
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalDenominator
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalLimit
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalScope
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.parseObject
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.string
import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Executes only validated allowlisted tools in-process.
 * Reads local data from [ToolExecutionContext]; never runs SQL or model-supplied code.
 */
class FinancialToolExecutor(
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
) {
    fun execute(
        call: ValidatedToolCall,
        context: ToolExecutionContext,
    ): ToolExecutionOutcome =
        try {
            val args = parseObject(call.argumentsJson)
            val payload = when (call.toolId) {
                FinancialToolId.GET_MONTHLY_FINANCIAL_SUMMARY ->
                    json.encodeToString(monthly(args, context))
                FinancialToolId.GET_AVERAGE_DAILY_EXPENSE ->
                    json.encodeToString(averageDaily(args, context))
                FinancialToolId.GET_EXPENSE_BREAKDOWN ->
                    json.encodeToString(breakdown(args, context))
                FinancialToolId.COMPARE_FINANCIAL_PERIODS ->
                    json.encodeToString(compare(args, context))
                FinancialToolId.LIST_ACTIVE_GOALS ->
                    json.encodeToString(FinancialToolCalculator.listActiveGoals(context.goals))
                FinancialToolId.GET_GOAL_PROGRESS ->
                    json.encodeToString(
                        FinancialToolCalculator.goalProgress(
                            goals = context.goals,
                            allocations = context.allocations,
                            goalId = requireGoalId(args),
                        ),
                    )
            }
            ToolExecutionOutcome.Success(
                toolCallId = call.id,
                toolName = call.toolId.apiName,
                resultJson = payload,
            )
        } catch (error: FinancialToolValidationException) {
            if (isAmbiguousPeriod(error)) {
                ToolExecutionOutcome.ClarificationNeeded(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    message = error.message ?: "بازهٔ زمانی مشخص نیست.",
                )
            } else {
                ToolExecutionOutcome.Rejected(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    reason = error.message ?: "Invalid tool arguments.",
                )
            }
        } catch (error: FinancialToolUnknownException) {
            ToolExecutionOutcome.Rejected(
                toolCallId = call.id,
                toolName = call.toolId.apiName,
                reason = error.message ?: "Unknown tool.",
            )
        }

    private fun monthly(args: JsonObject, context: ToolExecutionContext): MonthlyFinancialSummaryResult {
        val calendar = requireCalendar(args)
        val range = resolveMonthLikeRange(args, context, calendar)
        val scope = optionalScope(args)
        return FinancialToolCalculator.monthlySummary(context.transactions, range, scope)
    }

    private fun averageDaily(
        args: JsonObject,
        context: ToolExecutionContext,
    ): AverageDailyExpenseResult {
        val calendar = requireCalendar(args)
        val range = resolveFlexibleRange(args, context, calendar)
        val policy = optionalDenominator(args)
        return FinancialToolCalculator.averageDailyExpense(context.transactions, range, policy)
    }

    private fun breakdown(args: JsonObject, context: ToolExecutionContext): ExpenseBreakdownResult {
        val calendar = requireCalendar(args)
        val range = resolveFlexibleRange(args, context, calendar)
        val limit = optionalLimit(args)
        return FinancialToolCalculator.expenseBreakdown(
            transactions = context.transactions,
            categories = context.categories,
            range = range,
            limit = limit,
        )
    }

    private fun compare(args: JsonObject, context: ToolExecutionContext): CompareFinancialPeriodsResult {
        val calendar = requireCalendar(args)
        val metric = requireMetric(args)
        val first = resolveCompareSide(
            args = args,
            context = context,
            calendar = calendar,
            yearKey = "firstYear",
            monthKey = "firstMonth",
            periodKey = "firstPeriod",
            sideLabel = "first",
        )
        val second = resolveCompareSide(
            args = args,
            context = context,
            calendar = calendar,
            yearKey = "secondYear",
            monthKey = "secondMonth",
            periodKey = "secondPeriod",
            sideLabel = "second",
        )
        return FinancialToolCalculator.comparePeriods(
            transactions = context.transactions,
            first = first,
            second = second,
            metric = metric,
        )
    }

    private fun resolveMonthLikeRange(
        args: JsonObject,
        context: ToolExecutionContext,
        calendar: CalendarSystem,
    ): HalfOpenDateRange {
        val year = args.int("year")
        val month = args.int("month")
        val periodRaw = args.string("period")
        return when {
            year != null && month != null ->
                FinancialPeriodSupport.resolveYearMonth(year, month, calendar)
            !periodRaw.isNullOrBlank() -> {
                val period = runCatching { NamedMonthPeriod.valueOf(periodRaw) }.getOrElse {
                    throw FinancialToolValidationException("Invalid period.")
                }
                FinancialPeriodSupport.resolveNamedMonth(period, context.today, calendar)
            }
            else -> throw FinancialToolValidationException(
                "Ambiguous period: provide year+month or period=CURRENT_MONTH|PREVIOUS_MONTH.",
            )
        }
    }

    private fun resolveFlexibleRange(
        args: JsonObject,
        context: ToolExecutionContext,
        calendar: CalendarSystem,
    ): HalfOpenDateRange {
        val startRaw = args.string("startInclusive")
        val endRaw = args.string("endExclusive")
        if (!startRaw.isNullOrBlank() && !endRaw.isNullOrBlank()) {
            return FinancialPeriodSupport.resolveHalfOpen(
                startInclusive = FinancialPeriodSupport.parseIsoLocalDate(startRaw),
                endExclusive = FinancialPeriodSupport.parseIsoLocalDate(endRaw),
                calendar = calendar,
            )
        }
        return resolveMonthLikeRange(args, context, calendar)
    }

    private fun resolveCompareSide(
        args: JsonObject,
        context: ToolExecutionContext,
        calendar: CalendarSystem,
        yearKey: String,
        monthKey: String,
        periodKey: String,
        sideLabel: String,
    ): HalfOpenDateRange {
        val year = args.int(yearKey)
        val month = args.int(monthKey)
        val periodRaw = args.string(periodKey)
        return when {
            year != null && month != null ->
                FinancialPeriodSupport.resolveYearMonth(year, month, calendar)
            !periodRaw.isNullOrBlank() -> {
                val period = runCatching { NamedMonthPeriod.valueOf(periodRaw) }.getOrElse {
                    throw FinancialToolValidationException("Invalid $sideLabel period.")
                }
                FinancialPeriodSupport.resolveNamedMonth(period, context.today, calendar)
            }
            else -> throw FinancialToolValidationException(
                "Ambiguous $sideLabel period: provide year+month or named period.",
            )
        }
    }

    private fun isAmbiguousPeriod(error: FinancialToolValidationException): Boolean =
        error.message?.contains("Ambiguous", ignoreCase = true) == true
}
