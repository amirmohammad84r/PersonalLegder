package com.example.personalledger.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.AppLanguage
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.data.prefs.ThemeMode
import com.example.personalledger.data.prefs.ThemePreferences
import com.example.personalledger.domain.calendar.CalendarSystem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.FA,
    val calendar: CalendarSystem = CalendarSystem.JALALI,
    /** Languages the app currently ships strings for. */
    val supportedLanguages: List<AppLanguage> = listOf(AppLanguage.FA),
    val loading: Boolean = true,
)

class SettingsViewModel(
    private val themePreferences: ThemePreferences,
    private val displayPreferences: DisplayPreferences,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        themePreferences.observe(),
        displayPreferences.observeLanguage(),
        displayPreferences.observeCalendar(),
    ) { theme, language, calendar ->
        SettingsUiState(
            themeMode = theme,
            language = language,
            calendar = calendar,
            supportedLanguages = listOf(AppLanguage.FA),
            loading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            themeMode = themePreferences.read(),
            language = displayPreferences.readLanguage(),
            calendar = displayPreferences.readCalendar(),
            loading = false,
        ),
    )

    fun setTheme(mode: ThemeMode) {
        themePreferences.write(mode)
    }

    fun setLanguage(language: AppLanguage) {
        if (language !in listOf(AppLanguage.FA)) return
        displayPreferences.writeLanguage(language)
    }

    fun setCalendar(system: CalendarSystem) {
        displayPreferences.writeCalendar(system)
    }
}
