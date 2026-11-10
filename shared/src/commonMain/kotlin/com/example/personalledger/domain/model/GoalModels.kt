package com.example.personalledger.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class GoalStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED,
}

data class Goal(
    val id: String,
    val name: String,
    /** Target savings amount in ریال; must be > 0. */
    val targetAmountRial: Long,
    /** Optional deadline; stored as LocalDate (epoch days in JSON). */
    val targetDate: LocalDate?,
    /** Optional icon key for UI mapping. */
    val iconKey: String?,
    val status: GoalStatus,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Money set aside for a goal from a specific account.
 * Reduces that account's available balance until the allocation (or goal) is deleted.
 * Does not create income/expense transactions.
 */
data class GoalAllocation(
    val id: String,
    val goalId: String,
    /** Account this amount is reserved from. */
    val accountId: String,
    /** Amount in ریال. */
    val amountRial: Long,
    val date: LocalDate,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Derived progress for a goal from its allocations. */
data class GoalProgress(
    val goal: Goal,
    val allocatedTotalRial: Long,
    /** max(0, target − allocated) */
    val remainingRial: Long,
    /** max(0, allocated − target) when overfunded */
    val excessRial: Long,
    /** Display percent clamped to 0..100 */
    val progressPercent: Int,
    /** Bar fraction clamped to 0f..1f */
    val progressFraction: Float,
    val isTargetReached: Boolean,
)
