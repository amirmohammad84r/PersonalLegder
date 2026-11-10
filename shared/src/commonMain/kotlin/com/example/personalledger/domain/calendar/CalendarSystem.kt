package com.example.personalledger.domain.calendar

/**
 * Preferred calendar for *display*, *input*, and *period boundaries*.
 * Storage remains [kotlinx.datetime.LocalDate] / epoch days (Gregorian civil day) and is never rewritten by this setting.
 */
enum class CalendarSystem {
    JALALI,
    GREGORIAN,
}
