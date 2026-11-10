package com.example.personalledger.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Central design tokens. Screens should use MaterialTheme.colorScheme
 * or these semantic tokens — avoid hard-coded hex in UI.
 */
object LedgerColors {
    // —— Light ——
    val LightBackground = Color(0xFFF1E7D5)
    val LightCard = Color(0xFFFBF7EF)
    val LightSurfaceMuted = Color(0xFFE9DDC8)
    val LightText = Color(0xFF382D20)
    val LightTextMuted = Color(0xFF766956)
    val LightOutline = Color(0xFFD9CBB2)
    val LightGold = Color(0xFFB88727)
    val LightGoldBright = Color(0xFFC99B3B)
    val LightGoldDeep = Color(0xFF91651A)

    // —— Dark ——
    val DarkBackground = Color(0xFF100D0A)
    val DarkCard = Color(0xFF1C1711)
    val DarkSurfaceMuted = Color(0xFF252018)
    val DarkText = Color(0xFFF3EBDD)
    val DarkTextMuted = Color(0xFFB7AA95)
    val DarkOutline = Color(0xFF393025)
    val DarkGold = Color(0xFFD4A63A)
    val DarkGoldBright = Color(0xFFE0B84F)
    val DarkGoldDeep = Color(0xFFA97B22)

    // —— Semantic (shared) ——
    val IncomeGreen = Color(0xFF5F7A4A)
    val ExpenseRose = Color(0xFFA85A4A)
}
