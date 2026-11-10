package com.example.personalledger.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.personalledger.data.prefs.ThemeMode

private val LightScheme = lightColorScheme(
    primary = LedgerColors.LightGold,
    onPrimary = LedgerColors.LightCard,
    primaryContainer = LedgerColors.LightGoldBright.copy(alpha = 0.28f),
    onPrimaryContainer = LedgerColors.LightGoldDeep,
    secondary = LedgerColors.LightGoldDeep,
    onSecondary = LedgerColors.LightCard,
    secondaryContainer = LedgerColors.LightSurfaceMuted,
    onSecondaryContainer = LedgerColors.LightText,
    tertiary = LedgerColors.IncomeGreen,
    onTertiary = LedgerColors.LightCard,
    background = LedgerColors.LightBackground,
    onBackground = LedgerColors.LightText,
    surface = LedgerColors.LightCard,
    onSurface = LedgerColors.LightText,
    surfaceVariant = LedgerColors.LightSurfaceMuted,
    onSurfaceVariant = LedgerColors.LightTextMuted,
    outline = LedgerColors.LightOutline,
    outlineVariant = LedgerColors.LightOutline.copy(alpha = 0.7f),
    error = LedgerColors.ExpenseRose,
    onError = LedgerColors.LightCard,
    inverseSurface = LedgerColors.DarkCard,
    inverseOnSurface = LedgerColors.DarkText,
    inversePrimary = LedgerColors.DarkGold,
)

private val DarkScheme = darkColorScheme(
    primary = LedgerColors.DarkGold,
    onPrimary = LedgerColors.DarkBackground,
    primaryContainer = LedgerColors.DarkGoldDeep.copy(alpha = 0.45f),
    onPrimaryContainer = LedgerColors.DarkGoldBright,
    secondary = LedgerColors.DarkGoldBright,
    onSecondary = LedgerColors.DarkBackground,
    secondaryContainer = LedgerColors.DarkSurfaceMuted,
    onSecondaryContainer = LedgerColors.DarkText,
    tertiary = LedgerColors.IncomeGreen,
    onTertiary = LedgerColors.DarkText,
    background = LedgerColors.DarkBackground,
    onBackground = LedgerColors.DarkText,
    surface = LedgerColors.DarkCard,
    onSurface = LedgerColors.DarkText,
    surfaceVariant = LedgerColors.DarkSurfaceMuted,
    onSurfaceVariant = LedgerColors.DarkTextMuted,
    outline = LedgerColors.DarkOutline,
    outlineVariant = LedgerColors.DarkOutline.copy(alpha = 0.75f),
    error = LedgerColors.ExpenseRose,
    onError = LedgerColors.DarkText,
    inverseSurface = LedgerColors.LightCard,
    inverseOnSurface = LedgerColors.LightText,
    inversePrimary = LedgerColors.LightGold,
)

val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PersonalLedgerTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = LedgerShapes,
        content = content,
    )
}

/**
 * Outlined fields: transparent container so the floating-label cutout
 * inherits the real parent (screen / dialog) instead of a mismatched surface patch.
 */
@Composable
fun ledgerOutlinedFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = scheme.outline,
        disabledBorderColor = scheme.outline.copy(alpha = 0.6f),
        errorBorderColor = scheme.error,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedLabelColor = scheme.primary,
        unfocusedLabelColor = scheme.onSurfaceVariant,
        disabledLabelColor = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        errorLabelColor = scheme.error,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurface,
        errorTextColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
        errorCursorColor = scheme.error,
        focusedSupportingTextColor = scheme.onSurfaceVariant,
        unfocusedSupportingTextColor = scheme.onSurfaceVariant,
        errorSupportingTextColor = scheme.error,
        focusedTrailingIconColor = scheme.onSurfaceVariant,
        unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        disabledTrailingIconColor = scheme.onSurfaceVariant,
        focusedLeadingIconColor = scheme.onSurfaceVariant,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
    )
}
