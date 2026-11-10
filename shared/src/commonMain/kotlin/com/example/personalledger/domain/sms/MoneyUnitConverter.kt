package com.example.personalledger.domain.sms

import com.example.personalledger.domain.CurrencyConfig

/**
 * Central conversion into the ledger storage unit (ریال).
 * Call once at parse/normalize time — never convert twice.
 */
object MoneyUnitConverter {
    fun toStorageRial(amount: Long, unit: DetectedMoneyUnit): ConversionResult {
        return when (unit) {
            DetectedMoneyUnit.RIAL -> ConversionResult(
                amountRial = amount,
                unit = unit,
                ambiguous = false,
            )
            DetectedMoneyUnit.TOMAN -> ConversionResult(
                amountRial = amount * 10L,
                unit = unit,
                ambiguous = false,
            )
            DetectedMoneyUnit.UNKNOWN -> ConversionResult(
                amountRial = amount,
                unit = unit,
                ambiguous = true,
                note = "واحد پول در متن مشخص نیست؛ مقدار به‌عنوان ریال پیشنهاد شده و نیاز به تأیید دارد",
            )
        }
    }

    data class ConversionResult(
        val amountRial: Long,
        val unit: DetectedMoneyUnit,
        val ambiguous: Boolean,
        val note: String? = null,
    ) {
        init {
            require(CurrencyConfig.code == "IRR" && CurrencyConfig.scale == 0) {
                "MoneyUnitConverter assumes ریال with scale 0"
            }
        }
    }
}
