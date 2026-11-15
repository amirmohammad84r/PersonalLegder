package com.example.personalledger.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType

actual fun createAiHttpClient(): HttpClient =
    HttpClient(Darwin) {
        installAiDefaults()
        install(DefaultRequest) {
            contentType(ContentType.Application.Json)
        }
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
            configureSession {
                timeoutIntervalForRequest = 60.0
                timeoutIntervalForResource = 90.0
                waitsForConnectivity = true
            }
        }
    }
