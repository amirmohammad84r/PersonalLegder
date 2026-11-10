package com.example.personalledger.presentation.screens.transactionedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.domain.model.Account
import com.example.personalledger.domain.model.Category
import com.example.personalledger.domain.model.TransactionType
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.DomainException
import com.example.personalledger.domain.repository.TransactionDraft
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.CurrencyConfig
import com.example.personalledger.domain.sms.DetectedMoneyUnit
import com.example.personalledger.domain.sms.FieldConfidence
import com.example.personalledger.domain.sms.SmsAccountMatcher
import com.example.personalledger.domain.sms.SmsMessageKind
import com.example.personalledger.domain.todayLocalDate
import com.example.personalledger.domain.usecase.FindSimilarTransactionsUseCase
import com.example.personalledger.domain.usecase.ParseBankSmsUseCase
import com.example.personalledger.domain.usecase.SaveTransactionUseCase
import com.example.personalledger.presentation.format.toLatinDigits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

enum class AddEntryStep {
    ChooseMode,
    ManualForm,
    SmsPaste,
    SmsReview,
}

data class FieldStatus(
    val type: FieldConfidence = FieldConfidence.MISSING,
    val amount: FieldConfidence = FieldConfidence.MISSING,
    val unit: FieldConfidence = FieldConfidence.MISSING,
    val title: FieldConfidence = FieldConfidence.MISSING,
    val date: FieldConfidence = FieldConfidence.MISSING,
    val account: FieldConfidence = FieldConfidence.MISSING,
)

