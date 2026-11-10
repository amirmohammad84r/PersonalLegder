package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.sms.ParsedSmsTransaction
import com.example.personalledger.domain.sms.SmsTransactionParser

class ParseBankSmsUseCase(
    private val parser: SmsTransactionParser,
) {
    operator fun invoke(rawText: String): ParsedSmsTransaction = parser.parse(rawText)
}
