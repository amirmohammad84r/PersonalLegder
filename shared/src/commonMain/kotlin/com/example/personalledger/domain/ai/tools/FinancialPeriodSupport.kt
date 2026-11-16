package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Half-open local-date range `[startInclusive, endExclusive)`.
 * All tool aggregations use this contract; inclusive UI labels are derived for display only.
 */
data class HalfOpenDateRange(
    val startInclusive: LocalDate,
    val endExclusive: LocalDate,
    val calendar: CalendarSystem,
    val displayLabel: String,
) {
    init {
        require(endExclusive > startInclusive) {
            "endExclusive must be after startInclusive"
        }
    }

    fun contains(date: LocalDate): Boolean =
        date >= startInclusive && date < endExclusive

    val calendarDayCount: Int
        get() = (endExclusive.toEpochDays() - startInclusive.toEpochDays()).toInt()

    val lastInclusive: LocalDate
        get() = endExclusive.plus(DatePeriod(days = -1))
}

object FinancialPeriodSupport {
    fun resolveYearMonth(
        year: Int,
        month: Int,
        calendar: CalendarSystem,
    ): HalfOpenDateRange {
        if (year !in FinancialToolId.MIN_YEAR..FinancialToolId.MAX_YEAR) {
            throw FinancialToolValidationException("Year out of allowed range.")
        }
        if (month !in 1..12) {
            throw FinancialToolValidationException("Month must be 1..12.")
        }
        val days = AppCalendar.daysInMonth(year, month, calendar)
        if (days <= 0) {
            throw FinancialToolValidationException("Invalid year/month for calendar.")
        }
        val start = AppCalendar.toLocalDate(year, month, 1, calendar)
            ?: throw FinancialToolValidationException("Cannot resolve month start.")
        val endInclusive = AppCalendar.toLocalDate(year, month, days, calendar)
            ?: throw FinancialToolValidationException("Cannot resolve month end.")
        val endExclusive = endInclusive.plus(DatePeriod(days = 1))
        val label = AppCalendar.formatMonthYear(start, calendar)
        return enforceMaxLength(
            HalfOpenDateRange(
                startInclusive = start,
                endExclusive = endExclusive,
                calendar = calendar,
                displayLabel = label,
            ),
        )
    }

    fun resolveNamedMonth(
        period: NamedMonthPeriod,
        today: LocalDate,
        calendar: CalendarSystem,
    ): HalfOpenDateRange {
        val anchor = when (period) {
            NamedMonthPeriod.CURRENT_MONTH -> today
            NamedMonthPeriod.PREVIOUS_MONTH -> AppCalendar.plusMonths(today, -1, calendar)
        }
        val (year, month, _) = AppCalendar.parts(anchor, calendar)
        return resolveYearMonth(year, month, calendar)
    }

    /**
     * Last [months] calendar months ending at the month containing [today], half-open.
     * Example: months=1 → current month only; months=6 → from start of month (today-5m) to endExclusive after current month.
     */
    fun resolveLastNMonths(
        months: Int,
        today: LocalDate,
        calendar: CalendarSystem,
    ): HalfOpenDateRange {
        val clamped = months.coerceIn(1, FinancialToolId.MAX_LOOKBACK_MONTHS)
        val startAnchor = AppCalendar.plusMonths(today, -(clamped - 1), calendar)
        val (startYear, startMonth, _) = AppCalendar.parts(startAnchor, calendar)
        val start = AppCalendar.toLocalDate(startYear, startMonth, 1, calendar)
            ?: throw FinancialToolValidationException("Cannot resolve lookback start.")
        val currentMonth = resolveNamedMonth(NamedMonthPeriod.CURRENT_MONTH, today, calendar)
        return enforceMaxLength(
            HalfOpenDateRange(
                startInclusive = start,
                endExclusive = currentMonth.endExclusive,
                calendar = calendar,
                displayLabel = "${currentMonth.displayLabel} (آخرین $clamped ماه)",
            ),
        )
    }

    fun resolveHalfOpen(
        startInclusive: LocalDate,
        endExclusive: LocalDate,
        calendar: CalendarSystem,
        displayLabel: String? = null,
    ): HalfOpenDateRange {
        if (endExclusive <= startInclusive) {
            throw FinancialToolValidationException("Range must be non-empty half-open interval.")
        }
        val label = displayLabel ?: buildString {
            append(AppCalendar.formatShort(startInclusive, calendar))
            append(" – ")
            append(AppCalendar.formatShort(endExclusive.plus(DatePeriod(days = -1)), calendar))
        }
        return enforceMaxLength(
            HalfOpenDateRange(
                startInclusive = startInclusive,
                endExclusive = endExclusive,
                calendar = calendar,
                displayLabel = label,
            ),
        )
    }

    fun parseIsoLocalDate(raw: String): LocalDate {
        val trimmed = raw.trim()
        val parts = trimmed.split('-')
        if (parts.size != 3) {
            throw FinancialToolValidationException("Date must be YYYY-MM-DD.")
        }
        val y = parts[0].toIntOrNull()
            ?: throw FinancialToolValidationException("Invalid date year.")
        val m = parts[1].toIntOrNull()
            ?: throw FinancialToolValidationException("Invalid date month.")
        val d = parts[2].toIntOrNull()
            ?: throw FinancialToolValidationException("Invalid date day.")
        return runCatching { LocalDate(y, m, d) }.getOrElse {
            throw FinancialToolValidationException("Invalid calendar date.")
        }
    }

    fun calendarLabel(calendar: CalendarSystem): String = when (calendar) {
        CalendarSystem.JALALI -> "jalali"
        CalendarSystem.GREGORIAN -> "gregorian"
    }

    private fun enforceMaxLength(range: HalfOpenDateRange): HalfOpenDateRange {
        if (range.calendarDayCount > FinancialToolId.MAX_RANGE_DAYS) {
            throw FinancialToolValidationException(
                "Range exceeds maximum of ${FinancialToolId.MAX_RANGE_DAYS} days.",
            )
        }
        return range
    }
}
