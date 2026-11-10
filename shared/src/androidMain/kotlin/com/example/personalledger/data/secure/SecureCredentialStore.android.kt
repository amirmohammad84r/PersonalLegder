package com.example.personalledger.data.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * EncryptedSharedPreferences with a master key stored in Android Keystore.
 */
class AndroidSecureCredentialStore(context: Context) : SecureCredentialStore {
    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    override fun putSecret(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getSecret(key: String): String? = prefs.getString(key, null)

    override fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        private const val FILE_NAME = "ai_secure_credentials"

        private fun createPrefs(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
