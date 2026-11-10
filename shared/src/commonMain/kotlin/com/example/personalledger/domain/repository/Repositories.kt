package com.example.personalledger.domain.repository

import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeAll(): Flow<List<Transaction>>
    suspend fun listAll(): List<Transaction>
    suspend fun getById(id: String): Transaction?
    suspend fun upsert(transaction: Transaction)
    suspend fun delete(id: String)
    /** Deletes all transactions for [accountId]; returns how many were removed. */
    suspend fun deleteByAccountId(accountId: String): Int
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun getById(id: String): Category?
    suspend fun upsert(category: Category)
    suspend fun delete(id: String)
    suspend fun countTransactions(categoryId: String): Int
}

interface AccountRepository {
    fun observeAll(): Flow<List<Account>>
    suspend fun getById(id: String): Account?
    suspend fun upsert(account: Account)
    suspend fun delete(id: String)
    suspend fun countTransactions(accountId: String): Int
}

interface GoalRepository {
    fun observeAll(): Flow<List<Goal>>
    suspend fun getById(id: String): Goal?
    suspend fun upsert(goal: Goal)
    suspend fun delete(id: String)
}

interface GoalAllocationRepository {
    fun observeAll(): Flow<List<GoalAllocation>>
    fun observeByGoalId(goalId: String): Flow<List<GoalAllocation>>
    suspend fun listAll(): List<GoalAllocation>
    suspend fun listByGoalId(goalId: String): List<GoalAllocation>
    suspend fun getById(id: String): GoalAllocation?
    suspend fun upsert(allocation: GoalAllocation)
    suspend fun delete(id: String)
    /** Deletes all allocations for [goalId]; returns how many were removed. */
    suspend fun deleteByGoalId(goalId: String): Int
    /** Deletes all allocations reserved from [accountId]; returns how many were removed. */
    suspend fun deleteByAccountId(accountId: String): Int
    suspend fun countByGoalId(goalId: String): Int
    suspend fun countByAccountId(accountId: String): Int
}

sealed class DomainException(message: String) : Exception(message) {
    class Validation(message: String) : DomainException(message)
    class NotFound(message: String) : DomainException(message)
    class Conflict(message: String) : DomainException(message)
}

data class TransactionDraft(
    val id: String? = null,
    val type: TransactionType,
    val amountRial: Long?,
    val title: String,
    val categoryId: String?,
    val accountId: String?,
    val dateEpochDays: Long?,
)

data class GoalDraft(
    val id: String? = null,
    val name: String,
    val targetAmountRial: Long?,
    val targetDateEpochDays: Long?,
    val iconKey: String?,
)

data class GoalAllocationDraft(
    val id: String? = null,
    val goalId: String,
    val accountId: String?,
    val amountRial: Long?,
    val dateEpochDays: Long?,
    val note: String?,
)
