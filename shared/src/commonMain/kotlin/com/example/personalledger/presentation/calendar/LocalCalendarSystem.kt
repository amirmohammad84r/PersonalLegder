package com.example.personalledger.presentation.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.LocalAppContainer
import com.example.personalledger.domain.calendar.CalendarSystem

val LocalCalendarSystem = staticCompositionLocalOf { CalendarSystem.JALALI }

@Composable
fun rememberCalendarSystem(): CalendarSystem {
    val container = LocalAppContainer.current
    val calendar by container.displayPreferences.observeCalendar()
        .collectAsStateWithLifecycle(initialValue = container.displayPreferences.readCalendar())
    return calendar
}
