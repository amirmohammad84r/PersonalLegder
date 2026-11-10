package com.example.personalledger

import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.sms.DetectedMoneyUnit
import com.example.personalledger.domain.sms.FieldConfidence
import com.example.personalledger.domain.sms.HeuristicBankSmsParser
import com.example.personalledger.domain.sms.MoneyUnitConverter
import com.example.personalledger.domain.sms.SmsMessageKind
import com.example.personalledger.domain.text.DigitNormalizer
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankSmsParserTest {
    private val parser = HeuristicBankSmsParser()

    @Test
    fun digitNormalizer_supportsPersianAndArabic() {
        assertEquals("1234567890", DigitNormalizer.toLatinDigits("۱۲۳۴۵۶۷۸۹۰"))
        assertEquals("1234", DigitNormalizer.toLatinDigits("١٢٣٤"))
    }

    @Test
    fun moneyConverter_rialStaysRial() {
        val result = MoneyUnitConverter.toStorageRial(1_500_000, DetectedMoneyUnit.RIAL)
        assertEquals(1_500_000, result.amountRial)
        assertEquals(false, result.ambiguous)
    }

    @Test
    fun moneyConverter_tomanToRialOnce() {
        val result = MoneyUnitConverter.toStorageRial(100, DetectedMoneyUnit.TOMAN)
        assertEquals(1_000, result.amountRial)
        assertEquals(false, result.ambiguous)
    }

    @Test
    fun moneyConverter_unknownKeepsValueButAmbiguous() {
        val result = MoneyUnitConverter.toStorageRial(50_000, DetectedMoneyUnit.UNKNOWN)
        assertEquals(50_000, result.amountRial)
        assertTrue(result.ambiguous)
    }

    @Test
    fun tejaratLikeDeposit_extractsIncomeAmountInRial() {
        val text =
            "حساب: ****1234 / واریز: 1,500,000 ریال / از طریق: پایا / مانده: 12,000,000 ریال / 1403/07/01 14:30"
        val parsed = parser.parse(text)
        assertEquals(SmsMessageKind.TRANSACTION, parsed.messageKind)
        assertEquals(TransactionType.INCOME, parsed.type.value)
        assertEquals(1_500_000L, parsed.amountRial.value)
        assertEquals(DetectedMoneyUnit.RIAL, parsed.detectedUnit.value)
        assertEquals(12_000_000L, parsed.balanceAfterRial.value)
        assertEquals("پایا", parsed.titleSuggestion.value)
        assertEquals(LocalDate(2024, 9, 22), parsed.date.value)
        assertTrue(parsed.amountRial.confidence != FieldConfidence.MISSING)
    }

    @Test
    fun mellatLikeWithdraw_extractsExpenseAndIgnoresDocumentId() {
        val text =
            "برداشت از حساب ****5678 / مبلغ: 250000 ریال / موجودی: 980000 ریال / شماره سند: 99887766 / 1403/07/02 09:15"
        val parsed = parser.parse(text)
        assertEquals(SmsMessageKind.TRANSACTION, parsed.messageKind)
        assertEquals(TransactionType.EXPENSE, parsed.type.value)
        assertEquals(250_000L, parsed.amountRial.value)
        assertEquals(980_000L, parsed.balanceAfterRial.value)
        assertTrue(parsed.amountRial.value != 99_887_766L)
    }

    @Test
    fun colloquialBluebankLike_parsesTomanWithdrawAsRial() {
        val text = "۱۰۰ تومن از حساب شما پرید"
        val parsed = parser.parse(text)
        assertEquals(SmsMessageKind.TRANSACTION, parsed.messageKind)
        assertEquals(TransactionType.EXPENSE, parsed.type.value)
        assertEquals(1_000L, parsed.amountRial.value)
        assertEquals(DetectedMoneyUnit.TOMAN, parsed.detectedUnit.value)
    }

    @Test
    fun gregorianDateInSms_isParsedAndStoredAsLocalDate() {
        val text = "واریز: 50000 ریال / 2024-09-22"
        val parsed = parser.parse(text)
        assertEquals(SmsMessageKind.TRANSACTION, parsed.messageKind)
        assertEquals(LocalDate(2024, 9, 22), parsed.date.value)
    }

    @Test
    fun otpMessage_isRejected() {
        val parsed = parser.parse("رمز پویا: 123456 تا 02:00 معتبر است")
        assertEquals(SmsMessageKind.OTP, parsed.messageKind)
        assertNull(parsed.amountRial.value)
    }

    @Test
    fun promoMessage_isRejected() {
        val parsed = parser.parse("جایزه ویژه قرعه‌کشی؛ کد تخفیف ABC")
        assertEquals(SmsMessageKind.PROMOTIONAL, parsed.messageKind)
    }

    @Test
    fun failedTransaction_isRejected() {
        val parsed = parser.parse("برداشت ناموفق مبلغ 50000 ریال")
        assertEquals(SmsMessageKind.FAILED_OR_REVERSAL, parsed.messageKind)
    }
}
