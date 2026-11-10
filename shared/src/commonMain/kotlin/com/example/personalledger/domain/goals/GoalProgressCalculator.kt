package com.example.personalledger.domain.goals

import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalProgress

object GoalProgressCalculator {
    fun progress(goal: Goal, allocations: List<GoalAllocation>): GoalProgress {
        require(goal.targetAmountRial > 0L) {
            "Goal target must be positive ریال; got ${goal.targetAmountRial}"
        }
        val allocated = allocations
            .asSequence()
            .filter { it.goalId == goal.id }
            .sumOf { it.amountRial }
        val remaining = (goal.targetAmountRial - allocated).coerceAtLeast(0L)
        val excess = (allocated - goal.targetAmountRial).coerceAtLeast(0L)
        val rawRatio = allocated.toDouble() / goal.targetAmountRial.toDouble()
        val fraction = rawRatio.toFloat().coerceIn(0f, 1f)
        val percent = (rawRatio * 100.0).toInt().coerceIn(0, 100)
        return GoalProgress(
            goal = goal,
            allocatedTotalRial = allocated,
            remainingRial = remaining,
            excessRial = excess,
            progressPercent = percent,
            progressFraction = fraction,
            isTargetReached = allocated >= goal.targetAmountRial,
        )
    }

    fun progressList(
        goals: List<Goal>,
        allocations: List<GoalAllocation>,
    ): List<GoalProgress> {
        val byGoal = allocations.groupBy { it.goalId }
        return goals.map { progress(it, byGoal[it.id].orEmpty()) }
    }
}
