package com.example.personalledger.domain.ai.tools

/**
 * Checked Long arithmetic for ریال aggregates.
 * Prefer failing closed over silent overflow into wrong totals.
 */
object SafeMoney {
    fun add(a: Long, b: Long): Long {
        val sum = a + b
        if (((a xor sum) and (b xor sum)) < 0L) {
            throw FinancialToolValidationException("Overflow while aggregating amounts in ریال.")
        }
        return sum
    }

    fun subtract(a: Long, b: Long): Long {
        val diff = a - b
        if (((a xor b) and (a xor diff)) < 0L) {
            throw FinancialToolValidationException("Overflow while subtracting amounts in ریال.")
        }
        return diff
    }

    fun averageFloor(total: Long, denominator: Int): Long {
        require(denominator > 0) { "Denominator must be positive." }
        require(total >= 0L) { "Average floor expects non-negative total." }
        return total / denominator.toLong()
    }
}

class FinancialToolValidationException(message: String) : IllegalArgumentException(message)

class FinancialToolUnknownException(message: String) : IllegalArgumentException(message)

class FinancialToolBudgetExceededException(message: String) : IllegalStateException(message)
