package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.BalanceCalculator
import com.example.personalledger.domain.IdGenerator
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.AccountBalance
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.DashboardSummary
import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.nowInstant
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.TransactionDraft
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.todayLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class ObserveDashboardUseCase(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val goalAllocations: GoalAllocationRepository,
) {
    /**
     * @param accountId null = همه حساب‌ها؛ در غیر این صورت فقط همان حساب
     * درآمد/هزینه/تراکنش‌های اخیر محدود به ماه جاری در [calendar] هستند؛ موجودی کل است.
     */
    operator fun invoke(
        accountId: String? = null,
        calendar: CalendarSystem = CalendarSystem.JALALI,
        recentLimit: Int = 5,
        today: LocalDate = todayLocalDate(),
    ): Flow<DashboardSummary> =
        combine(
            transactions.observeAll(),
            accounts.observeAll(),
            goalAllocations.observeAll(),
        ) { txs, accs, allocs ->
            val monthStart = AppCalendar.startOfMonth(today, calendar)
            val monthEnd = AppCalendar.endOfMonth(today, calendar)
            val scopedAccounts = if (accountId == null) {
                accs
            } else {
                accs.filter { it.id == accountId }
            }
            val scopedTxs = if (accountId == null) {
                txs
            } else {
                txs.filter { it.accountId == accountId }
            }
            val monthTxs = scopedTxs.filter { it.date in monthStart..monthEnd }
            val recent = monthTxs
                .sortedWith(
                    compareByDescending<Transaction> { it.date }
                        .thenByDescending { it.createdAt },
                )
                .take(recentLimit)
            val balance = if (accountId == null) {
                BalanceCalculator.totalBalance(accs, txs, allocs)
            } else {
                val account = scopedAccounts.firstOrNull()
                if (account == null) {
                    0L
                } else {
                    BalanceCalculator.accountBalance(account, txs, allocs)
                }
            }
            DashboardSummary(
                totalBalanceRial = balance,
                monthIncomeRial = BalanceCalculator.totalIncome(monthTxs),
                monthExpenseRial = BalanceCalculator.totalExpense(monthTxs),
                recentTransactions = recent,
                accounts = accs,
                selectedAccountId = accountId,
                monthLabel = AppCalendar.formatMonthYear(today, calendar),
            )
        }
}

class ObserveTransactionsUseCase(
    private val transactions: TransactionRepository,
) {
    operator fun invoke(
        query: String = "",
        typeFilter: TransactionType? = null,
    ): Flow<List<Transaction>> =
        transactions.observeAll().map { list ->
            list
                .asSequence()
                .filter { typeFilter == null || it.type == typeFilter }
                .filter {
                    query.isBlank() || it.title.contains(query.trim(), ignoreCase = true)
                }
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
                .toList()
        }
}

class SaveTransactionUseCase(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(draft: TransactionDraft): Transaction {
        val title = draft.title.trim()
        if (title.isEmpty()) {
            throw DomainException.Validation("عنوان را وارد کنید")
        }
        val amount = draft.amountRial
        if (amount == null || amount <= 0L) {
            throw DomainException.Validation("مبلغ باید بزرگ‌تر از صفر باشد")
        }
        val categoryId = draft.categoryId ?: throw DomainException.Validation("دسته‌بندی را انتخاب کنید")
        val accountId = draft.accountId ?: throw DomainException.Validation("حساب را انتخاب کنید")
        val dateDays = draft.dateEpochDays ?: throw DomainException.Validation("تاریخ را انتخاب کنید")

        val category = categories.getById(categoryId)
            ?: throw DomainException.NotFound("دسته‌بندی پیدا نشد")
        if (category.type != draft.type) {
            throw DomainException.Validation("دسته‌بندی با نوع تراکنش هم‌خوان نیست")
        }
        accounts.getById(accountId) ?: throw DomainException.NotFound("حساب پیدا نشد")

        val now = nowInstant()
        val existing = draft.id?.let { transactions.getById(it) }
        val entity = Transaction(
            id = existing?.id ?: IdGenerator.newId(),
            type = draft.type,
            amountRial = amount,
            title = title,
            categoryId = categoryId,
            accountId = accountId,
            date = LocalDate.fromEpochDays(dateDays.toInt()),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        transactions.upsert(entity)
        return entity
    }
}

class DeleteTransactionUseCase(
    private val transactions: TransactionRepository,
) {
    suspend operator fun invoke(id: String) {
        transactions.getById(id) ?: throw DomainException.NotFound("تراکنش پیدا نشد")
        transactions.delete(id)
    }
}

class ObserveCategoriesUseCase(
    private val categories: CategoryRepository,
) {
    operator fun invoke(type: TransactionType? = null): Flow<List<Category>> =
        categories.observeAll().map { list ->
            list
                .filter { type == null || it.type == type }
                .sortedBy { it.name }
        }
}

class SaveCategoryUseCase(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(
        id: String?,
        name: String,
        type: TransactionType,
        iconKey: String,
    ): Category {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw DomainException.Validation("نام دسته‌بندی را وارد کنید")
        }
        val entity = Category(
            id = id ?: IdGenerator.newId(),
            name = trimmed,
            type = type,
            iconKey = iconKey.ifBlank { defaultIcon(type) },
        )
        categories.upsert(entity)
        return entity
    }

    private fun defaultIcon(type: TransactionType): String =
        when (type) {
            TransactionType.INCOME -> "income"
            TransactionType.EXPENSE -> "expense"
        }
}

class DeleteCategoryUseCase(
    private val categories: CategoryRepository,
) {
    suspend operator fun invoke(id: String) {
        categories.getById(id) ?: throw DomainException.NotFound("دسته‌بندی پیدا نشد")
        val count = categories.countTransactions(id)
        if (count > 0) {
            throw DomainException.Conflict(
                "این دسته‌بندی در $count تراکنش استفاده شده. ابتدا تراکنش‌ها را اصلاح کنید.",
            )
        }
        categories.delete(id)
    }
}

class ObserveAccountsUseCase(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val goalAllocations: GoalAllocationRepository,
) {
    operator fun invoke(): Flow<List<AccountBalance>> =
        combine(
            accounts.observeAll(),
            transactions.observeAll(),
            goalAllocations.observeAll(),
        ) { accs, txs, allocs ->
            BalanceCalculator.accountBalances(accs, txs, allocs)
                .sortedBy { it.account.name }
        }
}

class SaveAccountUseCase(
    private val accounts: AccountRepository,
) {
    suspend operator fun invoke(
        id: String?,
        name: String,
        initialBalanceRial: Long,
    ): Account {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            throw DomainException.Validation("نام حساب را وارد کنید")
        }
        val entity = Account(
            id = id ?: IdGenerator.newId(),
            name = trimmed,
            initialBalanceRial = initialBalanceRial,
        )
        accounts.upsert(entity)
        return entity
    }
}

class DeleteAccountUseCase(
    private val accounts: AccountRepository,
    private val transactions: TransactionRepository,
    private val goalAllocations: GoalAllocationRepository,
) {
    suspend fun relatedTransactionCount(id: String): Int =
        accounts.countTransactions(id)

    suspend fun relatedGoalAllocationCount(id: String): Int =
        goalAllocations.countByAccountId(id)

    /** Deletes the account, its transactions, and goal allocations reserved from it. */
    suspend operator fun invoke(id: String) {
        accounts.getById(id) ?: throw DomainException.NotFound("حساب پیدا نشد")
        transactions.deleteByAccountId(id)
        goalAllocations.deleteByAccountId(id)
        accounts.delete(id)
    }
}
