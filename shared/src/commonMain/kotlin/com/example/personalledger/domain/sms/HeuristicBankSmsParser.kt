package com.example.personalledger.domain.sms

import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.text.DigitNormalizer
import com.example.personalledger.domain.calendar.JalaliDateConverter
import kotlinx.datetime.LocalDate

/**
 * Local heuristic parser for common Iranian bank SMS patterns.
 * Not bank-official; based on public descriptions and anonymized samples.
 * Prefer field labels over raw number order; never treat OTP/promo as transactions.
 */
class HeuristicBankSmsParser : SmsTransactionParser {

    override fun parse(rawText: String): ParsedSmsTransaction {
        val text = DigitNormalizer.toLatinDigits(rawText).trim()
        if (text.isBlank()) {
            return emptyResult(
                kind = SmsMessageKind.UNSUPPORTED,
                reason = "متن پیامک خالی است",
            )
        }

        val kind = classifyKind(text)
        if (kind != SmsMessageKind.TRANSACTION) {
            return emptyResult(
                kind = kind,
                reason = when (kind) {
                    SmsMessageKind.OTP -> "این متن شبیه رمز یک‌بارمصرف است، نه تراکنش"
                    SmsMessageKind.PROMOTIONAL -> "این متن تبلیغاتی یا اطلاع‌رسانی غیرمالی به نظر می‌رسد"
                    SmsMessageKind.FAILED_OR_REVERSAL -> "تراکنش ناموفق، برگشت یا اصلاحیه تشخیص داده شد"
                    else -> "این قالب فعلاً پشتیبانی نمی‌شود"
                },
            )
        }

        val type = detectType(text)
        val unit = detectUnit(text)
        val amount = detectAmount(text, unit)
        val balance = detectBalance(text, unit)
        val date = detectDate(text)
        val title = suggestTitle(text, type.value)
        val accountHint = detectAccountHint(text)

        return ParsedSmsTransaction(
            messageKind = SmsMessageKind.TRANSACTION,
            type = type,
            amountRial = amount,
            detectedUnit = unit,
            titleSuggestion = title,
            date = date,
            balanceAfterRial = balance,
            accountHint = accountHint,
        )
    }

    private fun classifyKind(text: String): SmsMessageKind {
        val lower = text.lowercase()
        val otpHints = listOf(
            "رمز پویا", "رمز یکبار", "رمز یک‌بار", "کد تایید", "کد تأيید",
            "otp", "password", "cvv2", "رمز خرید",
        )
        if (otpHints.any { lower.contains(it) } && !hasMoneyCue(text)) {
            return SmsMessageKind.OTP
        }
        val failHints = listOf("ناموفق", "لغو شد", "رد شد", "برگشت وجه", "برگشت خورد", "اصلاحیه")
        if (failHints.any { text.contains(it) }) {
            return SmsMessageKind.FAILED_OR_REVERSAL
        }
        val promoHints = listOf("جایزه", "قرعه‌کشی", "تخفیف ویژه", "تبلیغات", "کد تخفیف")
        if (promoHints.any { text.contains(it) } && !hasMoneyCue(text)) {
            return SmsMessageKind.PROMOTIONAL
        }
        if (!hasMoneyCue(text) && !hasTypeCue(text)) {
            return SmsMessageKind.UNSUPPORTED
        }
        return SmsMessageKind.TRANSACTION
    }

    private fun hasMoneyCue(text: String): Boolean {
        val unit = text.contains("ریال") || text.contains("تومان") || text.contains("تومن")
        val labeled = MONEY_LABEL.containsMatchIn(text) || BALANCE_LABEL.containsMatchIn(text)
        val signed = Regex("""[+\-−]\s*[\d٬,.]{3,}""").containsMatchIn(text)
        return unit || labeled || signed || colloquialAmount(text) != null
    }

    private fun hasTypeCue(text: String): Boolean =
        depositScore(text) > 0 || withdrawScore(text) > 0

