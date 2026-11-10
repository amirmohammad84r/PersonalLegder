package com.example.personalledger.data.local

import android.content.Context
import java.io.File

actual class PlatformFileStore(private val context: Context) {
    actual fun readText(fileName: String): String? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return null
        return file.readText()
    }

    actual fun writeText(fileName: String, content: String) {
        File(context.filesDir, fileName).writeText(content)
    }
}
