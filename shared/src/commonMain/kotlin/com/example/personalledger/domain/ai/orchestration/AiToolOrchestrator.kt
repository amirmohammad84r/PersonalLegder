package com.example.personalledger.domain.ai.orchestration

import com.example.personalledger.domain.ai.AiCompletionMessage
import com.example.personalledger.domain.ai.AiCompletionRequest
import com.example.personalledger.domain.ai.AiCompletionResult
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiProposedToolCall
import com.example.personalledger.domain.ai.AiToolCallingMode
import com.example.personalledger.domain.ai.tools.FinancialToolBudgetExceededException
import com.example.personalledger.domain.ai.tools.FinancialToolExecutor
import com.example.personalledger.domain.ai.tools.FinancialToolId
import com.example.personalledger.domain.ai.tools.FinancialToolSchemas
import com.example.personalledger.domain.ai.tools.FinancialToolUnknownException
import com.example.personalledger.domain.ai.tools.FinancialToolValidationException
import com.example.personalledger.domain.ai.tools.FinancialToolValidator
import com.example.personalledger.domain.ai.tools.ProposedToolCall
import com.example.personalledger.domain.ai.tools.ToolExecutionContext
import com.example.personalledger.domain.ai.tools.ToolExecutionOutcome
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.repository.AiAssistantRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LangChain-style orchestration without LangChain4j:
 * plan (tools or structured JSON) → validate → execute allowlisted local tools → explain.
 *
 * Financial calculations never leave the device process; the model only sees aggregates.
 */