    private fun detectType(text: String): ConfidentValue<TransactionType?> {
        val deposit = depositScore(text)
        val withdraw = withdrawScore(text)
        return when {
            deposit > withdraw && deposit >= 2 ->
                ConfidentValue(TransactionType.INCOME, FieldConfidence.DETECTED)
            withdraw > deposit && withdraw >= 2 ->
                ConfidentValue(TransactionType.EXPENSE, FieldConfidence.DETECTED)
            deposit > withdraw && deposit >= 1 ->
                ConfidentValue(TransactionType.INCOME, FieldConfidence.NEEDS_REVIEW)
            withdraw > deposit && withdraw >= 1 ->
                ConfidentValue(TransactionType.EXPENSE, FieldConfidence.NEEDS_REVIEW)
            deposit == withdraw && deposit > 0 ->
                ConfidentValue(null, FieldConfidence.UNCERTAIN)
            else ->
                ConfidentValue(null, FieldConfidence.MISSING)
        }
    }

    private fun depositScore(text: String): Int {
        var score = 0
        val strong = listOf("واریز", "واریزی", "افزوده شد", "به حساب نشست", "دریافت وجه")
        val soft = listOf("دریافت", "افزایش موجودی", "+")
        strong.forEach { if (text.contains(it)) score += 2 }
        soft.forEach { if (text.contains(it)) score += 1 }
        if (Regex("""واریز\s*[:：]""").containsMatchIn(text)) score += 2
        if (Regex("""[+\+]\s*[\d٬,]""").containsMatchIn(text)) score += 2
        // "برگشت وجه" already classified as failed; don't boost deposit on "برگشت" alone
        return score
    }

    private fun withdrawScore(text: String): Int {
        var score = 0
        val strong = listOf(
            "برداشت", "کسر", "خرید", "پرداخت", "خارج شد", "پرید",
            "برداشت از حساب", "کسر از حساب",
        )
        val soft = listOf("انتقال", "کارمزد")
        strong.forEach { if (text.contains(it)) score += 2 }
        soft.forEach { if (text.contains(it)) score += 1 }
        if (Regex("""برداشت\s*[:：]""").containsMatchIn(text)) score += 2
        if (Regex("""[\-−]\s*[\d٬,]""").containsMatchIn(text)) score += 2
        if (text.contains("پرید")) score += 2
        return score
    }

    private fun detectUnit(text: String): ConfidentValue<DetectedMoneyUnit> {
        val hasRial = text.contains("ریال")
        val hasToman = text.contains("تومان") || text.contains("تومن")
        return when {
            hasRial && !hasToman -> ConfidentValue(DetectedMoneyUnit.RIAL, FieldConfidence.DETECTED)
            hasToman && !hasRial -> ConfidentValue(DetectedMoneyUnit.TOMAN, FieldConfidence.DETECTED)
            hasRial && hasToman -> ConfidentValue(DetectedMoneyUnit.UNKNOWN, FieldConfidence.UNCERTAIN)
            else -> ConfidentValue(DetectedMoneyUnit.UNKNOWN, FieldConfidence.MISSING)
        }
    }