data class TransactionEditForm(
    val id: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val title: String = "",
    val categoryId: String? = null,
    val accountId: String? = null,
    val dateEpochDays: Long = todayLocalDate().toEpochDays().toLong(),
    val detectedUnitLabel: String? = null,
    val balanceAfterLabel: String? = null,
    val accountHint: String? = null,
    val amountError: String? = null,
    val titleError: String? = null,
    val categoryError: String? = null,
    val accountError: String? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

private data class SmsUi(
    val text: String = "",
    val parsing: Boolean = false,
    val error: String? = null,
    val fieldStatus: FieldStatus = FieldStatus(),
    val duplicateWarning: String? = null,
)

data class TransactionEditUiState(
    val step: AddEntryStep = AddEntryStep.ManualForm,
    val form: TransactionEditForm = TransactionEditForm(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val loading: Boolean = true,
    val smsText: String = "",
    val parsing: Boolean = false,
    val smsError: String? = null,
    val fieldStatus: FieldStatus = FieldStatus(),
    val duplicateWarning: String? = null,
    val canSave: Boolean = false,
)

class TransactionEditViewModel(
    private val transactionId: String?,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val saveTransaction: SaveTransactionUseCase,
    private val parseBankSms: ParseBankSmsUseCase,
    private val findSimilarTransactions: FindSimilarTransactionsUseCase,
    private val accountMatcher: SmsAccountMatcher = SmsAccountMatcher(),
) : ViewModel() {
    private val isNew = transactionId == null
    private val step = MutableStateFlow(
        if (isNew) AddEntryStep.ChooseMode else AddEntryStep.ManualForm,
    )
    private val form = MutableStateFlow(TransactionEditForm(id = transactionId))
    private val loading = MutableStateFlow(true)
    private val smsUi = MutableStateFlow(SmsUi())

    val uiState: StateFlow<TransactionEditUiState> = combine(
        combine(
            step,
            form,
            categoryRepository.observeAll(),
            accountRepository.observeAll(),
            loading,
        ) { currentStep, f, categories, accounts, isLoading ->
            Triple(currentStep, f, Triple(categories, accounts, isLoading))
        },
        smsUi,
    ) { core, sms ->
        val (currentStep, f, rest) = core
        val (categories, accounts, isLoading) = rest
        TransactionEditUiState(
            step = currentStep,
            form = f,
            categories = categories.filter { it.type == f.type },
            accounts = accounts,
            loading = isLoading,
            smsText = sms.text,
            parsing = sms.parsing,
            smsError = sms.error,
            fieldStatus = sms.fieldStatus,
            duplicateWarning = sms.duplicateWarning,
            canSave = f.amountInput.toLongOrNull()?.let { it > 0 } == true &&
                f.title.isNotBlank() &&
                f.categoryId != null &&
                f.accountId != null &&
                !f.saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionEditUiState())

    init {
        viewModelScope.launch {
            if (transactionId != null) {
                val existing = transactionRepository.getById(transactionId)
                if (existing != null) {
                    form.value = TransactionEditForm(
                        id = existing.id,
                        type = existing.type,
                        amountInput = existing.amountRial.toString(),
                        title = existing.title,
                        categoryId = existing.categoryId,
                        accountId = existing.accountId,
                        dateEpochDays = existing.date.toEpochDays().toLong(),
                    )
                }
            }
            loading.value = false
        }
    }

    fun chooseManual() {
        step.value = AddEntryStep.ManualForm
        smsUi.update { it.copy(fieldStatus = FieldStatus(), duplicateWarning = null) }
    }

    fun chooseSms() {
        step.value = AddEntryStep.SmsPaste
        smsUi.update { it.copy(error = null) }
    }

    fun backToModeChoice() {
        if (!isNew) return
        step.value = AddEntryStep.ChooseMode
        form.value = TransactionEditForm()
        smsUi.value = SmsUi()
    }

    fun backToSmsPaste() {
        step.value = AddEntryStep.SmsPaste
        smsUi.update { it.copy(error = null, duplicateWarning = null) }
    }

    fun onSmsTextChange(value: String) {
        smsUi.update { it.copy(text = value, error = null) }
    }

    fun clearSmsText() {
        smsUi.update { it.copy(text = "", error = null) }
    }

    fun parseSms() {
        if (smsUi.value.parsing) return
        val raw = smsUi.value.text.trim()
        if (raw.isEmpty()) {
            smsUi.update { it.copy(error = "متن پیامک را وارد یا جای‌گذاری کنید") }
            return
        }
        smsUi.update { it.copy(parsing = true, error = null) }
        viewModelScope.launch {
            try {
                val parsed = parseBankSms(raw)
                if (parsed.messageKind != SmsMessageKind.TRANSACTION) {
                    smsUi.update {
                        it.copy(
                            parsing = false,
                            error = parsed.unsupportedReason
                                ?: "این متن برای ثبت تراکنش مناسب نیست",
                        )
                    }
                    return@launch
                }

                val type = parsed.type.value ?: TransactionType.EXPENSE
                val amount = parsed.amountRial.value
                val date = parsed.date.value ?: todayLocalDate()
                val unitLabel = when (parsed.detectedUnit.value) {
                    DetectedMoneyUnit.RIAL -> CurrencyConfig.displayName
                    DetectedMoneyUnit.TOMAN -> "تومان (تبدیل‌شده به ریال)"
                    DetectedMoneyUnit.UNKNOWN, null -> "واحد نامشخص — به‌عنوان ریال پیشنهاد شده؛ بازبینی کنید"
                }

                val accounts = accountRepository.observeAll().first()
                val suggested = accountMatcher.suggest(raw, accounts)

                form.value = TransactionEditForm(
                    type = type,
                    amountInput = amount?.toString().orEmpty(),
                    title = parsed.titleSuggestion.value.orEmpty(),
                    categoryId = null,
                    accountId = suggested?.account?.id,
                    dateEpochDays = date.toEpochDays().toLong(),
                    detectedUnitLabel = unitLabel,
                    balanceAfterLabel = parsed.balanceAfterRial.value?.let {
                        "$it ${CurrencyConfig.displayName}"
                    },
                    accountHint = listOfNotNull(
                        parsed.accountHint.value?.let { "نشانه در متن: $it" },
                        suggested?.reason,
                    ).joinToString(" · ").ifBlank { null },
                )
                val accountConfidence = when {
                    suggested != null -> FieldConfidence.NEEDS_REVIEW
                    else -> FieldConfidence.MISSING
                }
                smsUi.value = SmsUi(
                    text = "",
                    parsing = false,
                    fieldStatus = FieldStatus(
                        type = parsed.type.confidence,
                        amount = parsed.amountRial.confidence,
                        unit = parsed.detectedUnit.confidence,
                        title = parsed.titleSuggestion.confidence,
                        date = if (parsed.date.value == null) {
                            FieldConfidence.NEEDS_REVIEW
                        } else {
                            parsed.date.confidence
                        },
                        account = accountConfidence,
                    ),
                )
                step.value = AddEntryStep.SmsReview
                refreshDuplicateWarning()
            } catch (_: Exception) {
                smsUi.update {
                    it.copy(
                        parsing = false,
                        error = "بررسی پیامک ناموفق بود. می‌توانید دستی ثبت کنید.",
                    )
                }
            }
        }
    }

    fun onTypeChange(type: TransactionType) {
        form.update {
            it.copy(type = type, categoryId = null, categoryError = null)
        }
        refreshDuplicateWarning()
    }

    fun onAmountChange(value: String) {
        val cleaned = value.toLatinDigits().filter { it.isDigit() }
        form.update { it.copy(amountInput = cleaned, amountError = null) }
        refreshDuplicateWarning()
    }

    fun onTitleChange(value: String) {
        form.update { it.copy(title = value, titleError = null) }
    }

    fun onCategoryChange(id: String) {
        form.update { it.copy(categoryId = id, categoryError = null) }
    }

    fun onAccountChange(id: String) {
        form.update { it.copy(accountId = id, accountError = null) }
        refreshDuplicateWarning()
    }

    fun onDateChange(epochDays: Long) {
        form.update { it.copy(dateEpochDays = epochDays) }
        refreshDuplicateWarning()
    }

    private fun refreshDuplicateWarning() {
        val current = form.value
        val amount = current.amountInput.toLongOrNull() ?: run {
            smsUi.update { it.copy(duplicateWarning = null) }
            return
        }
        viewModelScope.launch {
            val similar = findSimilarTransactions(
                type = current.type,
                amountRial = amount,
                accountId = current.accountId,
                date = LocalDate.fromEpochDays(current.dateEpochDays.toInt()),
            )
            smsUi.update {
                it.copy(
                    duplicateWarning = if (similar.isNotEmpty()) {
                        "تراکنش مشابهی با همین مبلغ و تاریخ نزدیک از قبل ثبت شده. در صورت نیاز بازبینی کنید."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun save() {
        val current = form.value
        if (current.saving) return
        val amountOk = current.amountInput.toLongOrNull()?.let { it > 0 } == true
        val ready = amountOk &&
            current.title.isNotBlank() &&
            current.categoryId != null &&
            current.accountId != null
        if (!ready) {
            form.update {
                it.copy(
                    amountError = if (!amountOk) "مبلغ را تکمیل کنید" else null,
                    titleError = if (it.title.isBlank()) "عنوان را وارد کنید" else null,
                    categoryError = if (it.categoryId == null) "دسته‌بندی را انتخاب کنید" else null,
                    accountError = if (it.accountId == null) "حساب را انتخاب کنید" else null,
                )
            }
            return
        }
        form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                saveTransaction(
                    TransactionDraft(
                        id = current.id,
                        type = current.type,
                        amountRial = current.amountInput.toLongOrNull(),
                        title = current.title,
                        categoryId = current.categoryId,
                        accountId = current.accountId,
                        dateEpochDays = current.dateEpochDays,
                    ),
                )
                form.update { it.copy(saving = false, saved = true) }
            } catch (e: DomainException.Validation) {
                val msg = e.message.orEmpty()
                form.update {
                    it.copy(
                        saving = false,
                        amountError = msg.takeIf { m -> m.contains("مبلغ") },
                        titleError = msg.takeIf { m -> m.contains("عنوان") },
                        categoryError = msg.takeIf { m -> m.contains("دسته") },
                        accountError = msg.takeIf { m -> m.contains("حساب") },
                        error = msg,
                    )
                }
            } catch (e: Exception) {
                form.update { it.copy(saving = false, error = e.message) }
            }
        }
    }
}
