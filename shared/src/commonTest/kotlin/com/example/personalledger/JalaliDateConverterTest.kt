package com.example.personalledger

import com.example.personalledger.domain.calendar.JalaliDateConverter
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JalaliDateConverterTest {
    @Test
    fun knownJalaliToGregorian_nowruz1403() {
        // 1403/01/01 → 2024-03-20
        val g = JalaliDateConverter.toGregorian(1403, 1, 1)
        assertEquals(LocalDate(2024, 3, 20), g)
    }

    @Test
    fun knownGregorianToJalali_roundTrip() {
        val original = LocalDate(2024, 9, 22)
        val j = JalaliDateConverter.toJalali(original)
        assertEquals(1403, j.year)
        assertEquals(7, j.month)
        assertEquals(1, j.day)
        assertEquals(original, JalaliDateConverter.toGregorian(j.year, j.month, j.day))
    }

    @Test
    fun leapYearEsfand29_validInJalaliLeap() {
        // 1399 is Jalali leap; Esfand 30 exists
        val g = JalaliDateConverter.toGregorian(1399, 12, 30)
        assertNotNull(g)
        val back = JalaliDateConverter.toJalali(g)
        assertEquals(1399, back.year)
        assertEquals(12, back.month)
        assertEquals(30, back.day)
    }

    @Test
    fun dayBoundary_lastDayOfYear() {
        val g = JalaliDateConverter.toGregorian(1402, 12, 29)
        assertNotNull(g)
        val next = JalaliDateConverter.toGregorian(1403, 1, 1)
        assertNotNull(next)
        assertEquals(1, next.toEpochDays() - g.toEpochDays())
    }

    @Test
    fun invalidJalali_returnsNull() {
        assertNull(JalaliDateConverter.toGregorian(1403, 13, 1))
        assertNull(JalaliDateConverter.toGregorian(1100, 1, 1))
    }

    @Test
    fun format_isSortableJalaliString() {
        assertEquals("1403/07/01", JalaliDateConverter.format(LocalDate(2024, 9, 22)))
    }
}
