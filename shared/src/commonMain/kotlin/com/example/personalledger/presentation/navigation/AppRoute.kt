package com.example.personalledger.presentation.navigation

sealed interface AppRoute {
    data object Main : AppRoute
    data class TransactionEditor(val transactionId: String? = null) : AppRoute
    data object Categories : AppRoute
    data object Accounts : AppRoute
}
