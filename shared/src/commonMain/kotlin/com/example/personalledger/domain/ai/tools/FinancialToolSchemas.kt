package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.ai.AiToolDefinition
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Versioned tool definitions + JSON Schema bodies for providers.
 * Schemas live in code; the model may only propose arguments matching these shapes.
 */
object FinancialToolSchemas {
    const val PROMPT_VERSION = "financial-tools-v2"

    fun definitions(): List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = FinancialToolId.GET_MONTHLY_FINANCIAL_SUMMARY.apiName,
            description = "Read-only monthly income/expense/balance totals for one calendar month. " +
                "Provide either year+month or period=CURRENT_MONTH|PREVIOUS_MONTH. Amounts are ریال.",
            parametersSchema = monthlySummarySchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.GET_AVERAGE_DAILY_EXPENSE.apiName,
            description = "Read-only average daily expense over a half-open date range. " +
                "Default denominatorPolicy for monthly questions is CALENDAR_DAYS.",
            parametersSchema = averageDailySchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.GET_EXPENSE_BREAKDOWN.apiName,
            description = "Read-only top expense categories for a half-open range. " +
                "limit is clamped to 1..10. Returns labels and totals only.",
            parametersSchema = expenseBreakdownSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.COMPARE_FINANCIAL_PERIODS.apiName,
            description = "Read-only compare of two half-open ranges for INCOME, EXPENSE, or BALANCE.",
            parametersSchema = compareSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.LIST_ACTIVE_GOALS.apiName,
            description = "Lists active goals with id and name only so a later get_goal_progress can use a valid id.",
            parametersSchema = emptyObjectSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.GET_GOAL_PROGRESS.apiName,
            description = "Read-only progress for one goal by allowlisted goalId from list_active_goals.",
            parametersSchema = goalProgressSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.GET_INCOME_SAVINGS_SUMMARY.apiName,
            description = "Read-only income/expense/net balance over the last N calendar months (1..12). " +
                "Net is labeled as income−expense balance, NOT proven savings. Amounts are ریال.",
            parametersSchema = incomeSavingsSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.SEARCH_CURRENT_PROPERTY_PRICES.apiName,
            description = "Search current property asking prices for a user-stated city/area. " +
                "Do not invent neighborhood. Does not send financial/history data. " +
                "Returns citations with URL and retrieval time when a search provider is configured.",
            parametersSchema = propertySearchSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.ESTIMATE_HOME_PURCHASE_TIMELINE.apiName,
            description = "Deterministic scenario estimate for months to afford a home using " +
                "local income/expense averages and price range from a prior search_current_property_prices " +
                "in this turn. Does not invent market prices.",
            parametersSchema = homePurchaseEstimateSchema(),
        ),
        AiToolDefinition(
            name = FinancialToolId.REQUEST_CHAT_HISTORY.apiName,
            description = "Ask the app to attach up to 6 recent AI chat turns on a re-plan. " +
                "Use only when prior assistant/user messages are required to resolve the question. " +
                "Requires user history consent in settings.",
            parametersSchema = historyRequestSchema(),
        ),
    )

    private fun monthlySummarySchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("year") { put("type", "integer") }
            putJsonObject("month") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 12)
            }
            putJsonObject("period") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CURRENT_MONTH"))
                    add(JsonPrimitive("PREVIOUS_MONTH"))
                }
            }
            putJsonObject("transactionScope") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("INCOME"))
                    add(JsonPrimitive("EXPENSE"))
                    add(JsonPrimitive("ALL"))
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("calendar")) }
        put("additionalProperties", false)
    }

    private fun averageDailySchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("startInclusive") {
                put("type", "string")
                put("description", "YYYY-MM-DD Gregorian storage date")
            }
            putJsonObject("endExclusive") {
                put("type", "string")
                put("description", "YYYY-MM-DD Gregorian storage date, exclusive")
            }
            putJsonObject("year") { put("type", "integer") }
            putJsonObject("month") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 12)
            }
            putJsonObject("period") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CURRENT_MONTH"))
                    add(JsonPrimitive("PREVIOUS_MONTH"))
                }
            }
            putJsonObject("denominatorPolicy") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CALENDAR_DAYS"))
                    add(JsonPrimitive("DAYS_WITH_TRANSACTIONS"))
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("calendar")) }
        put("additionalProperties", false)
    }

    private fun expenseBreakdownSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("startInclusive") { put("type", "string") }
            putJsonObject("endExclusive") { put("type", "string") }
            putJsonObject("year") { put("type", "integer") }
            putJsonObject("month") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 12)
            }
            putJsonObject("period") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CURRENT_MONTH"))
                    add(JsonPrimitive("PREVIOUS_MONTH"))
                }
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", FinancialToolId.MAX_BREAKDOWN_LIMIT)
            }
        }
        putJsonArray("required") { add(JsonPrimitive("calendar")) }
        put("additionalProperties", false)
    }

    private fun compareSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("metric") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("INCOME"))
                    add(JsonPrimitive("EXPENSE"))
                    add(JsonPrimitive("BALANCE"))
                }
            }
            putJsonObject("firstYear") { put("type", "integer") }
            putJsonObject("firstMonth") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 12)
            }
            putJsonObject("secondYear") { put("type", "integer") }
            putJsonObject("secondMonth") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", 12)
            }
            putJsonObject("firstPeriod") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CURRENT_MONTH"))
                    add(JsonPrimitive("PREVIOUS_MONTH"))
                }
            }
            putJsonObject("secondPeriod") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("CURRENT_MONTH"))
                    add(JsonPrimitive("PREVIOUS_MONTH"))
                }
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("calendar"))
            add(JsonPrimitive("metric"))
        }
        put("additionalProperties", false)
    }

    private fun goalProgressSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("goalId") {
                put("type", "string")
                put("minLength", 1)
                put("maxLength", 64)
            }
        }
        putJsonArray("required") { add(JsonPrimitive("goalId")) }
        put("additionalProperties", false)
    }

    private fun emptyObjectSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        put("additionalProperties", false)
    }

    private fun incomeSavingsSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("months") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", FinancialToolId.MAX_LOOKBACK_MONTHS)
                put("description", "How many calendar months ending at current month")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("calendar")) }
        put("additionalProperties", false)
    }

    private fun propertySearchSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("locationQuery") {
                put("type", "string")
                put("minLength", 2)
                put("maxLength", FinancialToolId.MAX_SEARCH_QUERY_CHARS)
                put("description", "City/area explicitly stated or confirmed by the user")
            }
            putJsonObject("propertyType") {
                put("type", "string")
                put("maxLength", 40)
            }
            putJsonObject("areaSqmMin") { put("type", "integer") }
            putJsonObject("areaSqmMax") { put("type", "integer") }
        }
        putJsonArray("required") { add(JsonPrimitive("locationQuery")) }
        put("additionalProperties", false)
    }

    private fun homePurchaseEstimateSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("calendar") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("JALALI"))
                    add(JsonPrimitive("GREGORIAN"))
                }
            }
            putJsonObject("monthsLookback") {
                put("type", "integer")
                put("minimum", 1)
                put("maximum", FinancialToolId.MAX_LOOKBACK_MONTHS)
            }
            putJsonObject("downPaymentRial") {
                put("type", "integer")
                put("description", "Only if the user explicitly stated a down-payment in ریال")
            }
            putJsonObject("currentSavingsRial") {
                put("type", "integer")
                put("description", "Only if the user explicitly stated current savings in ریال")
            }
            putJsonObject("surplusAssumption") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("ALL_MONTHLY_BALANCE"))
                    add(JsonPrimitive("HALF_MONTHLY_BALANCE"))
                    add(JsonPrimitive("BOTH"))
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("calendar")) }
        put("additionalProperties", false)
    }

    private fun historyRequestSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("history_scope") {
                put("type", "string")
                putJsonArray("enum") {
                    add(JsonPrimitive("RECENT_RELEVANT"))
                }
            }
            putJsonObject("history_required") {
                put("type", "boolean")
            }
        }
        put("additionalProperties", false)
    }
}
