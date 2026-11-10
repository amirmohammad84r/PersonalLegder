package com.example.personalledger.presentation.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.personalledger.data.prefs.AppLanguage
import com.example.personalledger.data.prefs.ThemeMode
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.presentation.components.LedgerCard
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.ScreenHeader
import com.example.personalledger.presentation.components.ScreenPadding
import com.example.personalledger.presentation.strings.Strings

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingBox()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        ScreenHeader(title = Strings.settings)
        Spacer(Modifier.height(8.dp))

        SettingsSectionTitle(Strings.management)
        LedgerCard {
            SettingsNavRow(
                title = Strings.categories,
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                onClick = onOpenCategories,
            )
            Spacer(Modifier.height(4.dp))
            SettingsNavRow(
                title = Strings.accounts,
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                onClick = onOpenAccounts,
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(Strings.appearance)
        LedgerCard {
            Text(Strings.theme, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            ThemeOption(Strings.themeSystem, state.themeMode == ThemeMode.SYSTEM) {
                viewModel.setTheme(ThemeMode.SYSTEM)
            }
            ThemeOption(Strings.themeLight, state.themeMode == ThemeMode.LIGHT) {
                viewModel.setTheme(ThemeMode.LIGHT)
            }
            ThemeOption(Strings.themeDark, state.themeMode == ThemeMode.DARK) {
                viewModel.setTheme(ThemeMode.DARK)
            }
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(Strings.language)
        LedgerCard {
            state.supportedLanguages.forEach { language ->
                ThemeOption(
                    label = languageLabel(language),
                    selected = state.language == language,
                    onClick = { viewModel.setLanguage(language) },
                )
            }
            Text(
                text = Strings.languageOnlyPersianHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        SettingsSectionTitle(Strings.calendar)
        LedgerCard {
            ThemeOption(
                Strings.calendarJalali,
                state.calendar == CalendarSystem.JALALI,
            ) { viewModel.setCalendar(CalendarSystem.JALALI) }
            ThemeOption(
                Strings.calendarGregorian,
                state.calendar == CalendarSystem.GREGORIAN,
            ) { viewModel.setCalendar(CalendarSystem.GREGORIAN) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SettingsNavRow(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
    }
}

private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.FA -> Strings.languagePersian
}