class AiToolOrchestrator(
    private val aiRepository: AiAssistantRepository,
    private val executor: FinancialToolExecutor = FinancialToolExecutor(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = false },
) {
    data class OrchestrationDebugSnapshot(
        val planningMessages: List<AiCompletionMessage>,
        val planningHadTools: Boolean,
        val planningContainedTransactionPayload: Boolean,
        val explanationMessages: List<AiCompletionMessage>?,
        val explanationHadOnlyAggregates: Boolean?,
        val executedToolNames: List<String>,
    )

    var lastDebugSnapshot: OrchestrationDebugSnapshot? = null
        private set

    suspend fun ask(
        question: String,
        context: ToolExecutionContext,
        toolCallingMode: AiToolCallingMode,
    ): String {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) throw AiError.InvalidConfiguration("سؤال خالی است.")

        val todayLabel = AppCalendar.formatFull(context.today, context.preferredCalendar)
        val structured = toolCallingMode == AiToolCallingMode.STRUCTURED_JSON_PLAN
        val planningSystem = OrchestrationPrompts.planningSystemPrompt(
            preferredCalendar = context.preferredCalendar,
            todayLabel = todayLabel,
            structuredJsonFallback = structured,
        )
        val history = aiRepository.readRecentChatForProvider(limit = 6)
            .map {
                when (it.role) {
                    com.example.personalledger.domain.ai.AiChatRole.USER ->
                        AiCompletionMessage.User(it.content)
                    com.example.personalledger.domain.ai.AiChatRole.ASSISTANT ->
                        AiCompletionMessage.Assistant(content = it.content)
                }
            }

        val planningMessages = buildList {
            add(AiCompletionMessage.System(planningSystem))
            addAll(history)
            add(AiCompletionMessage.User(OrchestrationPrompts.planningUserPrompt(trimmed)))
        }

        val connection = aiRepository.readConnection()
            ?: throw AiError.NotConfigured(aiRepository.statusMessage())
        val tools = FinancialToolSchemas.definitions()
        val planningRequest = AiCompletionRequest(
            providerId = connection.providerId,
            modelId = connection.modelId,
            apiKey = "", // filled by repository from embedded secrets
            messages = planningMessages,
            tools = if (structured) null else tools,
            enableTools = !structured,
            disableThinkingForTools = !structured,
        )

        val planningHadTx = RequestPrivacyInspector.containsForbiddenFinancialPayload(planningMessages)
        val planResult = aiRepository.complete(planningRequest)

        val planDecision = when (toolCallingMode) {
            AiToolCallingMode.OPENAI_COMPATIBLE_TOOLS -> {
                if (planResult.toolCalls.isNotEmpty()) {
                    PlanDecision.ToolCalls(planResult.toolCalls)
                } else {
                    PlanDecision.FinalText(planResult.text?.trim().orEmpty())
                }
            }
            AiToolCallingMode.STRUCTURED_JSON_PLAN ->
                parseStructuredPlan(planResult.text ?: planResult.rawAssistantContent)
        }

        if (planDecision is PlanDecision.FinalText) {
            lastDebugSnapshot = OrchestrationDebugSnapshot(
                planningMessages = planningMessages,
                planningHadTools = planningRequest.enableTools,
                planningContainedTransactionPayload = planningHadTx,
                explanationMessages = null,
                explanationHadOnlyAggregates = null,
                executedToolNames = emptyList(),
            )
            if (planDecision.text.isBlank()) {
                throw AiError.Provider("پاسخ خالی از مدل دریافت شد.")
            }
            return planDecision.text
        }

        val proposed = (planDecision as PlanDecision.ToolCalls).calls

        val validated = try {
            FinancialToolValidator.validateProposedCalls(
                proposed.map {
                    ProposedToolCall(id = it.id, name = it.name, argumentsJson = it.argumentsJson)
                },
            )
        } catch (error: FinancialToolBudgetExceededException) {
            throw AiError.InvalidConfiguration(error.message ?: "سقف فراخوانی ابزار پر شد.")
        } catch (error: FinancialToolUnknownException) {
            throw AiError.InvalidConfiguration(error.message ?: "ابزار ناشناخته رد شد.")
        } catch (error: FinancialToolValidationException) {
            throw AiError.InvalidConfiguration(error.message ?: "ورودی ابزار نامعتبر است.")
        }

        val outcomes = validated.map { call -> executor.execute(call, context) }
        val clarifications = outcomes.filterIsInstance<ToolExecutionOutcome.ClarificationNeeded>()
        if (clarifications.isNotEmpty()) {
            val message = clarifications.joinToString("\n") { it.message }
            lastDebugSnapshot = OrchestrationDebugSnapshot(
                planningMessages = planningMessages,
                planningHadTools = planningRequest.enableTools,
                planningContainedTransactionPayload = planningHadTx,
                explanationMessages = null,
                explanationHadOnlyAggregates = null,
                executedToolNames = outcomes.map { outcomeName(it) },
            )
            return OrchestrationPrompts.clarificationFromAmbiguity(message)
        }
        val rejected = outcomes.filterIsInstance<ToolExecutionOutcome.Rejected>()
        if (rejected.isNotEmpty() && outcomes.none { it is ToolExecutionOutcome.Success }) {
            throw AiError.InvalidConfiguration(
                rejected.joinToString("; ") { it.reason },
            )
        }

        val successes = outcomes.filterIsInstance<ToolExecutionOutcome.Success>()
        val explanationMessages = buildExplanationMessages(
            question = trimmed,
            planningMessages = planningMessages,
            planResult = planResult,
            successes = successes,
            mode = toolCallingMode,
        )
        val explainRequest = AiCompletionRequest(
            providerId = planningRequest.providerId,
            modelId = planningRequest.modelId,
            apiKey = "",
            messages = explanationMessages,
            tools = null,
            enableTools = false,
            disableThinkingForTools = false,
        )
        val explainResult = aiRepository.complete(explainRequest)
        val answer = explainResult.text?.trim().orEmpty()
        lastDebugSnapshot = OrchestrationDebugSnapshot(
            planningMessages = planningMessages,
            planningHadTools = planningRequest.enableTools,
            planningContainedTransactionPayload = planningHadTx,
            explanationMessages = explanationMessages,
            explanationHadOnlyAggregates =
                RequestPrivacyInspector.explanationUsesOnlyToolAggregates(explanationMessages),
            executedToolNames = successes.map { it.toolName },
        )
        if (answer.isBlank()) throw AiError.Provider("پاسخ نهایی خالی بود.")
        return answer
    }

    private fun buildExplanationMessages(
        question: String,
        planningMessages: List<AiCompletionMessage>,
        planResult: AiCompletionResult,
        successes: List<ToolExecutionOutcome.Success>,
        mode: AiToolCallingMode,
    ): List<AiCompletionMessage> {
        val system = AiCompletionMessage.System(OrchestrationPrompts.explanationSystemPrompt())
        return when (mode) {
            AiToolCallingMode.OPENAI_COMPATIBLE_TOOLS -> {
                buildList {
                    add(system)
                    // Keep user question only from planning (no history dump of tools).
                    add(AiCompletionMessage.User(OrchestrationPrompts.planningUserPrompt(question)))
                    add(
                        AiCompletionMessage.Assistant(
                            content = planResult.text,
                            toolCalls = planResult.toolCalls,
                        ),
                    )
                    successes.forEach { success ->
                        add(
                            AiCompletionMessage.ToolResult(
                                toolCallId = success.toolCallId,
                                content = success.resultJson,
                            ),
                        )
                    }
                }
            }
            AiToolCallingMode.STRUCTURED_JSON_PLAN -> {
                val toolBlock = successes.joinToString(separator = "\n") {
                    "TOOL ${it.toolName}: ${it.resultJson}"
                }
                listOf(
                    system,
                    AiCompletionMessage.User(
                        buildString {
                            appendLine(OrchestrationPrompts.planningUserPrompt(question))
                            appendLine()
                            appendLine("TOOL_RESULTS (machine-computed aggregates only, unit=ریال):")
                            append(toolBlock)
                        },
                    ),
                )
            }
        }.also {
            // planningMessages retained for debug privacy assertions only
            @Suppress("UNUSED_EXPRESSION")
            planningMessages
        }
    }

    private sealed class PlanDecision {
        data class ToolCalls(val calls: List<AiProposedToolCall>) : PlanDecision()
        data class FinalText(val text: String) : PlanDecision()
    }

    private fun parseStructuredPlan(raw: String?): PlanDecision {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return PlanDecision.FinalText("")
        val jsonText = extractJsonObject(text)
            ?: return PlanDecision.FinalText(text)
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return PlanDecision.FinalText(text)
        val action = root["action"]?.jsonPrimitive?.contentOrNull?.lowercase()
        when (action) {
            "clarify", "refuse" -> {
                val message = root["message"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                return PlanDecision.FinalText(message.ifBlank { text })
            }
            "tool_calls" -> Unit
            else -> {
                if (root["calls"] == null) return PlanDecision.FinalText(text)
            }
        }
        val calls = root["calls"] as? JsonArray
            ?: return PlanDecision.FinalText(text)
        if (calls.size > FinancialToolId.MAX_CALLS_PER_QUESTION) {
            throw AiError.InvalidConfiguration(
                "Too many tool calls in structured plan.",
            )
        }
        val proposed = calls.mapIndexed { index, element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val arguments = obj["arguments"]
            val argsJson = when (arguments) {
                is JsonObject -> arguments.toString()
                else -> arguments?.jsonPrimitive?.contentOrNull ?: "{}"
            }
            if (!FinancialToolId.isAllowed(name)) {
                throw AiError.InvalidConfiguration("Unknown tool in structured plan: $name")
            }
            AiProposedToolCall(
                id = "structured_$index",
                name = name,
                argumentsJson = argsJson,
            )
        }
        return PlanDecision.ToolCalls(proposed)
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun outcomeName(outcome: ToolExecutionOutcome): String = when (outcome) {
        is ToolExecutionOutcome.Success -> outcome.toolName
        is ToolExecutionOutcome.ClarificationNeeded -> outcome.toolName
        is ToolExecutionOutcome.Rejected -> outcome.toolName
    }
}

/**
 * Privacy assertions used by orchestration and unit tests.
 */
object RequestPrivacyInspector {
    private val forbiddenKeys = listOf(
        "FINANCIAL_CONTEXT",
        "raw_transactions",
        "transaction_title",
        "sms_body",
        "card_number",
        "account_name",
        "income_rial=",
        "expense_rial=",
    )

    fun containsForbiddenFinancialPayload(messages: List<AiCompletionMessage>): Boolean {
        val blob = messages.joinToString("\n") { messageText(it) }
        return forbiddenKeys.any { blob.contains(it, ignoreCase = true) }
    }

    fun explanationUsesOnlyToolAggregates(messages: List<AiCompletionMessage>): Boolean {
        // Explanation may include TOOL_RESULTS / tool role payloads, but not raw ledger dumps.
        return !containsForbiddenFinancialPayload(messages.filterIsInstance<AiCompletionMessage.System>()) &&
            messages.none { messageText(it).contains("FINANCIAL_CONTEXT") }
    }

    private fun messageText(message: AiCompletionMessage): String = when (message) {
        is AiCompletionMessage.System -> message.content
        is AiCompletionMessage.User -> message.content
        is AiCompletionMessage.Assistant ->
            (message.content.orEmpty()) + message.toolCalls.joinToString { it.argumentsJson }
        is AiCompletionMessage.ToolResult -> message.content
    }
}
