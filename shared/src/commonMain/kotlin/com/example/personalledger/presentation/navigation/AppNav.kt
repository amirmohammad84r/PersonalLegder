package com.example.personalledger.presentation.navigation

sealed interface AppDestination {
    data object Root : AppDestination
    data class TransactionEditor(val transactionId: String?) : AppDestination
    /** Full transaction list — opened from Home, not a bottom-tab. */
    data object TransactionsList : AppDestination
    /** Categories management — opened from Settings. */
    data object Categories : AppDestination
    /** Accounts management — opened from Settings. */
    data object Accounts : AppDestination
    data class GoalEditor(val goalId: String?) : AppDestination
    data class GoalDetail(val goalId: String) : AppDestination
    data class GoalAllocationEditor(
        val goalId: String,
        val allocationId: String? = null,
    ) : AppDestination
    data class GoalCompleted(val goalId: String) : AppDestination
}
