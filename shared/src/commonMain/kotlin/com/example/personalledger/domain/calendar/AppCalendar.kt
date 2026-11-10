package com.example.personalledger.domain.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Central calendar-aware formatting and period math.
 *
 * Week convention (both calendars): Saturday–Friday, matching the Persian locale
 * already used by the product ([JalaliDateConverter.dayOfWeekIndex]).
 *
 * Day-granular ranges use inclusive ends (`start..end`). That is equivalent to the
 * half-open interval `[start, end.plus(1 day))` for [LocalDate] comparisons.
 */
object AppCalendar {
    val gregorianMonthNames: List<String> = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر",
    )

    val weekdayNames: List<String> = JalaliDateConverter.weekdayNames

    fun formatShort(date: LocalDate, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.format(date)
        CalendarSystem.GREGORIAN -> {
            val y = date.year.toString().padStart(4, '0')
            val m = date.monthNumber.toString().padStart(2, '0')
            val d = date.dayOfMonth.toString().padStart(2, '0')
            "$y/$m/$d"
        }
    }

    fun formatFull(date: LocalDate, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.formatFull(date)
        CalendarSystem.GREGORIAN ->
            "${date.dayOfMonth} ${gregorianMonthName(date.monthNumber)} ${date.year}"
    }

    fun formatMonthYear(date: LocalDate, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.formatMonthYear(date)
        CalendarSystem.GREGORIAN ->
            "${gregorianMonthName(date.monthNumber)} ${date.year}"
    }

    fun formatDayMonth(date: LocalDate, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.formatDayMonth(date)
        CalendarSystem.GREGORIAN ->
            "${date.dayOfMonth} ${gregorianMonthName(date.monthNumber)}"
    }

    fun dayOfMonthNumber(date: LocalDate, system: CalendarSystem): Int = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.toJalali(date).day
        CalendarSystem.GREGORIAN -> date.dayOfMonth
    }

    fun yearNumber(date: LocalDate, system: CalendarSystem): Int = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.toJalali(date).year
        CalendarSystem.GREGORIAN -> date.year
    }

    fun monthName(date: LocalDate, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> {
            val j = JalaliDateConverter.toJalali(date)
            JalaliDateConverter.monthName(j.month)
        }
        CalendarSystem.GREGORIAN -> gregorianMonthName(date.monthNumber)
    }

    fun monthName(month: Int, system: CalendarSystem): String = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.monthName(month)
        CalendarSystem.GREGORIAN -> gregorianMonthName(month)
    }

    fun gregorianMonthName(month: Int): String =
        gregorianMonthNames.getOrElse(month - 1) { "" }

    fun startOfWeek(date: LocalDate, system: CalendarSystem): LocalDate {
        // Saturday start for FA locale — same for both display calendars.
        return JalaliDateConverter.startOfJalaliWeek(date)
    }

    fun endOfWeek(date: LocalDate, system: CalendarSystem): LocalDate =
        startOfWeek(date, system).plus(DatePeriod(days = 6))

    fun startOfMonth(date: LocalDate, system: CalendarSystem): LocalDate = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.startOfJalaliMonth(date)
        CalendarSystem.GREGORIAN -> LocalDate(date.year, date.monthNumber, 1)
    }

    fun endOfMonth(date: LocalDate, system: CalendarSystem): LocalDate = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.endOfJalaliMonth(date)
        CalendarSystem.GREGORIAN -> {
            val next = if (date.monthNumber == 12) {
                LocalDate(date.year + 1, 1, 1)
            } else {
                LocalDate(date.year, date.monthNumber + 1, 1)
            }
            next.minus(DatePeriod(days = 1))
        }
    }

    fun startOfYear(date: LocalDate, system: CalendarSystem): LocalDate = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.startOfJalaliYear(date)
        CalendarSystem.GREGORIAN -> LocalDate(date.year, 1, 1)
    }

    fun endOfYear(date: LocalDate, system: CalendarSystem): LocalDate = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.endOfJalaliYear(date)
        CalendarSystem.GREGORIAN -> LocalDate(date.year, 12, 31)
    }

    fun daysInMonth(date: LocalDate, system: CalendarSystem): Int = when (system) {
        CalendarSystem.JALALI -> {
            val j = JalaliDateConverter.toJalali(date)
            JalaliDateConverter.daysInJalaliMonth(j.year, j.month)
        }
        CalendarSystem.GREGORIAN -> endOfMonth(date, system).dayOfMonth
    }

    fun plusMonths(date: LocalDate, months: Int, system: CalendarSystem): LocalDate =
        when (system) {
            CalendarSystem.JALALI -> JalaliDateConverter.plusJalaliMonths(date, months)
            CalendarSystem.GREGORIAN -> {
                val total = date.year * 12 + (date.monthNumber - 1) + months
                val ny = total / 12
                val nm = total % 12 + 1
                val probe = LocalDate(ny, nm, 1)
                val maxDay = endOfMonth(probe, CalendarSystem.GREGORIAN).dayOfMonth
                LocalDate(ny, nm, minOf(date.dayOfMonth, maxDay))
            }
        }

    fun plusYears(date: LocalDate, years: Int, system: CalendarSystem): LocalDate =
        when (system) {
            CalendarSystem.JALALI -> JalaliDateConverter.plusJalaliYears(date, years)
            CalendarSystem.GREGORIAN -> {
                val ny = date.year + years
                val probe = LocalDate(ny, date.monthNumber, 1)
                val maxDay = endOfMonth(probe, CalendarSystem.GREGORIAN).dayOfMonth
                LocalDate(ny, date.monthNumber, minOf(date.dayOfMonth, maxDay))
            }
        }

    /** Date at (year, month, day) in [system], as storage [LocalDate]. */
    fun toLocalDate(year: Int, month: Int, day: Int, system: CalendarSystem): LocalDate? =
        when (system) {
            CalendarSystem.JALALI -> JalaliDateConverter.toGregorian(year, month, day)
            CalendarSystem.GREGORIAN -> runCatching { LocalDate(year, month, day) }.getOrNull()
        }

    fun parts(date: LocalDate, system: CalendarSystem): Triple<Int, Int, Int> = when (system) {
        CalendarSystem.JALALI -> {
            val j = JalaliDateConverter.toJalali(date)
            Triple(j.year, j.month, j.day)
        }
        CalendarSystem.GREGORIAN -> Triple(date.year, date.monthNumber, date.dayOfMonth)
    }

    fun daysInMonth(year: Int, month: Int, system: CalendarSystem): Int = when (system) {
        CalendarSystem.JALALI -> JalaliDateConverter.daysInJalaliMonth(year, month)
        CalendarSystem.GREGORIAN -> {
            val probe = runCatching { LocalDate(year, month, 1) }.getOrNull() ?: return 0
            endOfMonth(probe, CalendarSystem.GREGORIAN).dayOfMonth
        }
    }
}
