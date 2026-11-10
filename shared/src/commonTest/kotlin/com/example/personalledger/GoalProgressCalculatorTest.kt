package com.example.personalledger

import com.example.personalledger.domain.goals.GoalProgressCalculator
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalProgressCalculatorTest {
    private val now = Instant.fromEpochMilliseconds(0)
    private val day = LocalDate(2026, 1, 1)

    @Test
    fun progress_sumsAllocationsAndComputesRemaining() {
        val goal = goal(target = 1_000_000)
        val allocs = listOf(
            allocation("a1", 300_000),
            allocation("a2", 200_000),
        )
        val progress = GoalProgressCalculator.progress(goal, allocs)
        assertEquals(500_000, progress.allocatedTotalRial)
        assertEquals(500_000, progress.remainingRial)
        assertEquals(0, progress.excessRial)
        assertEquals(50, progress.progressPercent)
        assertEquals(0.5f, progress.progressFraction)
        assertFalse(progress.isTargetReached)
    }

    @Test
    fun progress_clampsPercentWhenOverTarget_andReportsExcess() {
        val goal = goal(target = 100_000)
        val allocs = listOf(allocation("a1", 150_000))
        val progress = GoalProgressCalculator.progress(goal, allocs)
        assertEquals(150_000, progress.allocatedTotalRial)
        assertEquals(0, progress.remainingRial)
        assertEquals(50_000, progress.excessRial)
        assertEquals(100, progress.progressPercent)
        assertEquals(1f, progress.progressFraction)
        assertTrue(progress.isTargetReached)
    }

    @Test
    fun progress_zeroTarget_throws() {
        val goal = goal(target = 0)
        assertFailsWith<IllegalArgumentException> {
            GoalProgressCalculator.progress(goal, emptyList())
        }
    }

    @Test
    fun progress_ignoresOtherGoalsAllocations() {
        val goal = goal(id = "g1", target = 100_000)
        val allocs = listOf(
            allocation("a1", 40_000, goalId = "g1"),
            allocation("a2", 90_000, goalId = "g2"),
        )
        val progress = GoalProgressCalculator.progress(goal, allocs)
        assertEquals(40_000, progress.allocatedTotalRial)
        assertEquals(60_000, progress.remainingRial)
        assertEquals(40, progress.progressPercent)
    }

    @Test
    fun editAndDelete_reflectedInRecalculation() {
        val goal = goal(target = 100_000)
        var allocs = listOf(
            allocation("a1", 30_000),
            allocation("a2", 20_000),
        )
        assertEquals(50_000, GoalProgressCalculator.progress(goal, allocs).allocatedTotalRial)

        allocs = listOf(
            allocation("a1", 45_000),
            allocation("a2", 20_000),
        )
        assertEquals(65_000, GoalProgressCalculator.progress(goal, allocs).allocatedTotalRial)

        allocs = listOf(allocation("a1", 45_000))
        val afterDelete = GoalProgressCalculator.progress(goal, allocs)
        assertEquals(45_000, afterDelete.allocatedTotalRial)
        assertEquals(55_000, afterDelete.remainingRial)
        assertEquals(45, afterDelete.progressPercent)
    }

    private fun goal(id: String = "g1", target: Long) = Goal(
        id = id,
        name = "هدف",
        targetAmountRial = target,
        targetDate = null,
        iconKey = "star",
        status = GoalStatus.ACTIVE,
        completedAt = null,
        createdAt = now,
        updatedAt = now,
    )

    private fun allocation(
        id: String,
        amount: Long,
        goalId: String = "g1",
    ) = GoalAllocation(
        id = id,
        goalId = goalId,
        accountId = "acc-1",
        amountRial = amount,
        date = day,
        note = null,
        createdAt = now,
        updatedAt = now,
    )
}
