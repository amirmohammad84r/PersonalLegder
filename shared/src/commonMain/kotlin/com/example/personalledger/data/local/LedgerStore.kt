package com.example.personalledger.data.local

import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.IdGenerator
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.Goal
import com.example.personalledger.domain.model.GoalAllocation
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class LedgerStore(
    private val fileStore: PlatformFileStore,
    private val fileName: String = "ledger.json",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(LedgerSnapshotDto())
    val snapshot: StateFlow<LedgerSnapshotDto> = _snapshot.asStateFlow()

    suspend fun initialize() {
        mutex.withLock {
            val raw = fileStore.readText(fileName)
            if (raw.isNullOrBlank()) {
                val seed = seedSnapshot()
                persistLocked(seed)
                _snapshot.value = seed
            } else {
                val decoded = runCatching { json.decodeFromString<LedgerSnapshotDto>(raw) }
                    .getOrElse { seedSnapshot().also { persistLocked(it) } }
                val migrated = decoded.migrateToCurrent()
                if (migrated != decoded) {
                    persistLocked(migrated)
                }
                _snapshot.value = migrated
            }
        }
    }

    suspend fun update(transform: (LedgerSnapshotDto) -> LedgerSnapshotDto) {
        mutex.withLock {
            val next = transform(_snapshot.value)
            persistLocked(next)
            _snapshot.value = next
        }
    }

    fun categories(): List<Category> = _snapshot.value.categories.map { it.toDomain() }
    fun accounts(): List<Account> = _snapshot.value.accounts.map { it.toDomain() }
    fun transactions(): List<Transaction> = _snapshot.value.transactions.map { it.toDomain() }
    fun goals(): List<Goal> = _snapshot.value.goals.map { it.toDomain() }
    fun goalAllocations(): List<GoalAllocation> =
        _snapshot.value.goalAllocations.map { it.toDomain() }

    private fun persistLocked(snapshot: LedgerSnapshotDto) {
        fileStore.writeText(fileName, json.encodeToString(LedgerSnapshotDto.serializer(), snapshot))
    }

    private fun seedSnapshot(): LedgerSnapshotDto {
        val incomeSalary = Category(
            id = IdGenerator.newId(),
            name = "حقوق",
            type = TransactionType.INCOME,
            iconKey = "income",
        )
        val incomeGift = Category(
            id = IdGenerator.newId(),
            name = "هدیه",
            type = TransactionType.INCOME,
            iconKey = "gift",
        )
        val incomeOther = Category(
            id = IdGenerator.newId(),
            name = "سایر درآمد",
            type = TransactionType.INCOME,
            iconKey = "other",
        )
        val expenseFood = Category(
            id = IdGenerator.newId(),
            name = "خوراک",
            type = TransactionType.EXPENSE,
            iconKey = "food",
        )
        val expenseTransport = Category(
            id = IdGenerator.newId(),
            name = "حمل‌ونقل",
            type = TransactionType.EXPENSE,
            iconKey = "transport",
        )
        val expenseShopping = Category(
            id = IdGenerator.newId(),
            name = "خرید",
            type = TransactionType.EXPENSE,
            iconKey = "shopping",
        )
        val expenseHealth = Category(
            id = IdGenerator.newId(),
            name = "سلامت",
            type = TransactionType.EXPENSE,
            iconKey = "health",
        )
        val expenseOther = Category(
            id = IdGenerator.newId(),
            name = "سایر هزینه",
            type = TransactionType.EXPENSE,
            iconKey = "other",
        )
        val cash = Account(
            id = IdGenerator.newId(),
            name = "نقدی",
            initialBalanceRial = 0L,
        )
        return LedgerSnapshotDto(
            version = CurrencyConfig.ledgerSchemaVersion,
            currencyCode = CurrencyConfig.code,
            categories = listOf(
                incomeSalary, incomeGift, incomeOther,
                expenseFood, expenseTransport, expenseShopping, expenseHealth, expenseOther,
            ).map { it.toDto() },
            accounts = listOf(cash).map { it.toDto() },
            transactions = emptyList(),
        )
    }
}
