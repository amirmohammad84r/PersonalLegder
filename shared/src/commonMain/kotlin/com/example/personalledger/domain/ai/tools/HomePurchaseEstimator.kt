package com.example.personalledger.domain.ai.tools

import com.example.personalledger.domain.ai.search.PropertyListingCurrency
import com.example.personalledger.domain.ai.search.PropertyPriceListing
import kotlinx.serialization.Serializable

enum class SurplusAssumption {
    /** Use 100% of average monthly income−expense balance (optimistic scenario assumption). */
    ALL_MONTHLY_BALANCE,
    /** Use 50% of average monthly balance (conservative scenario assumption). */
    HALF_MONTHLY_BALANCE,
}

/**
 * Deterministic home-purchase timeline scenarios. Never invents market prices.
 */
object HomePurchaseEstimator {
    const val TOMAN_PER_RIAL_FACTOR = 10L

    fun toRial(amount: Long, currency: PropertyListingCurrency): Long {
        if (amount < 0L) {
            throw FinancialToolValidationException("Price amount must be non-negative.")
        }
        return when (currency) {
            PropertyListingCurrency.RIAL -> amount
            PropertyListingCurrency.TOMAN -> {
                if (amount > Long.MAX_VALUE / TOMAN_PER_RIAL_FACTOR) {
                    throw FinancialToolValidationException("Toman→ریال conversion overflow.")
                }
                amount * TOMAN_PER_RIAL_FACTOR
            }
        }
    }

    fun priceRangeRial(listings: List<PropertyPriceListing>): PriceRangeRial? {
        val priced = listings.mapNotNull { listing ->
            val amount = listing.priceAmount ?: return@mapNotNull null
            val currency = listing.priceCurrency ?: return@mapNotNull null
            toRial(amount, currency)
        }
        if (priced.isEmpty()) return null
        return PriceRangeRial(minRial = priced.minOrNull()!!, maxRial = priced.maxOrNull()!!)
    }

    fun estimate(
        averageMonthlyIncomeRial: Long,
        averageMonthlyExpenseRial: Long,
        priceMinRial: Long,
        priceMaxRial: Long,
        downPaymentRial: Long?,
        currentSavingsRial: Long?,
        assumptions: List<SurplusAssumption>,
    ): HomePurchaseEstimateResult {
        if (priceMinRial < 0L || priceMaxRial < priceMinRial) {
            throw FinancialToolValidationException("Invalid price range in ریال.")
        }
        val down = downPaymentRial?.takeIf { it >= 0L }
        val savings = currentSavingsRial?.takeIf { it >= 0L } ?: 0L
        val monthlyBalance = SafeMoney.subtract(averageMonthlyIncomeRial, averageMonthlyExpenseRial)
        val scenarios = assumptions.map { assumption ->
            val surplus = when (assumption) {
                SurplusAssumption.ALL_MONTHLY_BALANCE -> monthlyBalance
                SurplusAssumption.HALF_MONTHLY_BALANCE ->
                    if (monthlyBalance <= 0L) 0L else monthlyBalance / 2L
            }
            val targetMin = remainingTarget(priceMinRial, down, savings)
            val targetMax = remainingTarget(priceMaxRial, down, savings)
            ScenarioEstimate(
                assumption = assumption.name,
                assumptionNote = assumptionNote(assumption),
                assumedMonthlySurplusRial = surplus,
                monthsToMinPrice = monthsNeeded(targetMin, surplus),
                monthsToMaxPrice = monthsNeeded(targetMax, surplus),
                remainingMinRial = targetMin,
                remainingMaxRial = targetMax,
            )
        }
        return HomePurchaseEstimateResult(
            averageMonthlyIncomeRial = averageMonthlyIncomeRial,
            averageMonthlyExpenseRial = averageMonthlyExpenseRial,
            averageMonthlyBalanceRial = monthlyBalance,
            balanceLabel = "تراز درآمد و هزینه (پس‌انداز اثبات‌شده نیست)",
            priceMinRial = priceMinRial,
            priceMaxRial = priceMaxRial,
            downPaymentRial = down,
            currentSavingsRial = currentSavingsRial,
            scenarios = scenarios,
            fixedPriceAssumption = true,
            disclaimer = "تخمین سناریویی با فرض قیمت ثابت آگهی است؛ قیمت آگهی لزوماً قیمت معامله نیست و وام/تورم لحاظ نشده مگر جداگانه داده شود.",
        )
    }

    private fun remainingTarget(priceRial: Long, down: Long?, savings: Long): Long {
        val goal = down ?: priceRial
        return (goal - savings).coerceAtLeast(0L)
    }

    private fun monthsNeeded(remainingRial: Long, monthlySurplusRial: Long): Int? {
        if (remainingRial <= 0L) return 0
        if (monthlySurplusRial <= 0L) return null
        val months = (remainingRial + monthlySurplusRial - 1L) / monthlySurplusRial
        return months.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun assumptionNote(assumption: SurplusAssumption): String = when (assumption) {
        SurplusAssumption.ALL_MONTHLY_BALANCE ->
            "فرض سناریو: کل تراز ماهانه پس‌انداز شود (دادهٔ واقعی پس‌انداز نیست)."
        SurplusAssumption.HALF_MONTHLY_BALANCE ->
            "فرض سناریو: نیمی از تراز ماهانه پس‌انداز شود (فرض محافظه‌کارانه)."
    }
}

data class PriceRangeRial(val minRial: Long, val maxRial: Long)

@Serializable
data class ScenarioEstimate(
    val assumption: String,
    val assumptionNote: String,
    val assumedMonthlySurplusRial: Long,
    val monthsToMinPrice: Int?,
    val monthsToMaxPrice: Int?,
    val remainingMinRial: Long,
    val remainingMaxRial: Long,
)

@Serializable
data class HomePurchaseEstimateResult(
    val averageMonthlyIncomeRial: Long,
    val averageMonthlyExpenseRial: Long,
    val averageMonthlyBalanceRial: Long,
    val balanceLabel: String,
    val priceMinRial: Long,
    val priceMaxRial: Long,
    val downPaymentRial: Long?,
    val currentSavingsRial: Long?,
    val scenarios: List<ScenarioEstimate>,
    val fixedPriceAssumption: Boolean,
    val disclaimer: String,
    val currencyUnit: String = "ریال",
)
