package com.example.personalledger.presentation.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun NavigateBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses in-app back controls; system back is not used the same way.
}