    private fun detectAmount(
        text: String,
        unit: ConfidentValue<DetectedMoneyUnit>,
    ): ConfidentValue<Long?> {
        colloquialAmount(text)?.let { (raw, unitFromMatch) ->
            val u = unitFromMatch
            val converted = MoneyUnitConverter.toStorageRial(raw, u)
            val conf = when {
                converted.ambiguous || u == DetectedMoneyUnit.UNKNOWN -> FieldConfidence.NEEDS_REVIEW
                else -> FieldConfidence.DETECTED
            }
            return ConfidentValue(converted.amountRial, conf)
        }

        val labeled = extractTransactionAmount(text)
        if (labeled != null) {
            val u = unit.value ?: DetectedMoneyUnit.UNKNOWN
            val converted = MoneyUnitConverter.toStorageRial(labeled, u)
            val conf = when {
                converted.ambiguous || u == DetectedMoneyUnit.UNKNOWN -> FieldConfidence.NEEDS_REVIEW
                unit.confidence == FieldConfidence.DETECTED -> FieldConfidence.DETECTED
                else -> FieldConfidence.NEEDS_REVIEW
            }
            return ConfidentValue(converted.amountRial, conf)
        }

        // Signed amount near +/- often is the transaction
        val signed = Regex("""[+\-−]\s*([\d٬,.]+)""").findAll(text)
            .mapNotNull { parseGroupedNumber(it.groupValues[1]) }
            .firstOrNull()
        if (signed != null) {
            val u = unit.value ?: DetectedMoneyUnit.UNKNOWN
            val converted = MoneyUnitConverter.toStorageRial(signed, u)
            return ConfidentValue(
                converted.amountRial,
                if (converted.ambiguous) FieldConfidence.NEEDS_REVIEW else FieldConfidence.NEEDS_REVIEW,
            )
        }

        return ConfidentValue(null, FieldConfidence.MISSING)
    }

    private fun detectBalance(
        text: String,
        unit: ConfidentValue<DetectedMoneyUnit>,
    ): ConfidentValue<Long?> {
        val labeled = extractLabeledAmount(text, BALANCE_LABEL) ?: return ConfidentValue(null, FieldConfidence.MISSING)
        val u = unit.value ?: DetectedMoneyUnit.UNKNOWN
        val converted = MoneyUnitConverter.toStorageRial(labeled, u)
        val conf = when {
            converted.ambiguous || u == DetectedMoneyUnit.UNKNOWN -> FieldConfidence.NEEDS_REVIEW
            else -> FieldConfidence.DETECTED
        }
        return ConfidentValue(converted.amountRial, conf)
    }

