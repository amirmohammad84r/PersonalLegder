package com.example.personalledger.data.ai

import com.example.personalledger.data.local.PlatformFileStore
import com.example.personalledger.domain.ai.quota.AiUsageQuotaPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Persistent rolling AI send counters. Local-only; uninstall/reset clears them.
 *
 * Quota is consumed when a user message is accepted for orchestration (before HTTP).
 * Local validation failures before that point do not consume.
 */
class AiUsageQuotaStore(
    private val fileStore: PlatformFileStore,
) {
    fun snapshot(nowEpochMs: Long): AiUsageQuotaPolicy.Snapshot =
        AiUsageQuotaPolicy.snapshot(readTimestamps(), nowEpochMs)

    fun tryConsume(nowEpochMs: Long): AiUsageQuotaPolicy.Snapshot {
        val current = readTimestamps()
        val snap = AiUsageQuotaPolicy.snapshot(current, nowEpochMs)
        if (snap.remaining <= 0) return snap
        persist(AiUsageQuotaPolicy.recordSend(current, nowEpochMs))
        return AiUsageQuotaPolicy.snapshot(readTimestamps(), nowEpochMs)
    }

    fun clear() {
        persist(emptyList())
        runCatching { fileStore.writeText(FILE_NAME, "") }
    }

    private fun readTimestamps(): List<Long> {
        val raw = fileStore.readText(FILE_NAME)?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            aiJson.decodeFromString(AiUsageQuotaDto.serializer(), raw).sendEpochMs
        }.getOrDefault(emptyList())
    }

    private fun persist(timestamps: List<Long>) {
        val dto = AiUsageQuotaDto(sendEpochMs = timestamps)
        fileStore.writeText(FILE_NAME, aiJson.encodeToString(AiUsageQuotaDto.serializer(), dto))
    }

    private companion object {
        const val FILE_NAME = "ai_usage_quota.json"
    }
}

@Serializable
private data class AiUsageQuotaDto(
    val sendEpochMs: List<Long> = emptyList(),
)
