package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.BalanceCalculator
import com.example.personalledger.domain.IdGenerator
import com.example.personalledger.domain.goals.GoalProgressCalculator
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalProgress
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.nowInstant
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.repository.GoalAllocationDraft
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.GoalDraft
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

class ObserveActiveGoalsUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
) {
    operator fun invoke(): Flow<List<GoalProgress>> =
        combine(goals.observeAll(), allocations.observeAll()) { goalList, allocList ->
            GoalProgressCalculator.progressList(
                goals = goalList.filter {
                    it.status == GoalStatus.ACTIVE || it.status == GoalStatus.COMPLETED
                },
                allocations = allocList,
            ).sortedWith(
                compareBy<GoalProgress> { it.goal.status != GoalStatus.ACTIVE }
                    .thenByDescending { it.goal.updatedAt },
            )
        }
}

class ObserveArchivedGoalsUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
) {
    operator fun invoke(): Flow<List<GoalProgress>> =
        combine(goals.observeAll(), allocations.observeAll()) { goalList, allocList ->
            GoalProgressCalculator.progressList(
                goals = goalList.filter { it.status == GoalStatus.ARCHIVED },
                allocations = allocList,
            ).sortedByDescending { it.goal.updatedAt }
        }
}

class ObserveGoalDetailUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
    private val accounts: AccountRepository,
) {
    data class Detail(
        val progress: GoalProgress,
        val allocations: List<GoalAllocation>,
        val accountNames: Map<String, String>,
    )

    operator fun invoke(goalId: String): Flow<Detail?> =
        combine(
            goals.observeAll(),
            allocations.observeByGoalId(goalId),
            accounts.observeAll(),
        ) { goalList, allocs, accs ->
            val goal = goalList.find { it.id == goalId } ?: return@combine null
            Detail(
                progress = GoalProgressCalculator.progress(goal, allocs),
                allocations = allocs,
                accountNames = accs.associate { it.id to it.name },
            )
        }
}

class SaveGoalUseCase(
    private val goals: GoalRepository,
) {
    suspend operator fun invoke(draft: GoalDraft): Goal {
        val name = draft.name.trim()
        if (name.isEmpty()) {
            throw DomainException.Validation("نام هدف را وارد کنید")
        }
        val target = draft.targetAmountRial
            ?: throw DomainException.Validation("مبلغ هدف را وارد کنید")
        if (target <= 0L) {
            throw DomainException.Validation("مبلغ هدف باید بیشتر از صفر باشد")
        }
        val now = nowInstant()
        val existing = draft.id?.let { goals.getById(it) }
        val entity = Goal(
            id = existing?.id ?: IdGenerator.newId(),
            name = name,
            targetAmountRial = target,
            targetDate = draft.targetDateEpochDays?.let { LocalDate.fromEpochDays(it.toInt()) },
            iconKey = draft.iconKey?.takeIf { it.isNotBlank() },
            status = existing?.status ?: GoalStatus.ACTIVE,
            completedAt = existing?.completedAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        goals.upsert(entity)
        return entity
    }
}

class SaveGoalAllocationUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
) {
    /**
     * Reserves amount from an account toward the goal.
     * Reduces available balance via BalanceCalculator; no tx is created.
     */
    suspend operator fun invoke(draft: GoalAllocationDraft): GoalAllocation {
        val goal = goals.getById(draft.goalId)
            ?: throw DomainException.NotFound("هدف پیدا نشد")
        if (goal.status == GoalStatus.ARCHIVED) {
            throw DomainException.Validation("هدف بایگانی‌شده قابل ویرایش نیست")
        }
        val accountId = draft.accountId?.takeIf { it.isNotBlank() }
            ?: throw DomainException.Validation("حساب را انتخاب کنید")
        val account = accounts.getById(accountId)
            ?: throw DomainException.NotFound("حساب پیدا نشد")
        val amount = draft.amountRial
            ?: throw DomainException.Validation("مبلغ را وارد کنید")
        if (amount <= 0L) {
            throw DomainException.Validation("مبلغ باید بیشتر از صفر باشد")
        }
        val dateDays = draft.dateEpochDays
            ?: throw DomainException.Validation("تاریخ را انتخاب کنید")

        val available = BalanceCalculator.availableForGoalAllocation(
            account = account,
            transactions = transactions.listAll(),
            goalAllocations = allocations.listAll(),
            excludeAllocationId = draft.id,
        )
        if (amount > available) {
            throw DomainException.Validation(
                "موجودی این حساب کافی نیست (قابل اختصاص: $available ریال)",
            )
        }

        val now = nowInstant()
        val existing = draft.id?.let { allocations.getById(it) }
        val entity = GoalAllocation(
            id = existing?.id ?: IdGenerator.newId(),
            goalId = draft.goalId,
            accountId = accountId,
            amountRial = amount,
            date = LocalDate.fromEpochDays(dateDays.toInt()),
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        allocations.upsert(entity)
        maybeAutoComplete(goal)
        return entity
    }

    private suspend fun maybeAutoComplete(goal: Goal) {
        if (goal.status != GoalStatus.ACTIVE) return
        val related = allocations.listByGoalId(goal.id)
        val progress = GoalProgressCalculator.progress(goal, related)
        if (progress.isTargetReached) {
            goals.upsert(
                goal.copy(
                    status = GoalStatus.COMPLETED,
                    completedAt = nowInstant(),
                    updatedAt = nowInstant(),
                ),
            )
        }
    }
}

class DeleteGoalAllocationUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
) {
    /** Removes reservation; amount becomes available on the account again. */
    suspend operator fun invoke(id: String) {
        val allocation = allocations.getById(id)
            ?: throw DomainException.NotFound("مبلغ پیدا نشد")
        allocations.delete(id)
        val goal = goals.getById(allocation.goalId) ?: return
        if (goal.status != GoalStatus.COMPLETED) return
        val related = allocations.listByGoalId(goal.id)
        val progress = GoalProgressCalculator.progress(goal, related)
        if (!progress.isTargetReached) {
            goals.upsert(
                goal.copy(
                    status = GoalStatus.ACTIVE,
                    completedAt = null,
                    updatedAt = nowInstant(),
                ),
            )
        }
    }
}

