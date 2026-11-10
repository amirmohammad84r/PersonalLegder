package com.example.personalledger.domain.stats

import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Aggregates transactions into period buckets for the statistics screen.
 * Period boundaries and labels follow [calendar]; storage dates stay as LocalDate.
 * Amounts stay as positive ریال sums per type — never negated for chart series.
 */
object StatsCalculator {
    fun buildReport(
        transactions: List<Transaction>,
        range: StatsTimeRange,
        anchor: LocalDate,
        calendar: CalendarSystem = CalendarSystem.JALALI,
    ): StatsReport {
        val (rangeStart, rangeEnd, periodLabel, bucketsMeta) = periodPlan(range, anchor, calendar)
        val inRange = transactions.filter { it.date in rangeStart..rangeEnd }

        val incomeByBucket = LongArray(bucketsMeta.size)
        val expenseByBucket = LongArray(bucketsMeta.size)

        for (tx in inRange) {
            val idx = bucketsMeta.indexOfFirst { tx.date in it.start..it.end }
            if (idx < 0) continue
            when (tx.type) {
                TransactionType.INCOME -> incomeByBucket[idx] += tx.amountRial
                TransactionType.EXPENSE -> expenseByBucket[idx] += tx.amountRial
            }
        }

        val buckets = bucketsMeta.mapIndexed { index, meta ->
            StatsBucket(
                index = index,
                axisLabel = meta.axisLabel,
                tooltipLabel = meta.tooltipLabel,
                startDate = meta.start,
                endDate = meta.end,
                incomeRial = incomeByBucket[index],
                expenseRial = expenseByBucket[index],
            )
        }

        return StatsReport(
            periodLabel = periodLabel,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            totalIncomeRial = incomeByBucket.sum(),
            totalExpenseRial = expenseByBucket.sum(),
            buckets = buckets,
            hasAnyTransactionInApp = transactions.isNotEmpty(),
        )
    }

    fun shiftAnchor(
        anchor: LocalDate,
        range: StatsTimeRange,
        delta: Int,
        calendar: CalendarSystem = CalendarSystem.JALALI,
    ): LocalDate =
        when (range) {
            StatsTimeRange.DAILY -> anchor.plus(DatePeriod(days = delta))
            StatsTimeRange.WEEKLY -> anchor.plus(DatePeriod(days = 7 * delta))
            StatsTimeRange.MONTHLY -> AppCalendar.plusMonths(anchor, delta, calendar)
            StatsTimeRange.YEARLY -> AppCalendar.plusYears(anchor, delta, calendar)
        }

    private data class BucketMeta(
        val start: LocalDate,
        val end: LocalDate,
        val axisLabel: String,
        val tooltipLabel: String,
    )

    private data class PeriodPlan(
        val rangeStart: LocalDate,
        val rangeEnd: LocalDate,
        val periodLabel: String,
        val buckets: List<BucketMeta>,
    )

    private fun periodPlan(
        range: StatsTimeRange,
        anchor: LocalDate,
        calendar: CalendarSystem,
    ): PeriodPlan =
        when (range) {
            StatsTimeRange.DAILY -> dailyPlan(anchor, calendar)
            StatsTimeRange.WEEKLY -> weeklyPlan(anchor, calendar)
            StatsTimeRange.MONTHLY -> monthlyPlan(anchor, calendar)
            StatsTimeRange.YEARLY -> yearlyPlan(anchor, calendar)
        }

    /**
     * Daily: focus day with a short surrounding window (±3 days) for a readable trend.
     */
    private fun dailyPlan(anchor: LocalDate, calendar: CalendarSystem): PeriodPlan {
        val start = anchor.plus(DatePeriod(days = -3))
        val end = anchor.plus(DatePeriod(days = 3))
        val buckets = (0..6).map { offset ->
            val day = start.plus(DatePeriod(days = offset))
            BucketMeta(
                start = day,
                end = day,
                axisLabel = AppCalendar.dayOfMonthNumber(day, calendar).toString(),
                tooltipLabel = AppCalendar.formatFull(day, calendar),
            )
        }
        return PeriodPlan(
            rangeStart = start,
            rangeEnd = end,
            periodLabel = AppCalendar.formatFull(anchor, calendar),
            buckets = buckets,
        )
    }

    private fun weeklyPlan(anchor: LocalDate, calendar: CalendarSystem): PeriodPlan {
        val start = AppCalendar.startOfWeek(anchor, calendar)
        val end = AppCalendar.endOfWeek(anchor, calendar)
        val buckets = (0..6).map { offset ->
            val day = start.plus(DatePeriod(days = offset))
            BucketMeta(
                start = day,
                end = day,
                axisLabel = AppCalendar.weekdayNames[offset],
                tooltipLabel = AppCalendar.formatFull(day, calendar),
            )
        }
        val label =
            "${AppCalendar.formatDayMonth(start, calendar)} – ${AppCalendar.formatDayMonth(end, calendar)}"
        return PeriodPlan(start, end, label, buckets)
    }

    private fun monthlyPlan(anchor: LocalDate, calendar: CalendarSystem): PeriodPlan {
        val start = AppCalendar.startOfMonth(anchor, calendar)
        val end = AppCalendar.endOfMonth(anchor, calendar)
        val days = AppCalendar.daysInMonth(anchor, calendar)
        val (year, month, _) = AppCalendar.parts(anchor, calendar)
        val buckets = (1..days).map { day ->
            val date = AppCalendar.toLocalDate(year, month, day, calendar)
                ?: error("Invalid date $year/$month/$day for $calendar")
            BucketMeta(
                start = date,
                end = date,
                axisLabel = day.toString(),
                tooltipLabel = AppCalendar.formatFull(date, calendar),
            )
        }
        return PeriodPlan(
            rangeStart = start,
            rangeEnd = end,
            periodLabel = AppCalendar.formatMonthYear(anchor, calendar),
            buckets = buckets,
        )
    }

    private fun yearlyPlan(anchor: LocalDate, calendar: CalendarSystem): PeriodPlan {
        val start = AppCalendar.startOfYear(anchor, calendar)
        val end = AppCalendar.endOfYear(anchor, calendar)
        val year = AppCalendar.yearNumber(anchor, calendar)
        val buckets = (1..12).map { month ->
            val monthStart = AppCalendar.toLocalDate(year, month, 1, calendar)
                ?: error("Invalid month $year/$month")
            val monthEnd = AppCalendar.endOfMonth(monthStart, calendar)
            BucketMeta(
                start = monthStart,
                end = monthEnd,
                axisLabel = AppCalendar.monthName(month, calendar),
                tooltipLabel = "${AppCalendar.monthName(month, calendar)} $year",
            )
        }
        return PeriodPlan(
            rangeStart = start,
            rangeEnd = end,
            periodLabel = year.toString(),
            buckets = buckets,
        )
    }
}
