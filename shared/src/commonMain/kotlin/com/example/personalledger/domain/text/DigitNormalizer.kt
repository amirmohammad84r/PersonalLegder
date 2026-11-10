package com.example.personalledger.domain.text

object DigitNormalizer {
    fun toLatinDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch) {
                    '۰', '٠' -> '0'
                    '۱', '١' -> '1'
                    '۲', '٢' -> '2'
                    '۳', '٣' -> '3'
                    '۴', '٤' -> '4'
                    '۵', '٥' -> '5'
                    '۶', '٦' -> '6'
                    '۷', '٧' -> '7'
                    '۸', '٨' -> '8'
                    '۹', '٩' -> '9'
                    else -> ch
                },
            )
        }
    }

    /** Keep digits only after Latin normalization. */
    fun digitsOnly(input: String): String =
        toLatinDigits(input).filter { it.isDigit() }
}
