package com.example.personalledger.data.local

import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.GoalStatus
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LedgerSnapshotDto(
    val version: Int = CurrencyConfig.ledgerSchemaVersion,
    val currencyCode: String = CurrencyConfig.code,
    val categories: List<CategoryDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val transactions: List<TransactionDto> = emptyList(),
    /** Savings goals — independent of transactions/balances. */
    val goals: List<GoalDto> = emptyList(),
    val goalAllocations: List<GoalAllocationDto> = emptyList(),
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val type: String,
    val iconKey: String,
)

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val initialBalanceRial: Long = 0L,
    /** v1 JSON key `initialBalanceMinor` stored تومان. */
    @SerialName("initialBalanceMinor")
    val legacyInitialBalanceToman: Long? = null,
)

@Serializable
data class TransactionDto(
    val id: String,
    val type: String,
    val amountRial: Long = 0L,
    /** v1 JSON key `amountMinor` stored تومان. */
    @SerialName("amountMinor")
    val legacyAmountToman: Long? = null,
    val title: String,
    val categoryId: String,
    val accountId: String,
    val dateEpochDays: Long,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun CategoryDto.toDomain(): Category =
    Category(
        id = id,
        name = name,
        type = TransactionType.valueOf(type),
        iconKey = iconKey,
    )

fun Category.toDto(): CategoryDto =
    CategoryDto(
        id = id,
        name = name,
        type = type.name,
        iconKey = iconKey,
    )

fun AccountDto.toDomain(): Account =
    Account(
        id = id,
        name = name,
        initialBalanceRial = initialBalanceRial,
    )

fun Account.toDto(): AccountDto =
    AccountDto(
        id = id,
        name = name,
        initialBalanceRial = initialBalanceRial,
    )

fun TransactionDto.toDomain(): Transaction =
    Transaction(
        id = id,
        type = TransactionType.valueOf(type),
        amountRial = amountRial,
        title = title,
        categoryId = categoryId,
        accountId = accountId,
        date = LocalDate.fromEpochDays(dateEpochDays.toInt()),
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
    )

fun Transaction.toDto(): TransactionDto =
    TransactionDto(
        id = id,
        type = type.name,
        amountRial = amountRial,
        title = title,
        categoryId = categoryId,
        accountId = accountId,
        dateEpochDays = date.toEpochDays().toLong(),
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
    )

@Serializable
data class GoalDto(
    val id: String,
    val name: String,
    val targetAmountRial: Long,
    val targetDateEpochDays: Long? = null,
    val iconKey: String? = null,
    val status: String = GoalStatus.ACTIVE.name,
    val completedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

@Serializable
data class GoalAllocationDto(
    val id: String,
    val goalId: String,
    val accountId: String = "",
    val amountRial: Long,
    val dateEpochDays: Long,
    val note: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

fun GoalDto.toDomain(): Goal =
    Goal(
        id = id,
        name = name,
        targetAmountRial = targetAmountRial,
        targetDate = targetDateEpochDays?.let { LocalDate.fromEpochDays(it.toInt()) },
        iconKey = iconKey,
        status = GoalStatus.valueOf(status),
        completedAt = completedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
    )

fun Goal.toDto(): GoalDto =
    GoalDto(
        id = id,
        name = name,
        targetAmountRial = targetAmountRial,
        targetDateEpochDays = targetDate?.toEpochDays()?.toLong(),
        iconKey = iconKey,
        status = status.name,
        completedAtEpochMs = completedAt?.toEpochMilliseconds(),
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
    )

fun GoalAllocationDto.toDomain(): GoalAllocation =
    GoalAllocation(
        id = id,
        goalId = goalId,
        accountId = accountId,
        amountRial = amountRial,
        date = LocalDate.fromEpochDays(dateEpochDays.toInt()),
        note = note,
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMs),
    )

fun GoalAllocation.toDto(): GoalAllocationDto =
    GoalAllocationDto(
        id = id,
        goalId = goalId,
        accountId = accountId,
        amountRial = amountRial,
        dateEpochDays = date.toEpochDays().toLong(),
        note = note,
        createdAtEpochMs = createdAt.toEpochMilliseconds(),
        updatedAtEpochMs = updatedAt.toEpochMilliseconds(),
    )

/**
 * v1 amounts were تومان (documented in previous CurrencyConfig).
 * v2 amounts are ریال. Multiply by 10 exactly once.
 */
fun LedgerSnapshotDto.migrateToCurrent(): LedgerSnapshotDto {
    if (version >= CurrencyConfig.ledgerSchemaVersion && currencyCode == CurrencyConfig.code) {
        return copy(
            accounts = accounts.map { it.copy(legacyInitialBalanceToman = null) },
            transactions = transactions.map { it.copy(legacyAmountToman = null) },
        )
    }
    require(version == CurrencyConfig.legacyTomanSchemaVersion) {
        "Unsupported ledger version=$version currency=$currencyCode. " +
            "Refusing silent conversion; expected v1 تومان → v2 ریال."
    }
    return copy(
        version = CurrencyConfig.ledgerSchemaVersion,
        currencyCode = CurrencyConfig.code,
        accounts = accounts.map { acc ->
            val toman = acc.legacyInitialBalanceToman
                ?: error("v1 account ${acc.id} missing initialBalanceMinor; refusing guess")
            acc.copy(
                initialBalanceRial = toman * 10L,
                legacyInitialBalanceToman = null,
            )
        },
        transactions = transactions.map { tx ->
            val toman = tx.legacyAmountToman
                ?: error("v1 transaction ${tx.id} missing amountMinor; refusing guess")
            tx.copy(
                amountRial = toman * 10L,
                legacyAmountToman = null,
            )
        },
    )
}
