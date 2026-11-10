package com.example.personalledger

import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.sms.SmsAccountMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsAccountMatcherTest {
    private val matcher = SmsAccountMatcher()

    @Test
    fun matchesExactAccountNameInSmsBody() {
        val accounts = listOf(
            Account("1", "نقدی", 0),
            Account("2", "بانک ملت", 0),
        )
        val sms = "برداشت از حساب بانک ملت مبلغ 250000 ریال"
        val suggested = matcher.suggest(sms, accounts)
        assertEquals("2", suggested?.account?.id)
    }

    @Test
    fun arabicYeAndKeDoNotBreakMatch() {
        val accounts = listOf(
            Account("1", "بانك ملي", 0),
        )
        val sms = "بانک ملی ایران واریز: 1000 ریال"
        val suggested = matcher.suggest(sms, accounts)
        assertEquals("1", suggested?.account?.id)
    }

    @Test
    fun halfSpaceAndExtraSpacesNormalized() {
        val accounts = listOf(
            Account("1", "بانک\u200cملت", 0),
        )
        val sms = "بانک   ملت برداشت انجام شد"
        assertEquals("1", matcher.suggest(sms, accounts)?.account?.id)
    }

    @Test
    fun aliasInSmsMatchesAccountShortName() {
        val accounts = listOf(
            Account("1", "ملی", 0),
        )
        val sms = "بانک ملی ایران / واریز: 50000 ریال"
        assertEquals("1", matcher.suggest(sms, accounts)?.account?.id)
    }

    @Test
    fun firstAccountInListWinsWhenSeveralMatch() {
        val accounts = listOf(
            Account("a", "ملت", 0),
            Account("b", "بانک ملت", 0),
        )
        val sms = "پیامک بانک ملت برای حساب ملت"
        assertEquals("a", matcher.suggest(sms, accounts)?.account?.id)
    }

    @Test
    fun noAccountNameInText_leavesUnselected() {
        val accounts = listOf(
            Account("1", "کارت شخصی", 0),
            Account("2", "نقدی", 0),
        )
        val sms = "واریز: 1,500,000 ریال / مانده: 12,000,000 ریال"
        assertNull(matcher.suggest(sms, accounts))
    }

    @Test
    fun doesNotUseNumericSenderLikeTokensAsAccountMatch() {
        val accounts = listOf(
            Account("1", "کارت بانکی", 0),
        )
        val sms = "30003305 واریز 1000 ریال"
        assertNull(matcher.suggest(sms, accounts))
    }
}
