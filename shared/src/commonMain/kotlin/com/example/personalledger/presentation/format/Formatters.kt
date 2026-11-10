package com.example.personalledger.presentation.format

import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.datetime.LocalDate

object MoneyFormatter {
    fun format(amountRial: Long, withCurrency: Boolean = true): String {
        val signed = amountRial
        val abs = kotlin.math.abs(signed)
        val grouped = groupThousands(abs.toString()).toPersianDigits()
        val body = if (signed < 0) "−$grouped" else grouped
        return if (withCurrency) "$body ${CurrencyConfig.displayName}" else body
    }

    fun formatSigned(amountRial: Long, income: Boolean): String {
        val prefix = if (income) "+" else "−"
        return "$prefix${format(kotlin.math.abs(amountRial))}"
    }

    private fun groupThousands(digits: String): String {
        return digits.reversed().chunked(3).joinToString(",").reversed()
    }
}

object DateFormatter {
    /** Short numeric date in the user's calendar, with Persian digits. */
    fun format(date: LocalDate, calendar: CalendarSystem = CalendarSystem.JALALI): String =
        AppCalendar.formatShort(date, calendar).toPersianDigits()

    fun formatFull(date: LocalDate, calendar: CalendarSystem = CalendarSystem.JALALI): String =
        AppCalendar.formatFull(date, calendar).toPersianDigits()

    fun formatMonthYear(date: LocalDate, calendar: CalendarSystem = CalendarSystem.JALALI): String =
        AppCalendar.formatMonthYear(date, calendar).toPersianDigits()
}

fun String.toPersianDigits(): String = buildString(length) {
    for (ch in this@toPersianDigits) {
        append(
            when (ch) {
                '0' -> '۰'
                '1' -> '۱'
                '2' -> '۲'
                '3' -> '۳'
                '4' -> '۴'
                '5' -> '۵'
                '6' -> '۶'
                '7' -> '۷'
                '8' -> '۸'
                '9' -> '۹'
                else -> ch
            },
        )
    }
}

fun String.toLatinDigits(): String = buildString(length) {
    for (ch in this@toLatinDigits) {
        append(
            when (ch) {
                '۰', '٠' -> '0'
                '۱', '١' -> '1'
                '۲', '٢' -> '2'
                '۳', '٣' -> '3'
                '۴', '٤' -> '4'
                '۵', '٥' -> '5'
                '۶', '٦' -> '6'
                '۷', '٧' -> '7'
                '۸', '٨' -> '8'
                '۹', '٩' -> '9'
                else -> ch
            },
        )
    }
}
