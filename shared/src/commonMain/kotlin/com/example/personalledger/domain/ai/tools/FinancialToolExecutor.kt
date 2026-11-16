package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.ai.search.PropertyPriceSearchOutcome
import com.example.personalledger.domain.ai.search.PropertyPriceSearchProvider
import com.example.personalledger.domain.ai.search.PropertyPriceSearchRequest
import com.example.personalledger.domain.ai.search.UnavailablePropertyPriceSearchProvider
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.int
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.long
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalDenominator
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalLimit
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.optionalScope
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.parseObject
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireCalendar
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireGoalId
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.requireMetric
import com.example.personalledger.domain.ai.tools.FinancialToolValidator.string
import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Executes only validated allowlisted tools in-process.
 * Reads local data from [ToolExecutionContext]; never runs SQL or model-supplied code.
 */
class FinancialToolExecutor(
    private val propertySearch: PropertyPriceSearchProvider = UnavailablePropertyPriceSearchProvider(),
    private val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true },
) {
    suspend fun execute(
        call: ValidatedToolCall,
        context: ToolExecutionContext,
    ): ToolExecutionOutcome =
        try {
            if (call.toolId.isMetaHistoryRequest()) {
                return ToolExecutionOutcome.Rejected(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    reason = "History requests are handled by the orchestrator, not executed as data tools.",
                )
            }
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
                FinancialToolId.GET_INCOME_SAVINGS_SUMMARY ->
                    json.encodeToString(incomeSavings(args, context))
                FinancialToolId.SEARCH_CURRENT_PROPERTY_PRICES ->
                    return propertySearchOutcome(call, args, context)
                FinancialToolId.ESTIMATE_HOME_PURCHASE_TIMELINE ->
                    json.encodeToString(estimatePurchase(args, context))
                FinancialToolId.REQUEST_CHAT_HISTORY ->
                    error("unreachable")
            }
            ToolExecutionOutcome.Success(
                toolCallId = call.id,
                toolName = call.toolId.apiName,
                resultJson = payload,
            )
        } catch (error: FinancialToolValidationException) {
            if (isAmbiguousPeriod(error) || isClarification(error)) {
                ToolExecutionOutcome.ClarificationNeeded(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    message = error.message ?: "اطلاعات کافی نیست.",
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

    private suspend fun propertySearchOutcome(
        call: ValidatedToolCall,
        args: JsonObject,
        context: ToolExecutionContext,
    ): ToolExecutionOutcome {
        val location = FinancialToolCalculator.sanitizeLabel(
            args.string("locationQuery")?.trim().orEmpty(),
        )
        if (location.length < 2) {
            return ToolExecutionOutcome.ClarificationNeeded(
                toolCallId = call.id,
                toolName = call.toolId.apiName,
                message = "برای جست‌وجوی قیمت، شهر یا محله را مشخص کنید.",
            )
        }
        if (location.length > FinancialToolId.MAX_SEARCH_QUERY_CHARS) {
            return ToolExecutionOutcome.Rejected(
                toolCallId = call.id,
                toolName = call.toolId.apiName,
                reason = "locationQuery too long.",
            )
        }
        val request = PropertyPriceSearchRequest(
            locationQuery = location.take(FinancialToolId.MAX_SEARCH_QUERY_CHARS),
            propertyType = args.string("propertyType")?.trim()?.take(40),
            areaSqmMin = args.int("areaSqmMin"),
            areaSqmMax = args.int("areaSqmMax"),
            maxResults = FinancialToolId.MAX_SEARCH_RESULTS,
        )
        return when (val outcome = propertySearch.search(request)) {
            is PropertyPriceSearchOutcome.ClarificationNeeded ->
                ToolExecutionOutcome.ClarificationNeeded(call.id, call.toolId.apiName, outcome.message)
            is PropertyPriceSearchOutcome.Unavailable ->
                ToolExecutionOutcome.Success(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    resultJson = json.encodeToString(
                        buildJsonObject {
                            put("status", "UNAVAILABLE")
                            put("reason", outcome.reason)
                            put("activationHint", outcome.activationHint)
                            put("isCurrentMarketPrice", false)
                        },
                    ),
                )
            is PropertyPriceSearchOutcome.Failed ->
                ToolExecutionOutcome.Rejected(call.id, call.toolId.apiName, outcome.message)
            is PropertyPriceSearchOutcome.Success -> {
                val range = HomePurchaseEstimator.priceRangeRial(outcome.listings)
                context.lastPropertySearchJson = json.encodeToString(
                    buildJsonObject {
                        put("status", "OK")
                        put("queryUsed", FinancialToolCalculator.sanitizeLabel(outcome.queryUsed))
                        put("retrievedAtEpochMs", outcome.retrievedAtEpochMs)
                        put("isCurrentMarketPrice", range != null)
                        putJsonArray("listings") {
                            outcome.listings.forEach { listing ->
                                add(
                                    buildJsonObject {
                                        put("title", FinancialToolCalculator.sanitizeLabel(listing.title))
                                        put("url", listing.url.take(500))
                                        put("sourceName", listing.sourceName?.let {
                                            FinancialToolCalculator.sanitizeLabel(it)
                                        })
                                        put("sourcePublishedAtEpochMs", listing.sourcePublishedAtEpochMs)
                                        put("retrievedAtEpochMs", listing.retrievedAtEpochMs)
                                        put("cityOrArea", listing.cityOrArea?.let {
                                            FinancialToolCalculator.sanitizeLabel(it)
                                        })
                                        put("propertyType", listing.propertyType?.let {
                                            FinancialToolCalculator.sanitizeLabel(it)
                                        })
                                        put("areaSqm", listing.areaSqm)
                                        put("priceAmount", listing.priceAmount)
                                        put("priceCurrency", listing.priceCurrency?.name)
                                        put("priceKind", listing.priceKind)
                                    },
                                )
                            }
                        }
                        putJsonArray("notes") {
                            outcome.notes.forEach {
                                add(JsonPrimitive(FinancialToolCalculator.sanitizeLabel(it)))
                            }
                        }
                        if (range != null) {
                            putJsonObject("priceRangeRial") {
                                put("minRial", range.minRial)
                                put("maxRial", range.maxRial)
                            }
                        }
                    },
                )
                context.lastPropertyPriceMinRial = range?.minRial
                context.lastPropertyPriceMaxRial = range?.maxRial
                context.propertySearchRetrievedAtEpochMs = outcome.retrievedAtEpochMs
                ToolExecutionOutcome.Success(
                    toolCallId = call.id,
                    toolName = call.toolId.apiName,
                    resultJson = context.lastPropertySearchJson!!,
                )
            }
        }
    }

    private fun estimatePurchase(
        args: JsonObject,
        context: ToolExecutionContext,
    ): HomePurchaseEstimateResult {
        val min = context.lastPropertyPriceMinRial
        val max = context.lastPropertyPriceMaxRial
        if (min == null || max == null || context.lastPropertySearchJson == null) {
            throw FinancialToolValidationException(
                "Clarification: first run search_current_property_prices with a usable price range, " +
                    "or a search provider is not configured.",
            )
        }
        val calendar = requireCalendar(args)
        val months = (args.int("monthsLookback") ?: FinancialToolId.DEFAULT_LOOKBACK_MONTHS)
            .coerceIn(1, FinancialToolId.MAX_LOOKBACK_MONTHS)
        val range = FinancialPeriodSupport.resolveLastNMonths(months, context.today, calendar)
        val summary = FinancialToolCalculator.incomeSavingsSummary(
            context.transactions,
            range,
            months,
        )
        val assumptionRaw = args.string("surplusAssumption") ?: "BOTH"
        val assumptions = when (assumptionRaw) {
            "ALL_MONTHLY_BALANCE" -> listOf(SurplusAssumption.ALL_MONTHLY_BALANCE)
            "HALF_MONTHLY_BALANCE" -> listOf(SurplusAssumption.HALF_MONTHLY_BALANCE)
            "BOTH" -> listOf(
                SurplusAssumption.ALL_MONTHLY_BALANCE,
                SurplusAssumption.HALF_MONTHLY_BALANCE,
            )
            else -> throw FinancialToolValidationException("Invalid surplusAssumption.")
        }
        return HomePurchaseEstimator.estimate(
            averageMonthlyIncomeRial = summary.averageMonthlyIncomeRial,
            averageMonthlyExpenseRial = summary.averageMonthlyExpenseRial,
            priceMinRial = min,
            priceMaxRial = max,
            downPaymentRial = args.long("downPaymentRial"),
            currentSavingsRial = args.long("currentSavingsRial"),
            assumptions = assumptions,
        )
    }

    private fun incomeSavings(args: JsonObject, context: ToolExecutionContext): IncomeSavingsSummaryResult {
        val calendar = requireCalendar(args)
        val months = (args.int("months") ?: FinancialToolId.DEFAULT_LOOKBACK_MONTHS)
            .coerceIn(1, FinancialToolId.MAX_LOOKBACK_MONTHS)
        val range = FinancialPeriodSupport.resolveLastNMonths(months, context.today, calendar)
        return FinancialToolCalculator.incomeSavingsSummary(context.transactions, range, months)
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

    private fun isClarification(error: FinancialToolValidationException): Boolean =
        error.message?.contains("Clarification", ignoreCase = true) == true
}
