package com.example.personalledger.data.secure

/**
 * Platform-backed encrypted secret storage.
 * API keys must never be written to plain Settings / plaintext files.
 */
interface SecureCredentialStore {
    fun putSecret(key: String, value: String)
    fun getSecret(key: String): String?
    fun removeSecret(key: String)
    fun clear()
}

object SecureCredentialKeys {
    const val AI_API_KEY = "ai_provider_api_key"
}
