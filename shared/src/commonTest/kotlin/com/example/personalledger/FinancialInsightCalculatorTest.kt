package com.example.personalledger

import com.example.personalledger.domain.ai.AiPromptBuilder
import com.example.personalledger.domain.ai.FinancialInsightCalculator
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.calendar.JalaliDateConverter
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinancialInsightCalculatorTest {
    private val now = Instant.fromEpochMilliseconds(0)

    @Test
    fun buildsMonthTotalsInRialWithoutTitlesOrCategoryNames() {
        val today = JalaliDateConverter.requireGregorian(1403, 2, 10)
        val txs = listOf(
            tx("1", TransactionType.INCOME, 200_000, JalaliDateConverter.requireGregorian(1403, 2, 1), "cat-a"),
            tx("2", TransactionType.EXPENSE, 50_000, JalaliDateConverter.requireGregorian(1403, 2, 2), "cat-b"),
            tx("3", TransactionType.EXPENSE, 30_000, JalaliDateConverter.requireGregorian(1403, 2, 3), "cat-b"),
            tx("4", TransactionType.EXPENSE, 10_000, LocalDate(2024, 1, 1), "cat-b"), // outside month
        )
        val snapshot = FinancialInsightCalculator.build(
            transactions = txs,
            goals = emptyList(),
            allocations = emptyList(),
            accountCount = 2,
            today = today,
            calendar = CalendarSystem.JALALI,
        )
        assertEquals("ریال", snapshot.currencyUnit)
        assertEquals("jalali", snapshot.calendarSystem)
        assertEquals(4, snapshot.totalTransactionCount)
        assertEquals(2, snapshot.accountCount)
        val month = snapshot.currentMonth!!
        assertEquals(200_000L, month.incomeRial)
        assertEquals(80_000L, month.expenseRial)
        assertEquals(120_000L, month.netRial)
        assertEquals(3, month.recordCount)
        assertEquals(1, snapshot.topExpenseCategories.size)
        assertEquals("expense_category_rank_1", snapshot.topExpenseCategories.first().rankLabel)
        assertEquals(80_000L, snapshot.topExpenseCategories.first().totalRial)

        val rendered = AiPromptBuilder.renderSnapshot(snapshot)
        assertFalse(rendered.contains("عنوان محرمانه"))
        assertFalse(rendered.contains("cat-b"))
        assertTrue(rendered.contains("income_rial=200000"))
        assertTrue(rendered.contains("expense_rial=80000"))
        assertTrue(rendered.contains("unit=ریال") || rendered.contains("currency_unit=ریال"))
    }

    @Test
    fun emptyLedgerMarksInsufficientData() {
        val snapshot = FinancialInsightCalculator.build(
            transactions = emptyList(),
            goals = emptyList(),
            allocations = emptyList(),
            accountCount = 0,
            today = LocalDate(2024, 5, 1),
            calendar = CalendarSystem.GREGORIAN,
        )
        assertFalse(snapshot.hasEnoughDataForNumericAnswers)
        assertTrue(AiPromptBuilder.userPrompt("چقدر خرج کردم؟", snapshot).contains("data_sufficient_for_numeric_answers=false"))
    }

    @Test
    fun activeGoalsAreAnonymized() {
        val goal = Goal(
            id = "g1",
            name = "سفر محرمانه",
            targetAmountRial = 1_000_000,
            targetDate = null,
            iconKey = null,
            status = GoalStatus.ACTIVE,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        val allocation = GoalAllocation(
            id = "a1",
            goalId = "g1",
            accountId = "acc",
            amountRial = 250_000,
            date = LocalDate(2024, 5, 1),
            note = "یادداشت شخصی",
            createdAt = now,
            updatedAt = now,
        )
        val snapshot = FinancialInsightCalculator.build(
            transactions = emptyList(),
            goals = listOf(goal),
            allocations = listOf(allocation),
            accountCount = 1,
            today = LocalDate(2024, 5, 10),
            calendar = CalendarSystem.GREGORIAN,
        )
        val rendered = AiPromptBuilder.renderSnapshot(snapshot)
        assertEquals(1, snapshot.activeGoals.size)
        assertEquals("active_goal_1", snapshot.activeGoals.first().rankLabel)
        assertEquals(250_000L, snapshot.activeGoals.first().allocatedTotalRial)
        assertFalse(rendered.contains("سفر محرمانه"))
        assertFalse(rendered.contains("یادداشت شخصی"))
    }

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        date: LocalDate,
        categoryId: String = "c1",
    ) = Transaction(
        id = id,
        type = type,
        amountRial = amount,
        title = "عنوان محرمانه",
        categoryId = categoryId,
        accountId = "acc",
        date = date,
        createdAt = now,
        updatedAt = now,
    )
}
