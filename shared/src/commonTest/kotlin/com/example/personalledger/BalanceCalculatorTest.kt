package com.example.personalledger

import com.example.personalledger.domain.BalanceCalculator
import com.example.personalledger.domain.goals.GoalProgressCalculator
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BalanceCalculatorTest {
    private val now = Instant.fromEpochMilliseconds(0)
    private val day = LocalDate(2026, 1, 1)

    @Test
    fun totalBalance_sumsInitialAndNetTransactions() {
        val accounts = listOf(
            Account("a1", "نقدی", 100_000),
            Account("a2", "بانک", 50_000),
        )
        val txs = listOf(
            tx("1", TransactionType.INCOME, 20_000, "a1"),
            tx("2", TransactionType.EXPENSE, 5_000, "a2"),
        )
        assertEquals(165_000, BalanceCalculator.totalBalance(accounts, txs))
        assertEquals(120_000, BalanceCalculator.accountBalance(accounts[0], txs))
        assertEquals(45_000, BalanceCalculator.accountBalance(accounts[1], txs))
    }

    @Test
    fun goalAllocation_reducesAccountBalance_andReturnsWhenRemoved() {
        val accounts = listOf(Account("a1", "نقدی", 100_000))
        val txs = listOf(tx("1", TransactionType.EXPENSE, 10_000, "a1"))
        val beforeGoals = BalanceCalculator.totalBalance(accounts, txs)
        assertEquals(90_000, beforeGoals)

        val allocations = listOf(
            GoalAllocation(
                id = "ga1",
                goalId = "g1",
                accountId = "a1",
                amountRial = 25_000,
                date = day,
                note = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertEquals(65_000, BalanceCalculator.totalBalance(accounts, txs, allocations))
        assertEquals(65_000, BalanceCalculator.accountBalance(accounts[0], txs, allocations))

        // Deleting allocation → balance restored
        assertEquals(90_000, BalanceCalculator.totalBalance(accounts, txs, emptyList()))
        assertEquals(1, txs.size)
    }

    @Test
    fun availableForGoalAllocation_excludesCurrentWhenEditing() {
        val account = Account("a1", "نقدی", 50_000)
        val allocations = listOf(
            GoalAllocation("ga1", "g1", "a1", 20_000, day, null, now, now),
            GoalAllocation("ga2", "g1", "a1", 10_000, day, null, now, now),
        )
        assertEquals(
            20_000,
            BalanceCalculator.availableForGoalAllocation(account, emptyList(), allocations),
        )
        assertEquals(
            40_000,
            BalanceCalculator.availableForGoalAllocation(
                account,
                emptyList(),
                allocations,
                excludeAllocationId = "ga1",
            ),
        )
    }

    @Test
    fun goalProgress_stillIndependentOfTransactionCount() {
        val goal = Goal(
            id = "g1",
            name = "سفر",
            targetAmountRial = 50_000,
            targetDate = null,
            iconKey = null,
            status = GoalStatus.ACTIVE,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        val allocations = listOf(
            GoalAllocation("ga1", "g1", "a1", 25_000, day, null, now, now),
        )
        assertEquals(25_000, GoalProgressCalculator.progress(goal, allocations).allocatedTotalRial)
    }

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        accountId: String,
    ): Transaction = Transaction(
        id = id,
        type = type,
        amountRial = amount,
        title = "t",
        categoryId = "c",
        accountId = accountId,
        date = day,
        createdAt = now,
        updatedAt = now,
    )
}
