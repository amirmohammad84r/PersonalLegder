package com.example.personalledger.domain.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Central Jalali ↔ Gregorian conversion for storage (LocalDate/epoch day) and UI.
 */
object JalaliDateConverter {
    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    val monthNames: List<String> = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    val weekdayNames: List<String> = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه",
    )

    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate? {
        if (jy !in 1200..1500 || jm !in 1..12 || jd !in 1..31) return null
        val jy2 = jy - 979
        val jm2 = jm - 1
        val jd2 = jd - 1
        var jDayNo = 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4
        for (i in 0 until jm2) {
            jDayNo += if (i < 6) 31 else 30
        }
        jDayNo += jd2
        var gDayNo = jDayNo + 79
        val gy2 = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        val gyAdj: Int
        if (gDayNo >= 36525) {
            gDayNo--
            val adj = gy2 + 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
            gyAdj = adj
        } else {
            gyAdj = gy2
        }
        var gYear = gyAdj + 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gYear += gDayNo / 365
            gDayNo %= 365
        }
        val salA = intArrayOf(
            0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31,
        )
        var month = 0
        while (month < 13 && gDayNo >= salA[month]) {
            gDayNo -= salA[month]
            month++
        }
        return runCatching { LocalDate(gYear, month, gDayNo + 1) }.getOrNull()
    }

    fun toJalali(date: LocalDate): JalaliDate {
        val gy = date.year
        val gm = date.monthNumber
        val gd = date.dayOfMonth
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1
        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        for (i in 0 until gm2) {
            gDayNo += gDaysInMonth[i + 1]
        }
        if (gm > 2 && isGregorianLeap(gy)) gDayNo++
        gDayNo += gd2
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        val jDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jm = 0
        while (jm < 11 && jDayNo >= jDays[jm]) {
            jDayNo -= jDays[jm]
            jm++
        }
        return JalaliDate(jy, jm + 1, jDayNo + 1)
    }

    fun format(date: LocalDate): String {
        val j = toJalali(date)
        val y = j.year.toString().padStart(4, '0')
        val m = j.month.toString().padStart(2, '0')
        val d = j.day.toString().padStart(2, '0')
        return "$y/$m/$d"
    }

    fun monthName(month: Int): String = monthNames.getOrElse(month - 1) { "" }

    fun daysInJalaliMonth(year: Int, month: Int): Int = when (month) {
        in 1..6 -> 31
        in 7..11 -> 30
        12 -> if (isJalaliLeapYear(year)) 30 else 29
        else -> 0
    }

    /** Jalali leap years: remainder of year%33 in {1,5,9,13,17,22,26,30}. */
    fun isJalaliLeapYear(year: Int): Boolean {
        val r = year % 33
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
    }

    fun requireGregorian(jy: Int, jm: Int, jd: Int): LocalDate =
        toGregorian(jy, jm, jd) ?: error("Invalid Jalali date $jy/$jm/$jd")

    /** Days since Saturday (0=شنبه … 6=جمعه). */
    fun dayOfWeekIndex(date: LocalDate): Int = when (date.dayOfWeek) {
        DayOfWeek.SATURDAY -> 0
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 3
        DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5
        DayOfWeek.FRIDAY -> 6
    }

    fun startOfJalaliWeek(date: LocalDate): LocalDate =
        date.minus(DatePeriod(days = dayOfWeekIndex(date)))

    fun endOfJalaliWeek(date: LocalDate): LocalDate =
        startOfJalaliWeek(date).plus(DatePeriod(days = 6))

    fun startOfJalaliMonth(date: LocalDate): LocalDate {
        val j = toJalali(date)
        return requireGregorian(j.year, j.month, 1)
    }

    fun endOfJalaliMonth(date: LocalDate): LocalDate {
        val j = toJalali(date)
        return requireGregorian(j.year, j.month, daysInJalaliMonth(j.year, j.month))
    }

    fun startOfJalaliYear(date: LocalDate): LocalDate {
        val j = toJalali(date)
        return requireGregorian(j.year, 1, 1)
    }

    fun endOfJalaliYear(date: LocalDate): LocalDate {
        val j = toJalali(date)
        return requireGregorian(j.year, 12, daysInJalaliMonth(j.year, 12))
    }

    fun plusJalaliMonths(date: LocalDate, months: Int): LocalDate {
        val j = toJalali(date)
        val total = j.year * 12 + (j.month - 1) + months
        val ny = total / 12
        val nm = total % 12 + 1
        val maxDay = daysInJalaliMonth(ny, nm)
        val nd = minOf(j.day, maxDay)
        return requireGregorian(ny, nm, nd)
    }

    fun plusJalaliYears(date: LocalDate, years: Int): LocalDate {
        val j = toJalali(date)
        val ny = j.year + years
        val maxDay = daysInJalaliMonth(ny, j.month)
        val nd = minOf(j.day, maxDay)
        return requireGregorian(ny, j.month, nd)
    }

    fun formatMonthYear(date: LocalDate): String {
        val j = toJalali(date)
        return "${monthName(j.month)} ${j.year}"
    }

    fun formatDayMonth(date: LocalDate): String {
        val j = toJalali(date)
        return "${j.day} ${monthName(j.month)}"
    }

    fun formatFull(date: LocalDate): String {
        val j = toJalali(date)
        return "${j.day} ${monthName(j.month)} ${j.year}"
    }

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
