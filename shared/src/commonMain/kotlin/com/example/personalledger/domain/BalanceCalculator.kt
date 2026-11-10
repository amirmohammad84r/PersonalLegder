package com.example.personalledger.domain

import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.AccountBalance
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType

object BalanceCalculator {
    fun totalBalance(
        accounts: List<Account>,
        transactions: List<Transaction>,
        goalAllocations: List<GoalAllocation> = emptyList(),
    ): Long {
        val initials = accounts.sumOf { it.initialBalanceRial }
        return initials + netFromTransactions(transactions) - reservedForGoals(goalAllocations)
    }

    fun accountBalance(
        account: Account,
        transactions: List<Transaction>,
        goalAllocations: List<GoalAllocation> = emptyList(),
    ): Long {
        val related = transactions.filter { it.accountId == account.id }
        val reserved = goalAllocations
            .asSequence()
            .filter { it.accountId == account.id }
            .sumOf { it.amountRial }
        return account.initialBalanceRial + netFromTransactions(related) - reserved
    }

    /**
     * Spendable balance on [account], treating [excludeAllocationId] as not yet reserved
     * (useful when editing an existing goal allocation).
     */
    fun availableForGoalAllocation(
        account: Account,
        transactions: List<Transaction>,
        goalAllocations: List<GoalAllocation>,
        excludeAllocationId: String? = null,
    ): Long {
        val relatedAllocs = goalAllocations.filter {
            it.accountId == account.id && it.id != excludeAllocationId
        }
        return accountBalance(account, transactions, relatedAllocs)
    }

    fun accountBalances(
        accounts: List<Account>,
        transactions: List<Transaction>,
        goalAllocations: List<GoalAllocation> = emptyList(),
    ): List<AccountBalance> =
        accounts.map { account ->
            AccountBalance(
                account = account,
                balanceRial = accountBalance(account, transactions, goalAllocations),
            )
        }

    fun totalIncome(transactions: List<Transaction>): Long =
        transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountRial }

    fun totalExpense(transactions: List<Transaction>): Long =
        transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountRial }

    private fun reservedForGoals(allocations: List<GoalAllocation>): Long =
        allocations.sumOf { it.amountRial }

    private fun netFromTransactions(transactions: List<Transaction>): Long {
        var net = 0L
        for (tx in transactions) {
            net += when (tx.type) {
                TransactionType.INCOME -> tx.amountRial
                TransactionType.EXPENSE -> -tx.amountRial
            }
        }
        return net
    }
}
