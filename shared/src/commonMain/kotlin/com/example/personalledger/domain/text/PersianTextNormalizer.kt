package com.example.personalledger.domain.text

/**
 * Lightweight Persian text normalization for substring matching (SMS ↔ account name).
 */
object PersianTextNormalizer {
    fun normalize(input: String): String {
        val withLatinDigits = DigitNormalizer.toLatinDigits(input)
        val mapped = buildString(withLatinDigits.length) {
            for (ch in withLatinDigits) {
                append(
                    when (ch) {
                        'ي', 'ى' -> 'ی'
                        'ك' -> 'ک'
                        '\u200c', '\u200d', '\u00a0' -> ' ' // ZWNJ, ZWJ, NBSP
                        else -> ch
                    },
                )
            }
        }
        return mapped
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
