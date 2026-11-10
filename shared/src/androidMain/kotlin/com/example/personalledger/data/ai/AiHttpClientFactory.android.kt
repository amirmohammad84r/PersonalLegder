package com.example.personalledger.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

actual fun createAiHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        installAiDefaults()
        engine {
            preconfigured = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }
    }
