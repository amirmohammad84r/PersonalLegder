package com.example.personalledger.data.repository

import com.example.personalledger.data.local.LedgerStore
import com.example.personalledger.data.local.toDomain
import com.example.personalledger.data.local.toDto
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepositoryImpl(
    private val store: LedgerStore,
) : TransactionRepository {
    override fun observeAll(): Flow<List<Transaction>> =
        store.snapshot.map { snap -> snap.transactions.map { it.toDomain() } }

    override suspend fun listAll(): List<Transaction> = store.transactions()

    override suspend fun getById(id: String): Transaction? =
        store.transactions().find { it.id == id }

    override suspend fun upsert(transaction: Transaction) {
        store.update { snap ->
            val others = snap.transactions.filterNot { it.id == transaction.id }
            snap.copy(transactions = others + transaction.toDto())
        }
    }

    override suspend fun delete(id: String) {
        store.update { snap ->
            snap.copy(transactions = snap.transactions.filterNot { it.id == id })
        }
    }

    override suspend fun deleteByAccountId(accountId: String): Int {
        var removed = 0
        store.update { snap ->
            val kept = snap.transactions.filterNot { it.accountId == accountId }
            removed = snap.transactions.size - kept.size
            snap.copy(transactions = kept)
        }
        return removed
    }
}

class CategoryRepositoryImpl(
    private val store: LedgerStore,
) : CategoryRepository {
    override fun observeAll(): Flow<List<Category>> =
        store.snapshot.map { snap -> snap.categories.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? =
        store.categories().find { it.id == id }

    override suspend fun upsert(category: Category) {
        store.update { snap ->
            val others = snap.categories.filterNot { it.id == category.id }
            snap.copy(categories = others + category.toDto())
        }
    }

    override suspend fun delete(id: String) {
        store.update { snap ->
            snap.copy(categories = snap.categories.filterNot { it.id == id })
        }
    }

    override suspend fun countTransactions(categoryId: String): Int =
        store.transactions().count { it.categoryId == categoryId }
}

class AccountRepositoryImpl(
    private val store: LedgerStore,
) : AccountRepository {
    override fun observeAll(): Flow<List<Account>> =
        store.snapshot.map { snap -> snap.accounts.map { it.toDomain() } }

    override suspend fun getById(id: String): Account? =
        store.accounts().find { it.id == id }

    override suspend fun upsert(account: Account) {
        store.update { snap ->
            val others = snap.accounts.filterNot { it.id == account.id }
            snap.copy(accounts = others + account.toDto())
        }
    }

    override suspend fun delete(id: String) {
        store.update { snap ->
            snap.copy(accounts = snap.accounts.filterNot { it.id == id })
        }
    }

    override suspend fun countTransactions(accountId: String): Int =
        store.transactions().count { it.accountId == accountId }
}

class GoalRepositoryImpl(
    private val store: LedgerStore,
) : GoalRepository {
    override fun observeAll(): Flow<List<Goal>> =
        store.snapshot.map { snap -> snap.goals.map { it.toDomain() } }

    override suspend fun getById(id: String): Goal? =
        store.goals().find { it.id == id }

    override suspend fun upsert(goal: Goal) {
        store.update { snap ->
            val others = snap.goals.filterNot { it.id == goal.id }
            snap.copy(goals = others + goal.toDto())
        }
    }

    override suspend fun delete(id: String) {
        store.update { snap ->
            snap.copy(goals = snap.goals.filterNot { it.id == id })
        }
    }
}

class GoalAllocationRepositoryImpl(
    private val store: LedgerStore,
) : GoalAllocationRepository {
    override fun observeAll(): Flow<List<GoalAllocation>> =
        store.snapshot.map { snap -> snap.goalAllocations.map { it.toDomain() } }

    override fun observeByGoalId(goalId: String): Flow<List<GoalAllocation>> =
        observeAll().map { list ->
            list
                .filter { it.goalId == goalId }
                .sortedWith(
                    compareByDescending<GoalAllocation> { it.date }
                        .thenByDescending { it.createdAt },
                )
        }

    override suspend fun listAll(): List<GoalAllocation> = store.goalAllocations()

    override suspend fun listByGoalId(goalId: String): List<GoalAllocation> =
        store.goalAllocations()
            .filter { it.goalId == goalId }
            .sortedWith(
                compareByDescending<GoalAllocation> { it.date }
                    .thenByDescending { it.createdAt },
            )

    override suspend fun getById(id: String): GoalAllocation? =
        store.goalAllocations().find { it.id == id }

    override suspend fun upsert(allocation: GoalAllocation) {
        store.update { snap ->
            val others = snap.goalAllocations.filterNot { it.id == allocation.id }
            snap.copy(goalAllocations = others + allocation.toDto())
        }
    }

    override suspend fun delete(id: String) {
        store.update { snap ->
            snap.copy(goalAllocations = snap.goalAllocations.filterNot { it.id == id })
        }
    }

    override suspend fun deleteByGoalId(goalId: String): Int {
        var removed = 0
        store.update { snap ->
            val kept = snap.goalAllocations.filterNot { it.goalId == goalId }
            removed = snap.goalAllocations.size - kept.size
            snap.copy(goalAllocations = kept)
        }
        return removed
    }

    override suspend fun deleteByAccountId(accountId: String): Int {
        var removed = 0
        store.update { snap ->
            val kept = snap.goalAllocations.filterNot { it.accountId == accountId }
            removed = snap.goalAllocations.size - kept.size
            snap.copy(goalAllocations = kept)
        }
        return removed
    }

    override suspend fun countByGoalId(goalId: String): Int =
        store.goalAllocations().count { it.goalId == goalId }

    override suspend fun countByAccountId(accountId: String): Int =
        store.goalAllocations().count { it.accountId == accountId }
}
