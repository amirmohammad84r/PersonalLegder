package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.model.Transaction
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/**
 * Lightweight duplicate hint — compares saved transactions only; never stores raw SMS.
 */
class FindSimilarTransactionsUseCase(
    private val transactions: TransactionRepository,
) {
    suspend operator fun invoke(
        type: TransactionType,
        amountRial: Long,
        accountId: String?,
        date: LocalDate,
    ): List<Transaction> {
        val all = transactions.observeAll().first()
        return all.filter { tx ->
            tx.type == type &&
                tx.amountRial == amountRial &&
                (accountId == null || tx.accountId == accountId) &&
                abs(tx.date.toEpochDays() - date.toEpochDays()) <= 1
        }
    }
}
