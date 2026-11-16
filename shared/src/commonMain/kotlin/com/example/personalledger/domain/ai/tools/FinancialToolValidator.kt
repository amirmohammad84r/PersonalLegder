package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Validates model-proposed tool calls against the allowlist and argument domain rules.
 * Never executes queries; only produces validated calls or rejections.
 */
object FinancialToolValidator {
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    fun validateProposedCalls(
        proposed: List<ProposedToolCall>,
        maxCalls: Int = FinancialToolId.MAX_CALLS_PER_QUESTION,
    ): List<ValidatedToolCall> {
        if (proposed.isEmpty()) {
            throw FinancialToolValidationException("No tool calls proposed.")
        }
        val seenFingerprints = mutableSetOf<String>()
        var budgeted = 0
        return proposed.map { call ->
            val toolId = FinancialToolId.fromApiName(call.name)
                ?: throw FinancialToolUnknownException("Unknown tool: ${call.name}")
            if (toolId.countsAgainstToolBudget()) {
                budgeted++
                if (budgeted > maxCalls) {
                    throw FinancialToolBudgetExceededException(
                        "Too many tool calls ($budgeted); max is $maxCalls shared across financial and web tools.",
                    )
                }
            }
            val fingerprint = "${toolId.apiName}|${call.argumentsJson.trim()}"
            if (!seenFingerprints.add(fingerprint)) {
                throw FinancialToolBudgetExceededException(
                    "Duplicate tool call detected for ${toolId.apiName}.",
                )
            }
            // Structural parse only — deep domain checks happen per-tool in executor.
            parseObject(call.argumentsJson)
            ValidatedToolCall(
                id = call.id.ifBlank { "call_${toolId.apiName}" },
                toolId = toolId,
                argumentsJson = call.argumentsJson.trim().ifBlank { "{}" },
            )
        }
    }

    fun parseObject(raw: String): JsonObject {
        val trimmed = raw.trim().ifBlank { "{}" }
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrElse {
            throw FinancialToolValidationException("Tool arguments are not valid JSON.")
        }
        return element as? JsonObject
            ?: throw FinancialToolValidationException("Tool arguments must be a JSON object.")
    }

    fun requireCalendar(obj: JsonObject): CalendarSystem {
        val raw = obj.string("calendar")
            ?: throw FinancialToolValidationException("calendar is required.")
        return runCatching { CalendarSystem.valueOf(raw) }.getOrElse {
            throw FinancialToolValidationException("calendar must be JALALI or GREGORIAN.")
        }
    }

    fun optionalScope(obj: JsonObject): ToolTransactionScope {
        val raw = obj.string("transactionScope") ?: return ToolTransactionScope.ALL
        return runCatching { ToolTransactionScope.valueOf(raw) }.getOrElse {
            throw FinancialToolValidationException("Invalid transactionScope.")
        }
    }

    fun optionalDenominator(obj: JsonObject): AverageDenominatorPolicy {
        val raw = obj.string("denominatorPolicy") ?: return AverageDenominatorPolicy.CALENDAR_DAYS
        return runCatching { AverageDenominatorPolicy.valueOf(raw) }.getOrElse {
            throw FinancialToolValidationException("Invalid denominatorPolicy.")
        }
    }

    fun optionalLimit(obj: JsonObject): Int {
        val value = obj.int("limit") ?: return FinancialToolId.DEFAULT_BREAKDOWN_LIMIT
        if (value !in 1..FinancialToolId.MAX_BREAKDOWN_LIMIT) {
            throw FinancialToolValidationException(
                "limit must be 1..${FinancialToolId.MAX_BREAKDOWN_LIMIT}.",
            )
        }
        return value
    }

    fun requireMetric(obj: JsonObject): CompareMetric {
        val raw = obj.string("metric")
            ?: throw FinancialToolValidationException("metric is required.")
        return runCatching { CompareMetric.valueOf(raw) }.getOrElse {
            throw FinancialToolValidationException("Invalid metric.")
        }
    }

    fun requireGoalId(obj: JsonObject): String {
        val id = obj.string("goalId")?.trim().orEmpty()
        if (id.isEmpty() || id.length > 64) {
            throw FinancialToolValidationException("goalId is missing or too long.")
        }
        if (id.any { it.isWhitespace() || it == '\n' || it == '\r' }) {
            throw FinancialToolValidationException("goalId contains invalid characters.")
        }
        return id
    }

    fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    fun JsonObject.long(key: String): Long? =
        this[key]?.jsonPrimitive?.longOrNull

    fun JsonObject.boolean(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull
}
