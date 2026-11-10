package com.example.personalledger

import androidx.compose.ui.window.ComposeUIViewController
import com.example.personalledger.data.local.PlatformFileStore
import com.example.personalledger.data.secure.IosSecureCredentialStore
import com.example.personalledger.di.AppContainer

fun MainViewController() = ComposeUIViewController {
    App(
        AppContainer(
            fileStore = PlatformFileStore(),
            secureCredentialStore = IosSecureCredentialStore(),
        ),
    )
}
