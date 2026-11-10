package com.example.personalledger.data.secure

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain-backed secret storage (device-local).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureCredentialStore : SecureCredentialStore {
    private val serviceName = "com.example.personalledger.ai.secure"

    override fun putSecret(key: String, value: String) {
        val payload = nsString(value).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        delete(key)
        val query = baseQuery(key)
        CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(payload))
        SecItemAdd(query, null)
    }

    override fun getSecret(key: String): String? = memScoped {
        val query = baseQuery(key)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess) return null
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    override fun removeSecret(key: String) {
        delete(key)
    }

    override fun clear() {
        val query = requireNotNull(
            CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null),
        )
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(nsString(serviceName)))
        SecItemDelete(query)
    }

    private fun delete(key: String) {
        SecItemDelete(baseQuery(key))
    }

    private fun baseQuery(account: String): CFDictionaryRef {
        val dict = requireNotNull(
            CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null),
        )
        CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(dict, kSecAttrService, CFBridgingRetain(nsString(serviceName)))
        CFDictionarySetValue(dict, kSecAttrAccount, CFBridgingRetain(nsString(account)))
        return dict
    }

    private fun nsString(value: String): NSString = NSString.create(string = value)
}
