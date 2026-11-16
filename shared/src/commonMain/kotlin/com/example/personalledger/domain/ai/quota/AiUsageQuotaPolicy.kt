package com.example.personalledger.domain.ai.quota

/**
 * Rolling 24-hour AI chat quota.
 *
 * One user send = one unit, regardless of planning / tools / explanation HTTP rounds.
 */
object AiUsageQuotaPolicy {
    const val MAX_MESSAGES_PER_WINDOW = 10
    const val WINDOW_MILLIS = 24L * 60L * 60L * 1000L

    data class Snapshot(
        val usedInWindow: Int,
        val remaining: Int,
        /** Epoch ms when the oldest counted send leaves the window; null if unused. */
        val nextSlotAvailableAtEpochMs: Long?,
        val nowEpochMs: Long,
    )

    fun snapshot(sendTimestampsEpochMs: List<Long>, nowEpochMs: Long): Snapshot {
        val cutoff = nowEpochMs - WINDOW_MILLIS
        val inWindow = sendTimestampsEpochMs
            .asSequence()
            .filter { it in (cutoff + 1)..nowEpochMs }
            .sorted()
            .toList()
        val used = inWindow.size
        val remaining = (MAX_MESSAGES_PER_WINDOW - used).coerceAtLeast(0)
        val nextSlot = if (used >= MAX_MESSAGES_PER_WINDOW) {
            inWindow.firstOrNull()?.let { it + WINDOW_MILLIS }
        } else {
            null
        }
        return Snapshot(
            usedInWindow = used,
            remaining = remaining,
            nextSlotAvailableAtEpochMs = nextSlot,
            nowEpochMs = nowEpochMs,
        )
    }

    fun canSend(sendTimestampsEpochMs: List<Long>, nowEpochMs: Long): Boolean =
        snapshot(sendTimestampsEpochMs, nowEpochMs).remaining > 0

    /**
     * Prune + append. Rejects clock rollback that would shrink the window unfairly by
     * keeping timestamps already stored (monotonic retention of past sends).
     */
    fun recordSend(existing: List<Long>, nowEpochMs: Long): List<Long> {
        val cutoff = nowEpochMs - WINDOW_MILLIS
        val pruned = existing.filter { it > cutoff }
        return (pruned + nowEpochMs).takeLast(MAX_MESSAGES_PER_WINDOW * 3)
    }
}
