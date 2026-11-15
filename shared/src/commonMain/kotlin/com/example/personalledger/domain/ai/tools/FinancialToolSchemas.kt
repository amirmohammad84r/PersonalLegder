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
    const val PROMPT_VERSION = "financial-tools-v1"

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
}
