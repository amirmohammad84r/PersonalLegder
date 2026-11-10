package com.example.personalledger.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createAiHttpClient(): HttpClient =
    HttpClient(Darwin) {
        installAiDefaults()
    }
