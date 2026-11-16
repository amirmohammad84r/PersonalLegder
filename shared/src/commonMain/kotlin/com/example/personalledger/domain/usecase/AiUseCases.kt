package com.example.personalledger.domain.usecase

import com.example.personalledger.data.ai.AiUsageQuotaStore
import com.example.personalledger.domain.IdGenerator
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.FinancialInsightSnapshot
import com.example.personalledger.domain.ai.orchestration.AiToolOrchestrator
import com.example.personalledger.domain.ai.quota.AiUsageQuotaPolicy
import com.example.personalledger.domain.ai.tools.ToolExecutionContext
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.ai.FinancialInsightCalculator
import com.example.personalledger.domain.nowInstant
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.AiAssistantRepository
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.todayLocalDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class BuildFinancialInsightUseCase(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
) {
    suspend operator fun invoke(calendar: CalendarSystem): FinancialInsightSnapshot {
        val accountList = accounts.observeAll().first()
        val goalList = goals.observeAll().first()
        return FinancialInsightCalculator.build(
            transactions = transactions.listAll(),
            goals = goalList,
            allocations = allocations.listAll(),
            accountCount = accountList.size,
            today = todayLocalDate(),
            calendar = calendar,
        )
    }
}

/**
 * Asks the assistant via tool orchestration:
 * quota check → plan without ledger payloads → optional history re-plan →
 * validate → local deterministic tools → explain aggregates.
 *
 * Quota: one user send consumes one unit before any Jasper HTTP call.
 * Planning + tools + explanation share that single unit.
 */
class AskAiAssistantUseCase(
    private val aiRepository: AiAssistantRepository,
    private val orchestrator: AiToolOrchestrator,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
    private val quotaStore: AiUsageQuotaStore,
) {
    suspend operator fun invoke(
        question: String,
        calendar: CalendarSystem,
    ): String {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) throw AiError.InvalidConfiguration("سؤال خالی است.")
        if (!aiRepository.isReady()) throw AiError.NotConfigured(aiRepository.statusMessage())

        val now = nowInstant().toEpochMilliseconds()
        val before = quotaStore.snapshot(now)
        if (before.remaining <= 0) {
            throw AiError.QuotaExceeded(quotaMessage(before))
        }
        // Consume before any Jasper HTTP so network retries cannot bypass the ceiling.
        quotaStore.tryConsume(now)

        val context = ToolExecutionContext(
            today = todayLocalDate(),
            preferredCalendar = calendar,
            transactions = transactions.listAll(),
            categories = categories.observeAll().first(),
            goals = goals.observeAll().first(),
            allocations = allocations.listAll(),
        )
        val result = orchestrator.ask(
            question = trimmed,
            context = context,
            toolCallingMode = aiRepository.toolCallingMode(),
        )
        val stamp = nowInstant().toEpochMilliseconds()
        aiRepository.appendChatMessage(
            AiChatMessage(
                id = IdGenerator.newId(),
                role = AiChatRole.USER,
                content = trimmed,
                createdAtEpochMs = stamp,
            ),
        )
        aiRepository.appendChatMessage(
            AiChatMessage(
                id = IdGenerator.newId(),
                role = AiChatRole.ASSISTANT,
                content = result,
                createdAtEpochMs = nowInstant().toEpochMilliseconds(),
            ),
        )
        return result
    }

    companion object {
        fun quotaMessage(snapshot: AiUsageQuotaPolicy.Snapshot): String {
            val whenText = snapshot.nextSlotAvailableAtEpochMs?.let { epoch ->
                val local = Instant.fromEpochMilliseconds(epoch)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                val hh = local.hour.toString().padStart(2, '0')
                val mm = local.minute.toString().padStart(2, '0')
                val date = "${local.year}/${local.monthNumber}/${local.dayOfMonth}"
                toPersianDigits("$date، ساعت $hh:$mm")
            } ?: "۲۴ ساعت آینده"
            return "سقف ۱۰ پیام در ۲۴ ساعت پر شده است. ارسال بعدی حدود $whenText آزاد می‌شود."
        }

        private fun toPersianDigits(raw: String): String = buildString(raw.length) {
            for (ch in raw) {
                append(
                    when (ch) {
                        '0' -> '۰'; '1' -> '۱'; '2' -> '۲'; '3' -> '۳'; '4' -> '۴'
                        '5' -> '۵'; '6' -> '۶'; '7' -> '۷'; '8' -> '۸'; '9' -> '۹'
                        else -> ch
                    },
                )
            }
        }
    }
}

class ResetAiAssistantUseCase(
    private val aiRepository: AiAssistantRepository,
) {
    operator fun invoke() {
        aiRepository.resetAssistant()
    }
}
