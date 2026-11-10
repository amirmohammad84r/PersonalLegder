package com.example.personalledger.data.prefs

import com.example.personalledger.domain.calendar.CalendarSystem
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getStringFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Supported app language. Only languages that the UI actually ships copy for.
 * v1 Strings are Persian-only → only [FA].
 */
enum class AppLanguage {
    FA,
}

class DisplayPreferences(
    private val settings: Settings,
) {
    private val languageKey = "app_language"
    private val calendarKey = "calendar_system"

    @OptIn(ExperimentalSettingsApi::class)
    fun observeLanguage(): Flow<AppLanguage> =
        stringFlow(languageKey, AppLanguage.FA.name).map { raw ->
            runCatching { AppLanguage.valueOf(raw) }.getOrDefault(AppLanguage.FA)
        }

    fun readLanguage(): AppLanguage =
        runCatching { AppLanguage.valueOf(settings.getString(languageKey, AppLanguage.FA.name)) }
            .getOrDefault(AppLanguage.FA)

    fun writeLanguage(language: AppLanguage) {
        settings.putString(languageKey, language.name)
    }

    @OptIn(ExperimentalSettingsApi::class)
    fun observeCalendar(): Flow<CalendarSystem> =
        stringFlow(calendarKey, CalendarSystem.JALALI.name).map { raw ->
            runCatching { CalendarSystem.valueOf(raw) }.getOrDefault(CalendarSystem.JALALI)
        }

    fun readCalendar(): CalendarSystem =
        runCatching {
            CalendarSystem.valueOf(settings.getString(calendarKey, CalendarSystem.JALALI.name))
        }.getOrDefault(CalendarSystem.JALALI)

    fun writeCalendar(system: CalendarSystem) {
        settings.putString(calendarKey, system.name)
    }

    @OptIn(ExperimentalSettingsApi::class)
    private fun stringFlow(key: String, default: String): Flow<String> {
        val observable = settings as? ObservableSettings
            ?: return flow { emit(settings.getString(key, default)) }
        return observable.getStringFlow(key, default)
    }
}
