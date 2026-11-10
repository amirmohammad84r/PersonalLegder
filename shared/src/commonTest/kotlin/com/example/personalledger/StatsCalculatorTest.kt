package com.example.personalledger

import com.example.personalledger.domain.calendar.JalaliDateConverter
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.stats.StatsCalculator
import com.example.personalledger.domain.stats.StatsTimeRange
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatsCalculatorTest {
    private val now = Instant.fromEpochMilliseconds(0)

    @Test
    fun monthly_groupsByJalaliDayAndSumsRialSeparately() {
        val anchor = JalaliDateConverter.requireGregorian(1403, 2, 10)
        val txs = listOf(
            tx("1", TransactionType.INCOME, 100_000, JalaliDateConverter.requireGregorian(1403, 2, 1)),
            tx("2", TransactionType.EXPENSE, 40_000, JalaliDateConverter.requireGregorian(1403, 2, 1)),
            tx("3", TransactionType.INCOME, 50_000, JalaliDateConverter.requireGregorian(1403, 2, 15)),
            tx("4", TransactionType.EXPENSE, 10_000, LocalDate(2024, 3, 1)), // outside month
        )
        val report = StatsCalculator.buildReport(
            txs,
            StatsTimeRange.MONTHLY,
            anchor,
            com.example.personalledger.domain.calendar.CalendarSystem.JALALI,
        )
        assertTrue(report.periodLabel.contains("اردیبهشت"))
        assertTrue(report.periodLabel.contains("1403"))
        assertEquals(150_000L, report.totalIncomeRial)
        assertEquals(40_000L, report.totalExpenseRial)
        val day1 = report.buckets.first { it.startDate == JalaliDateConverter.requireGregorian(1403, 2, 1) }
        assertEquals(100_000L, day1.incomeRial)
        assertEquals(40_000L, day1.expenseRial)
        assertTrue(day1.expenseRial > 0L)
    }

    @Test
    fun weekly_usesSaturdayStart() {
        val wednesday = LocalDate(2024, 5, 1)
        val start = JalaliDateConverter.startOfJalaliWeek(wednesday)
        assertEquals(0, JalaliDateConverter.dayOfWeekIndex(start))
        val report = StatsCalculator.buildReport(emptyList(), StatsTimeRange.WEEKLY, wednesday)
        assertEquals(7, report.buckets.size)
        assertEquals(start, report.rangeStart)
        assertEquals(start.plus(DatePeriod(days = 6)), report.rangeEnd)
    }

    @Test
    fun yearly_hasTwelveMonthBuckets() {
        val anchor = JalaliDateConverter.requireGregorian(1403, 7, 1)
        val txs = listOf(
            tx("1", TransactionType.INCOME, 1_000, JalaliDateConverter.requireGregorian(1403, 1, 1)),
            tx("2", TransactionType.EXPENSE, 200, JalaliDateConverter.requireGregorian(1403, 12, 29)),
        )
        val report = StatsCalculator.buildReport(txs, StatsTimeRange.YEARLY, anchor)
        assertEquals(12, report.buckets.size)
        assertEquals(1_000L, report.totalIncomeRial)
        assertEquals(200L, report.totalExpenseRial)
        assertEquals("1403", report.periodLabel)
    }

    @Test
    fun daily_windowIsSevenDaysAroundAnchor() {
        val anchor = LocalDate(2024, 5, 10)
        val report = StatsCalculator.buildReport(emptyList(), StatsTimeRange.DAILY, anchor)
        assertEquals(7, report.buckets.size)
        assertEquals(anchor.plus(DatePeriod(days = -3)), report.rangeStart)
        assertEquals(anchor.plus(DatePeriod(days = 3)), report.rangeEnd)
    }

    @Test
    fun emptyApp_flagFalseWhenNoTransactions() {
        val report = StatsCalculator.buildReport(
            emptyList(),
            StatsTimeRange.MONTHLY,
            LocalDate(2024, 5, 1),
        )
        assertFalse(report.hasAnyTransactionInApp)
        assertFalse(report.hasDataInPeriod)
    }

    @Test
    fun shiftAnchor_monthlyUsesJalaliMonths() {
        val farvardin = JalaliDateConverter.requireGregorian(1403, 1, 15)
        val next = StatsCalculator.shiftAnchor(farvardin, StatsTimeRange.MONTHLY, 1)
        val j = JalaliDateConverter.toJalali(next)
        assertEquals(1403, j.year)
        assertEquals(2, j.month)
    }

    @Test
    fun monthBoundary_transactionOnLastDayIncluded() {
        val esfand29 = JalaliDateConverter.requireGregorian(1402, 12, 29)
        val txs = listOf(tx("1", TransactionType.EXPENSE, 5_000, esfand29))
        val report = StatsCalculator.buildReport(txs, StatsTimeRange.MONTHLY, esfand29)
        assertEquals(5_000L, report.totalExpenseRial)
        assertEquals(0L, report.totalIncomeRial)
    }

    private fun tx(
        id: String,
        type: TransactionType,
        amount: Long,
        date: LocalDate,
    ): Transaction = Transaction(
        id = id,
        type = type,
        amountRial = amount,
        title = "t",
        categoryId = "c",
        accountId = "a",
        date = date,
        createdAt = now,
        updatedAt = now,
    )
}