    private fun extractTransactionAmount(text: String): Long? {
        // Prefer explicit «مبلغ» so account numbers after «برداشت از حساب» are not taken.
        extractLabeledAmount(text, Regex("""مبلغ\s*[:：]?"""))?.let { return it }
        extractLabeledAmount(text, Regex("""(?:واریز|برداشت)\s*[:：]\s*"""))?.let { return it }
        // «واریز 1,000,000 ریال» without colon
        Regex("""(?:واریز|برداشت)\s+([+\-−]?[\d٬,.]+)\s*(?:ریال|تومان|تومن)?""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { parseGroupedNumber(it) }
            ?.let { return it }
        return null
    }

    private fun extractLabeledAmount(text: String, label: Regex): Long? {
        val match = label.find(text) ?: return null
        val after = text.substring(match.range.last + 1).take(48)
        val number = Regex("""[+\-−]?\s*([\d٬,.]+)""").find(after)?.groupValues?.getOrNull(1)
            ?: return null
        return parseGroupedNumber(number)
    }

    private fun colloquialAmount(text: String): Pair<Long, DetectedMoneyUnit>? {
        // e.g. «۱۰۰ تومن از حساب شما پرید»
        val m = Regex(
            """([\d٬,.]+)\s*(تومن|تومان|ریال)\s*.{0,20}(پرید|برداشت|کسر|خارج)""",
        ).find(text) ?: return null
        val amount = parseGroupedNumber(m.groupValues[1]) ?: return null
        val unitWord = m.groupValues[2]
        val unit = when (unitWord) {
            "ریال" -> DetectedMoneyUnit.RIAL
            else -> DetectedMoneyUnit.TOMAN
        }
        return amount to unit
    }

    private fun parseGroupedNumber(raw: String): Long? {
        val cleaned = raw
            .replace("٬", "")
            .replace(",", "")
            .replace(" ", "")
            .replace(".", "")
            .trim()
        if (cleaned.isEmpty() || cleaned.length > 15) return null
        // Heuristic: very long digit strings are IDs, not amounts
        if (cleaned.length >= 12 && !raw.contains("٬") && !raw.contains(",")) return null
        return cleaned.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun detectDate(text: String): ConfidentValue<LocalDate?> {
        // Jalali yyyy/mm/dd or yy/mm/dd
        val jalali = Regex("""(\d{2,4})[/.](\d{1,2})[/.](\d{1,2})(?:\s+(\d{1,2}):(\d{2}))?""")
            .findAll(text)
            .mapNotNull { m ->
                val yRaw = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val day = m.groupValues[3].toInt()
                val y = if (yRaw < 100) 1400 + yRaw else yRaw
                if (y in 1300..1499) JalaliDateConverter.toGregorian(y, month, day) else null
            }
            .firstOrNull()
        if (jalali != null) {
            return ConfidentValue(jalali, FieldConfidence.DETECTED)
        }

        val gregorian = Regex("""(20\d{2})[/-](\d{1,2})[/-](\d{1,2})""")
            .find(text)
            ?.let { m ->
                runCatching {
                    LocalDate(
                        m.groupValues[1].toInt(),
                        m.groupValues[2].toInt(),
                        m.groupValues[3].toInt(),
                    )
                }.getOrNull()
            }
        if (gregorian != null) {
            return ConfidentValue(gregorian, FieldConfidence.DETECTED)
        }

        return ConfidentValue(null, FieldConfidence.MISSING)
    }

    private fun suggestTitle(text: String, type: TransactionType?): ConfidentValue<String?> {
        val through = Regex("""از طریق\s*[:：]?\s*([^\n/|]{2,40})""").find(text)
            ?.groupValues?.getOrNull(1)?.trim()
        if (!through.isNullOrBlank() && !looksLikeId(through)) {
            return ConfidentValue(through.take(40), FieldConfidence.DETECTED)
        }
        val desc = Regex("""شرح\s*[:：]?\s*([^\n/|]{2,40})""").find(text)
            ?.groupValues?.getOrNull(1)?.trim()
        if (!desc.isNullOrBlank() && !looksLikeId(desc)) {
            return ConfidentValue(desc.take(40), FieldConfidence.DETECTED)
        }
        val fallback = when (type) {
            TransactionType.INCOME -> "واریز بانکی"
            TransactionType.EXPENSE -> "برداشت بانکی"
            null -> "تراکنش بانکی"
        }
        return ConfidentValue(fallback, FieldConfidence.NEEDS_REVIEW)
    }

    private fun detectAccountHint(text: String): ConfidentValue<String?> {
        val m = Regex("""حساب\s*[:：]?\s*([*\dxX-]{4,24})""").find(text)
            ?: Regex("""برداشت از حساب\s+([*\dxX-]{4,24})""").find(text)
            ?: return ConfidentValue(null, FieldConfidence.MISSING)
        val hint = m.groupValues[1].trim()
        // Masked / partial account → never auto-bind; only hint for user review
        return ConfidentValue(hint, FieldConfidence.NEEDS_REVIEW)
    }

    private fun looksLikeId(value: String): Boolean {
        val digits = DigitNormalizer.digitsOnly(value)
        return digits.length >= 8 && value.filter { it.isLetter() }.length < 2
    }

    private fun emptyResult(kind: SmsMessageKind, reason: String) = ParsedSmsTransaction(
        messageKind = kind,
        unsupportedReason = reason,
        type = ConfidentValue(null, FieldConfidence.MISSING),
        amountRial = ConfidentValue(null, FieldConfidence.MISSING),
        detectedUnit = ConfidentValue(DetectedMoneyUnit.UNKNOWN, FieldConfidence.MISSING),
        titleSuggestion = ConfidentValue(null, FieldConfidence.MISSING),
        date = ConfidentValue(null, FieldConfidence.MISSING),
        balanceAfterRial = ConfidentValue(null, FieldConfidence.MISSING),
        accountHint = ConfidentValue(null, FieldConfidence.MISSING),
    )

    companion object {
        private val MONEY_LABEL = Regex("""مبلغ\s*[:：]?""")
        private val BALANCE_LABEL = Regex(
            """(?:مانده|موجودی|مانده حساب)\s*[:：]?""",
        )
    }
}
