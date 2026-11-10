package com.example.personalledger

import com.example.personalledger.data.ai.AiSettingsStore
import com.example.personalledger.data.ai.provider.CompatibleModelItem
import com.example.personalledger.data.ai.provider.OpenAiAdapter
import com.example.personalledger.data.ai.provider.OpenAiCompatibleModelFilters
import com.example.personalledger.data.secure.SecureCredentialKeys
import com.example.personalledger.data.secure.SecureCredentialStore
import com.example.personalledger.domain.ai.AiProviderId
import com.example.personalledger.presentation.screens.ai.sanitizeAssistantMarkdown
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAssistantHelpersTest {
    @Test
    fun openAiFiltersNonChatModels() {
        assertTrue(OpenAiAdapter.isChatCapableModel("gpt-4o-mini"))
        assertTrue(OpenAiAdapter.isChatCapableModel("o3-mini"))
        assertFalse(OpenAiAdapter.isChatCapableModel("text-embedding-3-large"))
        assertFalse(OpenAiAdapter.isChatCapableModel("whisper-1"))
        assertFalse(OpenAiAdapter.isChatCapableModel("dall-e-3"))
    }

    @Test
    fun deepSeekAndGapGptFiltersMatchOfficialChatModels() {
        assertTrue(
            OpenAiCompatibleModelFilters.deepSeekChat(
                CompatibleModelItem(id = "deepseek-flash", name = "DeepSeek-V4.1-Flash"),
            ),
        )
        assertTrue(
            OpenAiCompatibleModelFilters.deepSeekChat(
                CompatibleModelItem(id = "deepseek-v4-pro"),
            ),
        )
        assertFalse(
            OpenAiCompatibleModelFilters.deepSeekChat(
                CompatibleModelItem(id = "text-embedding-3"),
            ),
        )
        assertTrue(
            OpenAiCompatibleModelFilters.gapGptChat(
                CompatibleModelItem(id = "gpt-5.6-sol"),
            ),
        )
        assertFalse(
            OpenAiCompatibleModelFilters.gapGptChat(
                CompatibleModelItem(id = "gpt-image-2"),
            ),
        )
        assertTrue(AiProviderId.entries.map { it.storageKey }.containsAll(listOf("deepseek", "gapgpt")))
    }

    @Test
    fun sanitizeStripsHtmlAndLinks() {
        val raw = """<script>alert(1)</script><b>سلام</b> [متن](https://evil.example)"""
        val clean = sanitizeAssistantMarkdown(raw)
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains("script"))
        assertFalse(clean.contains("https://"))
        assertTrue(clean.contains("سلام"))
        assertTrue(clean.contains("متن"))
    }

    @Test
    fun settingsResetClearsKeyProviderAndValidation() {
        val secure = InMemorySecureCredentialStore()
        val settings = InMemorySettings()
        val store = AiSettingsStore(settings, secure)
        store.saveValidatedConnection(
            providerId = AiProviderId.OPENAI,
            modelId = "gpt-4o-mini",
            apiKey = "sk-test-key-should-not-leak",
            validatedAtEpochMs = 123L,
        )
        assertTrue(store.isReady())
        assertEquals("sk-test-key-should-not-leak", store.readApiKey())

        store.resetAllSecretsAndSettings()
        assertFalse(store.isReady())
        assertNull(store.readConnection())
        assertNull(store.readApiKey())
        assertNull(secure.getSecret(SecureCredentialKeys.AI_API_KEY))
        assertNull(settings.getStringOrNull("ai_provider_id"))
    }

    @Test
    fun previousConnectionRemainsUntilSuccessfulSave() {
        val secure = InMemorySecureCredentialStore()
        val settings = InMemorySettings()
        val store = AiSettingsStore(settings, secure)
        store.saveValidatedConnection(
            providerId = AiProviderId.GEMINI,
            modelId = "gemini-2.0-flash",
            apiKey = "valid-key",
            validatedAtEpochMs = 1L,
        )
        assertEquals(AiProviderId.GEMINI, store.readConnection()?.providerId)
        assertEquals("gemini-2.0-flash", store.readConnection()?.modelId)
        assertEquals("valid-key", store.readApiKey())
        assertTrue(store.isReady())
    }
}

class InMemorySecureCredentialStore : SecureCredentialStore {
    private val map = linkedMapOf<String, String>()

    override fun putSecret(key: String, value: String) {
        map[key] = value
    }

    override fun getSecret(key: String): String? = map[key]

    override fun removeSecret(key: String) {
        map.remove(key)
    }

    override fun clear() {
        map.clear()
    }
}

class InMemorySettings : Settings {
    private val strings = linkedMapOf<String, String>()
    private val bools = linkedMapOf<String, Boolean>()
    private val longs = linkedMapOf<String, Long>()
    private val ints = linkedMapOf<String, Int>()
    private val floats = linkedMapOf<String, Float>()
    private val doubles = linkedMapOf<String, Double>()

    override val keys: Set<String>
        get() = strings.keys + bools.keys + longs.keys + ints.keys + floats.keys + doubles.keys

    override val size: Int get() = keys.size

    override fun clear() {
        strings.clear()
        bools.clear()
        longs.clear()
        ints.clear()
        floats.clear()
        doubles.clear()
    }

    override fun remove(key: String) {
        strings.remove(key)
        bools.remove(key)
        longs.remove(key)
        ints.remove(key)
        floats.remove(key)
        doubles.remove(key)
    }

    override fun hasKey(key: String): Boolean = key in keys

    override fun putInt(key: String, value: Int) {
        ints[key] = value
    }

    override fun getInt(key: String, defaultValue: Int): Int = ints[key] ?: defaultValue

    override fun getIntOrNull(key: String): Int? = ints[key]

    override fun putLong(key: String, value: Long) {
        longs[key] = value
    }

    override fun getLong(key: String, defaultValue: Long): Long = longs[key] ?: defaultValue

    override fun getLongOrNull(key: String): Long? = longs[key]

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getString(key: String, defaultValue: String): String = strings[key] ?: defaultValue

    override fun getStringOrNull(key: String): String? = strings[key]

    override fun putFloat(key: String, value: Float) {
        floats[key] = value
    }

    override fun getFloat(key: String, defaultValue: Float): Float = floats[key] ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = floats[key]

    override fun putDouble(key: String, value: Double) {
        doubles[key] = value
    }

    override fun getDouble(key: String, defaultValue: Double): Double = doubles[key] ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = doubles[key]

    override fun putBoolean(key: String, value: Boolean) {
        bools[key] = value
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = bools[key] ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = bools[key]
}
