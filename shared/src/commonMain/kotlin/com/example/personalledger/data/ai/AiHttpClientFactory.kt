package com.example.personalledger.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

expect fun createAiHttpClient(): HttpClient

internal val aiJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

internal fun HttpClientConfig<*>.installAiDefaults() {
    expectSuccess = false
    install(ContentNegotiation) {
        json(aiJson)
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
    }
}
