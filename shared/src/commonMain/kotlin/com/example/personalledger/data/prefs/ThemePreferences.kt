package com.example.personalledger.data.prefs

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.Settings
import com.russhwolf.settings.coroutines.getStringFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

class ThemePreferences(
    private val settings: Settings,
) {
    private val key = "theme_mode"

    @OptIn(ExperimentalSettingsApi::class)
    fun observe(): Flow<ThemeMode> {
        val observable = settings as? ObservableSettings
            ?: return kotlinx.coroutines.flow.flow {
                emit(read())
            }
        return observable.getStringFlow(key, ThemeMode.SYSTEM.name).map { raw ->
            runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        }
    }

    fun read(): ThemeMode =
        runCatching { ThemeMode.valueOf(settings.getString(key, ThemeMode.SYSTEM.name)) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun write(mode: ThemeMode) {
        settings.putString(key, mode.name)
    }
}
