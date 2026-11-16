package com.example.personalledger.domain.ai.tools

/**
 * Hard allowlist of read-only financial / search / meta tools.
 * Unknown names from the model are rejected before any execution.
 */
enum class FinancialToolId(val apiName: String) {
    GET_MONTHLY_FINANCIAL_SUMMARY("get_monthly_financial_summary"),
    GET_AVERAGE_DAILY_EXPENSE("get_average_daily_expense"),
    GET_EXPENSE_BREAKDOWN("get_expense_breakdown"),
    COMPARE_FINANCIAL_PERIODS("compare_financial_periods"),
    LIST_ACTIVE_GOALS("list_active_goals"),
    GET_GOAL_PROGRESS("get_goal_progress"),
    GET_INCOME_SAVINGS_SUMMARY("get_income_savings_summary"),
    SEARCH_CURRENT_PROPERTY_PRICES("search_current_property_prices"),
    ESTIMATE_HOME_PURCHASE_TIMELINE("estimate_home_purchase_timeline"),
    /** Meta: asks the app to attach recent AI chat turns on a re-plan. Not a data tool. */
    REQUEST_CHAT_HISTORY("request_chat_history"),
    ;

    fun isMetaHistoryRequest(): Boolean = this == REQUEST_CHAT_HISTORY

    fun countsAgainstToolBudget(): Boolean = !isMetaHistoryRequest()

    companion object {
        const val MAX_CALLS_PER_QUESTION = 3
        const val MAX_BREAKDOWN_LIMIT = 10
        const val DEFAULT_BREAKDOWN_LIMIT = 5
        const val MAX_RANGE_DAYS = 400
        const val MIN_YEAR = 1300
        const val MAX_YEAR = 2100
        const val MAX_LOOKBACK_MONTHS = 12
        const val DEFAULT_LOOKBACK_MONTHS = 6
        const val MAX_HISTORY_MESSAGES = 6
        const val MAX_SEARCH_QUERY_CHARS = 120
        const val MAX_SEARCH_RESULTS = 5

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
    CALENDAR_DAYS,
    DAYS_WITH_TRANSACTIONS,
}

enum class CompareMetric {
    INCOME,
    EXPENSE,
    BALANCE,
}

enum class MoneyRoundingPolicy {
    FLOOR_TOWARD_ZERO,
}

enum class HistoryScope {
    NONE,
    RECENT_RELEVANT,
}
