package com.example.personalledger.data.local

expect class PlatformFileStore {
    fun readText(fileName: String): String?
    fun writeText(fileName: String, content: String)
}
