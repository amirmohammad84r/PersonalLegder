package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.IdGenerator
import com.example.personalledger.domain.ai.AiChatMessage
import com.example.personalledger.domain.ai.AiChatRole
import com.example.personalledger.domain.ai.AiError
import com.example.personalledger.domain.ai.AiPromptBuilder
import com.example.personalledger.domain.ai.FinancialInsightSnapshot
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.ai.FinancialInsightCalculator
import com.example.personalledger.domain.nowInstant
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.AiAssistantRepository
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.todayLocalDate
import kotlinx.coroutines.flow.first

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

class AskAiAssistantUseCase(
    private val aiRepository: AiAssistantRepository,
    private val buildInsight: BuildFinancialInsightUseCase,
) {
    suspend operator fun invoke(
        question: String,
        calendar: CalendarSystem,
    ): String {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) throw AiError.InvalidConfiguration("سؤال خالی است.")
        if (!aiRepository.isReady()) throw AiError.NotConfigured()

        val snapshot = buildInsight(calendar)
        val system = AiPromptBuilder.systemPrompt()
        val user = AiPromptBuilder.userPrompt(trimmed, snapshot)

        // Ask first with prior history only; persist turns after a successful reply.
        val result = aiRepository.ask(
            question = trimmed,
            systemPrompt = system,
            userPrompt = user,
        )
        val now = nowInstant().toEpochMilliseconds()
        aiRepository.appendChatMessage(
            AiChatMessage(
                id = IdGenerator.newId(),
                role = AiChatRole.USER,
                content = trimmed,
                createdAtEpochMs = now,
            ),
        )
        aiRepository.appendChatMessage(
            AiChatMessage(
                id = IdGenerator.newId(),
                role = AiChatRole.ASSISTANT,
                content = result.text,
                createdAtEpochMs = nowInstant().toEpochMilliseconds(),
            ),
        )
        return result.text
    }
}

class ResetAiAssistantUseCase(
    private val aiRepository: AiAssistantRepository,
) {
    operator fun invoke() {
        aiRepository.resetAssistant()
    }
}
