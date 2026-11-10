package com.example.personalledger.domain.sms

import com.example.personalledger.domain.model.TransactionType
import kotlinx.datetime.LocalDate

enum class FieldConfidence {
    DETECTED,
    NEEDS_REVIEW,
    UNCERTAIN,
    MISSING,
}

enum class DetectedMoneyUnit {
    TOMAN,
    RIAL,
    UNKNOWN,
}

enum class SmsMessageKind {
    TRANSACTION,
    OTP,
    PROMOTIONAL,
    FAILED_OR_REVERSAL,
    UNSUPPORTED,
}

data class ConfidentValue<T>(
    val value: T?,
    val confidence: FieldConfidence,
)

data class ParsedSmsTransaction(
    val messageKind: SmsMessageKind,
    val unsupportedReason: String? = null,
    val type: ConfidentValue<TransactionType?>,
    /** Amount in ریال (storage unit). */
    val amountRial: ConfidentValue<Long?>,
    val detectedUnit: ConfidentValue<DetectedMoneyUnit>,
    val titleSuggestion: ConfidentValue<String?>,
    val date: ConfidentValue<LocalDate?>,
    val balanceAfterRial: ConfidentValue<Long?>,
    val accountHint: ConfidentValue<String?>,
)

interface SmsTransactionParser {
    fun parse(rawText: String): ParsedSmsTransaction
}
