package com.example.personalledger

import com.example.personalledger.data.local.AccountDto
import com.example.personalledger.data.local.LedgerSnapshotDto
import com.example.personalledger.data.local.TransactionDto
import com.example.personalledger.data.local.migrateToCurrent
import com.example.personalledger.domain.CurrencyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LedgerMigrationTest {
    @Test
    fun v1Toman_multipliesByTenToRial() {
        val v1 = LedgerSnapshotDto(
            version = 1,
            currencyCode = "IRR",
            accounts = listOf(
                AccountDto(
                    id = "a1",
                    name = "نقدی",
                    initialBalanceRial = 0,
                    legacyInitialBalanceToman = 10_000,
                ),
            ),
            transactions = listOf(
                TransactionDto(
                    id = "t1",
                    type = "EXPENSE",
                    amountRial = 0,
                    legacyAmountToman = 2_500,
                    title = "خرید",
                    categoryId = "c1",
                    accountId = "a1",
                    dateEpochDays = 20_000,
                    createdAtEpochMs = 0,
                    updatedAtEpochMs = 0,
                ),
            ),
        )
        val v2 = v1.migrateToCurrent()
        assertEquals(CurrencyConfig.ledgerSchemaVersion, v2.version)
        assertEquals(CurrencyConfig.code, v2.currencyCode)
        assertEquals(100_000L, v2.accounts.single().initialBalanceRial)
        assertNull(v2.accounts.single().legacyInitialBalanceToman)
        assertEquals(25_000L, v2.transactions.single().amountRial)
        assertNull(v2.transactions.single().legacyAmountToman)
    }

    @Test
    fun v2AlreadyCurrent_isIdempotent() {
        val v2 = LedgerSnapshotDto(
            version = 2,
            currencyCode = "IRR",
            accounts = listOf(
                AccountDto("a1", "نقدی", initialBalanceRial = 50_000),
            ),
        )
        val again = v2.migrateToCurrent()
        assertEquals(50_000L, again.accounts.single().initialBalanceRial)
        assertEquals(2, again.version)
        assertEquals(emptyList(), again.goals)
        assertEquals(emptyList(), again.goalAllocations)
    }

    @Test
    fun v1MissingLegacyAmount_refusesGuess() {
        val broken = LedgerSnapshotDto(
            version = 1,
            currencyCode = "IRR",
            transactions = listOf(
                TransactionDto(
                    id = "t1",
                    type = "INCOME",
                    amountRial = 100,
                    legacyAmountToman = null,
                    title = "x",
                    categoryId = "c",
                    accountId = "a",
                    dateEpochDays = 1,
                    createdAtEpochMs = 0,
                    updatedAtEpochMs = 0,
                ),
            ),
        )
        assertFailsWith<IllegalStateException> { broken.migrateToCurrent() }
    }

    @Test
    fun unsupportedVersion_refusesSilentConversion() {
        val weird = LedgerSnapshotDto(version = 99, currencyCode = "USD")
        assertFailsWith<IllegalArgumentException> { weird.migrateToCurrent() }
    }
}