class CompleteGoalUseCase(
    private val goals: GoalRepository,
) {
    suspend operator fun invoke(id: String): Goal {
        val goal = goals.getById(id) ?: throw DomainException.NotFound("هدف پیدا نشد")
        if (goal.status == GoalStatus.ARCHIVED) {
            throw DomainException.Validation("هدف بایگانی‌شده را نمی‌توان تکمیل کرد")
        }
        val now = nowInstant()
        val updated = goal.copy(
            status = GoalStatus.COMPLETED,
            completedAt = goal.completedAt ?: now,
            updatedAt = now,
        )
        goals.upsert(updated)
        return updated
    }
}

class ArchiveGoalUseCase(
    private val goals: GoalRepository,
) {
    suspend operator fun invoke(id: String): Goal {
        val goal = goals.getById(id) ?: throw DomainException.NotFound("هدف پیدا نشد")
        val updated = goal.copy(
            status = GoalStatus.ARCHIVED,
            updatedAt = nowInstant(),
        )
        goals.upsert(updated)
        return updated
    }
}

class RestoreGoalUseCase(
    private val goals: GoalRepository,
) {
    suspend operator fun invoke(id: String): Goal {
        val goal = goals.getById(id) ?: throw DomainException.NotFound("هدف پیدا نشد")
        if (goal.status != GoalStatus.ARCHIVED) {
            throw DomainException.Validation("فقط هدف بایگانی‌شده قابل بازگردانی است")
        }
        val updated = goal.copy(
            status = GoalStatus.ACTIVE,
            completedAt = null,
            updatedAt = nowInstant(),
        )
        goals.upsert(updated)
        return updated
    }
}

class DeleteGoalUseCase(
    private val goals: GoalRepository,
    private val allocations: GoalAllocationRepository,
) {
    suspend fun relatedAllocationCount(id: String): Int =
        allocations.countByGoalId(id)

    /** Deletes goal + allocations; reserved amounts return to accounts. */
    suspend operator fun invoke(id: String) {
        goals.getById(id) ?: throw DomainException.NotFound("هدف پیدا نشد")
        allocations.deleteByGoalId(id)
        goals.delete(id)
    }
}
