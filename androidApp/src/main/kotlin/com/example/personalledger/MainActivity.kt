package com.example.personalledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.personalledger.data.local.PlatformFileStore
import com.example.personalledger.data.secure.AndroidSecureCredentialStore
import com.example.personalledger.di.AppContainer

class MainActivity : ComponentActivity() {
    private val container by lazy {
        AppContainer(
            fileStore = PlatformFileStore(applicationContext),
            secureCredentialStore = AndroidSecureCredentialStore(applicationContext),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            App(container)
        }
    }
}
