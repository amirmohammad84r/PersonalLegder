package com.example.personalledger

import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.calendar.JalaliDateConverter
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.stats.StatsCalculator
import com.example.personalledger.domain.stats.StatsTimeRange
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCalendarTest {
    private val now = Instant.fromEpochMilliseconds(0)

    @Test
    fun jalaliRoundTrip_sameCivilDay() {
        val stored = JalaliDateConverter.requireGregorian(1403, 7, 15)
        val parts = AppCalendar.parts(stored, CalendarSystem.JALALI)
        assertEquals(Triple(1403, 7, 15), parts)
        assertEquals(
            stored,
            AppCalendar.toLocalDate(parts.first, parts.second, parts.third, CalendarSystem.JALALI),
        )
        assertTrue(AppCalendar.formatShort(stored, CalendarSystem.JALALI).startsWith("1403/07/15"))
    }

    @Test
    fun gregorianRoundTrip_sameCivilDay() {
        val stored = LocalDate(2024, 10, 6)
        val parts = AppCalendar.parts(stored, CalendarSystem.GREGORIAN)
        assertEquals(Triple(2024, 10, 6), parts)
        assertEquals(
            stored,
            AppCalendar.toLocalDate(parts.first, parts.second, parts.third, CalendarSystem.GREGORIAN),
        )
        assertEquals("2024/10/06", AppCalendar.formatShort(stored, CalendarSystem.GREGORIAN))
    }

    @Test
    fun sameStoredDate_formatsDifferentlyPerCalendar() {
        val stored = LocalDate(2024, 9, 22) // 1403/07/01
        assertEquals("1403/07/01", AppCalendar.formatShort(stored, CalendarSystem.JALALI))
        assertEquals("2024/09/22", AppCalendar.formatShort(stored, CalendarSystem.GREGORIAN))
    }

    @Test
    fun jalaliMonthBounds_includeLastDayExcludeNextMonth() {
        val esfand = JalaliDateConverter.requireGregorian(1402, 12, 15)
        val start = AppCalendar.startOfMonth(esfand, CalendarSystem.JALALI)
        val end = AppCalendar.endOfMonth(esfand, CalendarSystem.JALALI)
        assertEquals(JalaliDateConverter.requireGregorian(1402, 12, 1), start)
        assertEquals(JalaliDateConverter.requireGregorian(1402, 12, 29), end)
        val nextStart = AppCalendar.startOfMonth(
            AppCalendar.plusMonths(esfand, 1, CalendarSystem.JALALI),
            CalendarSystem.JALALI,
        )
        assertEquals(JalaliDateConverter.requireGregorian(1403, 1, 1), nextStart)
    }

    @Test
    fun gregorianMonthBounds_andYearCrossing() {
        val dec = LocalDate(2024, 12, 10)
        assertEquals(LocalDate(2024, 12, 1), AppCalendar.startOfMonth(dec, CalendarSystem.GREGORIAN))
        assertEquals(LocalDate(2024, 12, 31), AppCalendar.endOfMonth(dec, CalendarSystem.GREGORIAN))
        val jan = AppCalendar.plusMonths(dec, 1, CalendarSystem.GREGORIAN)
        assertEquals(1, jan.monthNumber)
        assertEquals(2025, jan.year)
    }

    @Test
    fun leapDay_gregorianPreservedOnShiftBack() {
        val leap = LocalDate(2024, 2, 29)
        val next = AppCalendar.plusYears(leap, 1, CalendarSystem.GREGORIAN)
        assertEquals(LocalDate(2025, 2, 28), next)
        val jalaliLeap = JalaliDateConverter.requireGregorian(1399, 12, 30)
        val after = AppCalendar.plusYears(jalaliLeap, 1, CalendarSystem.JALALI)
        val j = JalaliDateConverter.toJalali(after)
        assertEquals(1400, j.year)
        assertEquals(12, j.month)
        assertEquals(29, j.day)
    }

    @Test
    fun statsMonthly_usesSelectedCalendarBounds() {
        val anchor = LocalDate(2024, 3, 15)
        val jalaliReport = StatsCalculator.buildReport(
            emptyList(),
            StatsTimeRange.MONTHLY,
            anchor,
            CalendarSystem.JALALI,
        )
        val gregorianReport = StatsCalculator.buildReport(
            emptyList(),
            StatsTimeRange.MONTHLY,
            anchor,
            CalendarSystem.GREGORIAN,
        )
        assertTrue(jalaliReport.periodLabel.contains("اسفند") || jalaliReport.periodLabel.contains("فروردین"))
        assertTrue(gregorianReport.periodLabel.contains("مارس"))
        assertEquals(LocalDate(2024, 3, 1), gregorianReport.rangeStart)
        assertEquals(LocalDate(2024, 3, 31), gregorianReport.rangeEnd)
    }

    @Test
    fun changingCalendar_doesNotChangeStoredTransactionDate() {
        val date = LocalDate(2024, 5, 1)
        val txs = listOf(tx("1", date))
        val jalali = StatsCalculator.buildReport(txs, StatsTimeRange.MONTHLY, date, CalendarSystem.JALALI)
        val gregorian = StatsCalculator.buildReport(txs, StatsTimeRange.MONTHLY, date, CalendarSystem.GREGORIAN)
        assertEquals(date, txs.single().date)
        assertTrue(jalali.totalExpenseRial + jalali.totalIncomeRial >= 0L)
        assertTrue(gregorian.buckets.isNotEmpty())
    }

    @Test
    fun shiftMonth_respectsCalendar() {
        val farvardin = JalaliDateConverter.requireGregorian(1403, 1, 15)
        val nextJalali = StatsCalculator.shiftAnchor(
            farvardin,
            StatsTimeRange.MONTHLY,
            1,
            CalendarSystem.JALALI,
        )
        assertEquals(2, JalaliDateConverter.toJalali(nextJalali).month)

        val jan = LocalDate(2024, 1, 15)
        val nextGreg = StatsCalculator.shiftAnchor(
            jan,
            StatsTimeRange.MONTHLY,
            1,
            CalendarSystem.GREGORIAN,
        )
        assertEquals(2, nextGreg.monthNumber)
        assertEquals(2024, nextGreg.year)
    }

    private fun tx(id: String, date: LocalDate): Transaction = Transaction(
        id = id,
        type = TransactionType.EXPENSE,
        amountRial = 1_000,
        title = "t",
        categoryId = "c",
        accountId = "a",
        date = date,
        createdAt = now,
        updatedAt = now,
    )
}
