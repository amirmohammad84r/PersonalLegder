package com.example.personalledger.di

import com.example.personalledger.data.ai.AiChatHistoryStore
import com.example.personalledger.data.ai.AiSettingsStore
import com.example.personalledger.data.ai.createAiHttpClient
import com.example.personalledger.data.ai.provider.AiProviderRegistry
import com.example.personalledger.data.local.LedgerStore
import com.example.personalledger.data.local.PlatformFileStore
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.data.prefs.ThemePreferences
import com.example.personalledger.data.repository.AccountRepositoryImpl
import com.example.personalledger.data.repository.AiAssistantRepositoryImpl
import com.example.personalledger.data.repository.CategoryRepositoryImpl
import com.example.personalledger.data.repository.GoalAllocationRepositoryImpl
import com.example.personalledger.data.repository.GoalRepositoryImpl
import com.example.personalledger.data.repository.TransactionRepositoryImpl
import com.example.personalledger.data.secure.SecureCredentialStore
import com.example.personalledger.domain.repository.AccountRepository
import com.example.personalledger.domain.repository.AiAssistantRepository
import com.example.personalledger.domain.repository.CategoryRepository
import com.example.personalledger.domain.repository.GoalAllocationRepository
import com.example.personalledger.domain.repository.GoalRepository
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.sms.HeuristicBankSmsParser
import com.example.personalledger.domain.sms.SmsTransactionParser
import com.example.personalledger.domain.usecase.ArchiveGoalUseCase
import com.example.personalledger.domain.usecase.AskAiAssistantUseCase
import com.example.personalledger.domain.usecase.BuildFinancialInsightUseCase
import com.example.personalledger.domain.usecase.CompleteGoalUseCase
import com.example.personalledger.domain.usecase.DeleteAccountUseCase
import com.example.personalledger.domain.usecase.DeleteCategoryUseCase
import com.example.personalledger.domain.usecase.DeleteGoalAllocationUseCase
import com.example.personalledger.domain.usecase.DeleteGoalUseCase
import com.example.personalledger.domain.usecase.DeleteTransactionUseCase
import com.example.personalledger.domain.usecase.FindSimilarTransactionsUseCase
import com.example.personalledger.domain.usecase.ObserveAccountsUseCase
import com.example.personalledger.domain.usecase.ObserveActiveGoalsUseCase
import com.example.personalledger.domain.usecase.ObserveArchivedGoalsUseCase
import com.example.personalledger.domain.usecase.ObserveCategoriesUseCase
import com.example.personalledger.domain.usecase.ObserveDashboardUseCase
import com.example.personalledger.domain.usecase.ObserveGoalDetailUseCase
import com.example.personalledger.domain.usecase.ObserveStatsUseCase
import com.example.personalledger.domain.usecase.ObserveTransactionsUseCase
import com.example.personalledger.domain.usecase.ParseBankSmsUseCase
import com.example.personalledger.domain.usecase.ResetAiAssistantUseCase
import com.example.personalledger.domain.usecase.RestoreGoalUseCase
import com.example.personalledger.domain.usecase.SaveAccountUseCase
import com.example.personalledger.domain.usecase.SaveCategoryUseCase
import com.example.personalledger.domain.usecase.SaveGoalAllocationUseCase
import com.example.personalledger.domain.usecase.SaveGoalUseCase
import com.example.personalledger.domain.usecase.SaveTransactionUseCase
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient

class AppContainer(
    fileStore: PlatformFileStore,
    secureCredentialStore: SecureCredentialStore,
    settings: Settings = Settings(),
) {
    private val ledgerStore = LedgerStore(fileStore)
    private val aiHttpClient: HttpClient = createAiHttpClient()

    val themePreferences = ThemePreferences(settings)
    val displayPreferences = DisplayPreferences(settings)

    val transactionRepository: TransactionRepository = TransactionRepositoryImpl(ledgerStore)
    val categoryRepository: CategoryRepository = CategoryRepositoryImpl(ledgerStore)
    val accountRepository: AccountRepository = AccountRepositoryImpl(ledgerStore)
    val goalRepository: GoalRepository = GoalRepositoryImpl(ledgerStore)
    val goalAllocationRepository: GoalAllocationRepository =
        GoalAllocationRepositoryImpl(ledgerStore)

    private val aiSettingsStore = AiSettingsStore(settings, secureCredentialStore)
    private val aiChatHistoryStore = AiChatHistoryStore(fileStore)
    private val aiProviderRegistry = AiProviderRegistry(aiHttpClient)
    val aiAssistantRepository: AiAssistantRepository = AiAssistantRepositoryImpl(
        settingsStore = aiSettingsStore,
        historyStore = aiChatHistoryStore,
        providers = aiProviderRegistry,
    )

    private val smsParser: SmsTransactionParser = HeuristicBankSmsParser()
    val parseBankSms = ParseBankSmsUseCase(smsParser)
    val findSimilarTransactions = FindSimilarTransactionsUseCase(transactionRepository)

    val observeDashboard = ObserveDashboardUseCase(
        transactionRepository,
        accountRepository,
        goalAllocationRepository,
    )
    val observeTransactions = ObserveTransactionsUseCase(transactionRepository)
    val observeStats = ObserveStatsUseCase(transactionRepository)
    val saveTransaction = SaveTransactionUseCase(
        transactionRepository,
        categoryRepository,
        accountRepository,
    )
    val deleteTransaction = DeleteTransactionUseCase(transactionRepository)

    val observeCategories = ObserveCategoriesUseCase(categoryRepository)
    val saveCategory = SaveCategoryUseCase(categoryRepository)
    val deleteCategory = DeleteCategoryUseCase(categoryRepository)

    val observeAccounts = ObserveAccountsUseCase(
        accountRepository,
        transactionRepository,
        goalAllocationRepository,
    )
    val saveAccount = SaveAccountUseCase(accountRepository)
    val deleteAccount = DeleteAccountUseCase(
        accountRepository,
        transactionRepository,
        goalAllocationRepository,
    )

    val observeActiveGoals = ObserveActiveGoalsUseCase(goalRepository, goalAllocationRepository)
    val observeArchivedGoals = ObserveArchivedGoalsUseCase(goalRepository, goalAllocationRepository)
    val observeGoalDetail = ObserveGoalDetailUseCase(
        goalRepository,
        goalAllocationRepository,
        accountRepository,
    )
    val saveGoal = SaveGoalUseCase(goalRepository)
    val saveGoalAllocation = SaveGoalAllocationUseCase(
        goalRepository,
        goalAllocationRepository,
        accountRepository,
        transactionRepository,
    )
    val deleteGoalAllocation = DeleteGoalAllocationUseCase(goalRepository, goalAllocationRepository)
    val completeGoal = CompleteGoalUseCase(goalRepository)
    val archiveGoal = ArchiveGoalUseCase(goalRepository)
    val restoreGoal = RestoreGoalUseCase(goalRepository)
    val deleteGoal = DeleteGoalUseCase(goalRepository, goalAllocationRepository)

    val buildFinancialInsight = BuildFinancialInsightUseCase(
        transactionRepository,
        accountRepository,
        goalRepository,
        goalAllocationRepository,
    )
    val askAiAssistant = AskAiAssistantUseCase(aiAssistantRepository, buildFinancialInsight)
    val resetAiAssistant = ResetAiAssistantUseCase(aiAssistantRepository)

    suspend fun initialize() {
        ledgerStore.initialize()
    }
}
