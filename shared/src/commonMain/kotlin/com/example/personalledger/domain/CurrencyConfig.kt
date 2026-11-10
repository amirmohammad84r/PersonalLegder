package com.example.personalledger.domain

/**
 * Single source of truth for currency.
 * Amounts are stored as Long in ریال (1 storage unit = 1 ریال).
 */
object CurrencyConfig {
    const val code: String = "IRR"
    const val displayName: String = "ریال"
    const val scale: Int = 0

    /** Ledger JSON schema version that stores amounts in ریال. */
    const val ledgerSchemaVersion: Int = 2

    /** Previous schema stored amounts in تومان. */
    const val legacyTomanSchemaVersion: Int = 1
}
