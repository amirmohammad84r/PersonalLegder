package com.example.personalledger.domain.sms

import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.text.PersianTextNormalizer

data class SuggestedAccountFromSms(
    val account: Account,
    val matchedPhrase: String,
    val reason: String,
)

/**
 * Suggests an account by matching account [Account.name] (and optional bank-name aliases)
 * against the SMS body text.
 */
class SmsAccountMatcher {
    fun suggest(smsText: String, accounts: List<Account>): SuggestedAccountFromSms? {
        val haystack = PersianTextNormalizer.normalize(smsText)
        if (haystack.isBlank() || accounts.isEmpty()) return null

        for (account in accounts) {
            val needles = needlesFor(account.name)
            val hit = needles.firstOrNull { needle ->
                needle.length >= MIN_NEEDLE_LENGTH && haystack.contains(needle)
            }
            if (hit != null) {
                return SuggestedAccountFromSms(
                    account = account,
                    matchedPhrase = hit,
                    reason = "نام حساب «${account.name}» در متن پیامک یافت شد",
                )
            }
        }
        return null
    }

    private fun needlesFor(accountName: String): List<String> {
        val normalizedName = PersianTextNormalizer.normalize(accountName)
        if (normalizedName.isBlank()) return emptyList()

        val needles = linkedSetOf(normalizedName)
        for (group in BANK_TEXT_ALIASES) {
            val normalizedGroup = group.map { PersianTextNormalizer.normalize(it) }
                .filter { it.length >= MIN_NEEDLE_LENGTH }
            val belongs = normalizedGroup.any { alias ->
                alias == normalizedName ||
                    normalizedName.contains(alias) ||
                    alias.contains(normalizedName)
            }
            if (belongs) {
                needles.addAll(normalizedGroup)
            }
        }
        return needles.sortedByDescending { it.length }
    }

    companion object {
        private const val MIN_NEEDLE_LENGTH = 2

        /**
         * Extensible text aliases seen in real/sample bank SMS bodies.
         * Used only to widen matching of an existing account [name] against SMS text.
         */
        private val BANK_TEXT_ALIASES: List<List<String>> = listOf(
            listOf("بانک ملی ایران", "بانک ملی", "ملی"),
            listOf("بانک ملت", "ملت"),
            listOf("بانک تجارت", "تجارت"),
            listOf("بانک سامان", "سامان"),
            listOf("بانک صادرات", "صادرات"),
            listOf("بانک سپه", "سپه"),
            listOf("بانک پاسارگاد", "پاسارگاد"),
            listOf("بلوبانک", "بلو بانک", "bluebank"),
        )
    }
}
