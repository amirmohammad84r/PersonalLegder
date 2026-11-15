package com.example.personalledger.domain.ai.tools

/**
 * Hard allowlist of read-only financial tools.
 * Unknown names from the model are rejected before any execution.
 */
enum class FinancialToolId(val apiName: String) {
    GET_MONTHLY_FINANCIAL_SUMMARY("get_monthly_financial_summary"),
    GET_AVERAGE_DAILY_EXPENSE("get_average_daily_expense"),
    GET_EXPENSE_BREAKDOWN("get_expense_breakdown"),
    COMPARE_FINANCIAL_PERIODS("compare_financial_periods"),
    LIST_ACTIVE_GOALS("list_active_goals"),
    GET_GOAL_PROGRESS("get_goal_progress"),
    ;

    companion object {
        const val MAX_CALLS_PER_QUESTION = 3
        const val MAX_BREAKDOWN_LIMIT = 10
        const val DEFAULT_BREAKDOWN_LIMIT = 5
        /** Inclusive upper bound on half-open range length in calendar days. */
        const val MAX_RANGE_DAYS = 400
        const val MIN_YEAR = 1300
        const val MAX_YEAR = 2100

        private val byName = entries.associateBy { it.apiName }

        fun fromApiName(name: String): FinancialToolId? = byName[name.trim()]

        fun isAllowed(name: String): Boolean = fromApiName(name) != null
    }
}

enum class ToolTransactionScope {
    INCOME,
    EXPENSE,
    ALL,
}

enum class NamedMonthPeriod {
    CURRENT_MONTH,
    PREVIOUS_MONTH,
}

enum class AverageDenominatorPolicy {
    /** Divide by every calendar day in the half-open range (default for monthly questions). */
    CALENDAR_DAYS,
    /** Divide only by days that have at least one expense transaction. */
    DAYS_WITH_TRANSACTIONS,
}

enum class CompareMetric {
    INCOME,
    EXPENSE,
    BALANCE,
}

enum class MoneyRoundingPolicy {
    /** Integer division truncating toward zero for non-negative amounts. */
    FLOOR_TOWARD_ZERO,
}
