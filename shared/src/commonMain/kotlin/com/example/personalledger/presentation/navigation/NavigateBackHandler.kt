package com.example.personalledger.presentation.navigation

import androidx.compose.runtime.Composable

/** Platform back (Android system back / gesture). No-op on iOS. */
@Composable
expect fun NavigateBackHandler(enabled: Boolean = true, onBack: () -> Unit)
