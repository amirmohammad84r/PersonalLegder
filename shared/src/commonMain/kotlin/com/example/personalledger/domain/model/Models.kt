package com.example.personalledger.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

enum class TransactionType {
    INCOME,
    EXPENSE,
}

data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val iconKey: String,
)

data class Account(
    val id: String,
    val name: String,
    /** Opening balance in ریال. */
    val initialBalanceRial: Long,
)

data class Transaction(
    val id: String,
    val type: TransactionType,
    /** Amount in ریال. */
    val amountRial: Long,
    val title: String,
    val categoryId: String,
    val accountId: String,
    val date: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AccountBalance(
    val account: Account,
    val balanceRial: Long,
)

data class DashboardSummary(
    /** Balance for selected account, or sum of all accounts when [selectedAccountId] is null. */
    val totalBalanceRial: Long,
    /** Income in the current Jalali month (account-filtered). */
    val monthIncomeRial: Long,
    /** Expense in the current Jalali month (account-filtered). */
    val monthExpenseRial: Long,
    /** Recent txs in the current Jalali month (account-filtered). */
    val recentTransactions: List<Transaction>,
    val accounts: List<Account>,
    /** null = همه حساب‌ها */
    val selectedAccountId: String?,
    val monthLabel: String,
)
