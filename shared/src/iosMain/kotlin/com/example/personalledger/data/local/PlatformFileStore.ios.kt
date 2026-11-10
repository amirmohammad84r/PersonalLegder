package com.example.personalledger.data.local

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual class PlatformFileStore {
    private fun path(fileName: String): String {
        val urls = NSFileManager.defaultManager.URLsForDirectory(
            directory = NSDocumentDirectory,
            inDomains = NSUserDomainMask,
        )
        val dir = urls.first() as platform.Foundation.NSURL
        return dir.URLByAppendingPathComponent(fileName)!!.path!!
    }

    actual fun readText(fileName: String): String? {
        return NSString.stringWithContentsOfFile(
            path = path(fileName),
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    actual fun writeText(fileName: String, content: String) {
        (content as NSString).writeToFile(
            path = path(fileName),
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }
}
