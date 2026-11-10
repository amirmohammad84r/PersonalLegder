package com.example.personalledger.presentation.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun NavigateBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
