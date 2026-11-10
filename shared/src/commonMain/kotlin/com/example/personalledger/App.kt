package com.example.personalledger

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.personalledger.di.AppContainer
import com.example.personalledger.presentation.calendar.LocalCalendarSystem
import com.example.personalledger.presentation.components.LedgerBottomBar
import com.example.personalledger.presentation.components.LoadingBox
import com.example.personalledger.presentation.components.MainTab
import com.example.personalledger.presentation.navigation.AppDestination
import com.example.personalledger.presentation.navigation.NavigateBackHandler
import com.example.personalledger.presentation.screens.accounts.AccountsScreen
import com.example.personalledger.presentation.screens.accounts.AccountsViewModel
import com.example.personalledger.presentation.screens.ai.AiScreen
import com.example.personalledger.presentation.screens.ai.AiViewModel
import com.example.personalledger.presentation.screens.categories.CategoriesScreen
import com.example.personalledger.presentation.screens.categories.CategoriesViewModel
import com.example.personalledger.presentation.screens.goals.GoalAllocationScreen
import com.example.personalledger.presentation.screens.goals.GoalAllocationViewModel
import com.example.personalledger.presentation.screens.goals.GoalCompletedScreen
import com.example.personalledger.presentation.screens.goals.GoalDetailScreen
import com.example.personalledger.presentation.screens.goals.GoalDetailViewModel
import com.example.personalledger.presentation.screens.goals.GoalEditorScreen
import com.example.personalledger.presentation.screens.goals.GoalEditorViewModel
import com.example.personalledger.presentation.screens.goals.GoalsScreen
import com.example.personalledger.presentation.screens.goals.GoalsViewModel
import com.example.personalledger.presentation.screens.home.HomeScreen
import com.example.personalledger.presentation.screens.home.HomeViewModel
import com.example.personalledger.presentation.screens.settings.SettingsScreen
import com.example.personalledger.presentation.screens.settings.SettingsViewModel
import com.example.personalledger.presentation.screens.stats.StatsScreen
import com.example.personalledger.presentation.screens.stats.StatsViewModel
import com.example.personalledger.presentation.screens.transactionedit.TransactionEditScreen
import com.example.personalledger.presentation.screens.transactionedit.TransactionEditViewModel
import com.example.personalledger.presentation.screens.transactions.TransactionsScreen
import com.example.personalledger.presentation.screens.transactions.TransactionsViewModel
import com.example.personalledger.presentation.theme.PersonalLedgerTheme
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

@Composable
fun App(container: AppContainer) {
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(container) {
        container.initialize()
        ready = true
    }

    val themeMode by container.themePreferences.observe()
        .collectAsStateWithLifecycle(initialValue = container.themePreferences.read())
    val calendarSystem by container.displayPreferences.observeCalendar()
        .collectAsStateWithLifecycle(initialValue = container.displayPreferences.readCalendar())

    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalCalendarSystem provides calendarSystem,
    ) {
        PersonalLedgerTheme(themeMode = themeMode) {
            if (!ready) {
                LoadingBox()
            } else {
                LedgerRoot()
            }
        }
    }
}

@Composable
private fun LedgerRoot() {
    val container = LocalAppContainer.current
    var tab by remember { mutableStateOf(MainTab.Home) }
    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Root) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val categories by container.categoryRepository.observeAll()
        .map { list -> list.associate { it.id to it.name } }
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    // Back: nested screen → root tabs; other tab → Home; Home → leave app
    val interceptBack = destination !is AppDestination.Root || tab != MainTab.Home
    NavigateBackHandler(enabled = interceptBack) {
        when {
            destination !is AppDestination.Root -> destination = AppDestination.Root
            tab != MainTab.Home -> tab = MainTab.Home
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (destination is AppDestination.Root) {
                LedgerBottomBar(selected = tab, onSelect = { tab = it })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val dest = destination) {
                AppDestination.Root -> {
                    when (tab) {
                        MainTab.Home -> {
                            val vm: HomeViewModel = viewModel(factory = rememberFactory(container) {
                                HomeViewModel(
                                    container.observeDashboard,
                                    container.displayPreferences,
                                )
                            })
                            HomeScreen(
                                viewModel = vm,
                                categoryNames = categories,
                                onAddTransaction = {
                                    destination = AppDestination.TransactionEditor(null)
                                },
                                onOpenTransaction = {
                                    destination = AppDestination.TransactionEditor(it)
                                },
                                onSeeAllTransactions = {
                                    destination = AppDestination.TransactionsList
                                },
                            )
                        }
                        MainTab.Goals -> {
                            val vm: GoalsViewModel = viewModel(factory = rememberFactory(container) {
                                GoalsViewModel(
                                    container.observeActiveGoals,
                                    container.observeArchivedGoals,
                                    container.restoreGoal,
                                )
                            })
                            GoalsScreen(
                                viewModel = vm,
                                onCreateGoal = {
                                    destination = AppDestination.GoalEditor(null)
                                },
                                onOpenGoal = {
                                    destination = AppDestination.GoalDetail(it)
                                },
                            )
                        }
                        MainTab.Stats -> {
                            val vm: StatsViewModel = viewModel(factory = rememberFactory(container) {
                                StatsViewModel(
                                    container.observeStats,
                                    container.displayPreferences,
                                )
                            })
                            StatsScreen(vm)
                        }
                        MainTab.Ai -> {
                            val vm: AiViewModel = viewModel(factory = rememberFactory(container) {
                                AiViewModel(
                                    container.aiAssistantRepository,
                                    container.askAiAssistant,
                                    container.resetAiAssistant,
                                    container.displayPreferences,
                                )
                            })
                            AiScreen(vm)
                        }
                        MainTab.Settings -> {
                            val vm: SettingsViewModel = viewModel(factory = rememberFactory(container) {
                                SettingsViewModel(
                                    container.themePreferences,
                                    container.displayPreferences,
                                )
                            })
                            SettingsScreen(
                                viewModel = vm,
                                onOpenCategories = {
                                    destination = AppDestination.Categories
                                },
                                onOpenAccounts = {
                                    destination = AppDestination.Accounts
                                },
                            )
                        }
                    }
                }
                AppDestination.Categories -> {
                    val vm: CategoriesViewModel = viewModel(factory = rememberFactory(container) {
                        CategoriesViewModel(
                            container.observeCategories,
                            container.saveCategory,
                            container.deleteCategory,
                        )
                    })
                    CategoriesScreen(
                        viewModel = vm,
                        onBack = { destination = AppDestination.Root },
                    )
                }
                AppDestination.Accounts -> {
                    val vm: AccountsViewModel = viewModel(factory = rememberFactory(container) {
                        AccountsViewModel(
                            container.observeAccounts,
                            container.saveAccount,
                            container.deleteAccount,
                        )
                    })
                    AccountsScreen(
                        viewModel = vm,
                        onBack = { destination = AppDestination.Root },
                    )
                }
                AppDestination.TransactionsList -> {
                    val vm: TransactionsViewModel = viewModel(factory = rememberFactory(container) {
                        TransactionsViewModel(
                            container.observeTransactions,
                            container.categoryRepository,
                            container.deleteTransaction,
                        )
                    })
                    TransactionsScreen(
                        viewModel = vm,
                        onAddTransaction = {
                            destination = AppDestination.TransactionEditor(null)
                        },
                        onOpenTransaction = {
                            destination = AppDestination.TransactionEditor(it)
                        },
                        onBack = { destination = AppDestination.Root },
                    )
                }
                is AppDestination.TransactionEditor -> {
                    val key = dest.transactionId ?: "new"
                    val owner = remember(key) { SimpleViewModelStoreOwner() }
                    val vm: TransactionEditViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = rememberFactory(container, key) {
                            TransactionEditViewModel(
                                transactionId = dest.transactionId,
                                transactionRepository = container.transactionRepository,
                                categoryRepository = container.categoryRepository,
                                accountRepository = container.accountRepository,
                                saveTransaction = container.saveTransaction,
                                parseBankSms = container.parseBankSms,
                                findSimilarTransactions = container.findSimilarTransactions,
                            )
                        },
                    )
                    TransactionEditScreen(
                        viewModel = vm,
                        onBack = { destination = AppDestination.Root },
                        onDeleted = {
                            scope.launch {
                                dest.transactionId?.let { id ->
                                    runCatching { container.deleteTransaction(id) }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "error") }
                                }
                                destination = AppDestination.Root
                            }
                        },
                        onDeleteRequest = {},
                    )
                }
                is AppDestination.GoalEditor -> {
                    val key = dest.goalId ?: "new-goal"
                    val owner = remember(key) { SimpleViewModelStoreOwner() }
                    val vm: GoalEditorViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = rememberFactory(container, key) {
                            GoalEditorViewModel(
                                goalId = dest.goalId,
                                goalRepository = container.goalRepository,
                                saveGoal = container.saveGoal,
                            )
                        },
                    )
                    GoalEditorScreen(
                        viewModel = vm,
                        onBack = {
                            destination = dest.goalId?.let { AppDestination.GoalDetail(it) }
                                ?: AppDestination.Root
                        },
                        onSaved = { id ->
                            destination = AppDestination.GoalDetail(id)
                        },
                    )
                }
                is AppDestination.GoalDetail -> {
                    val owner = remember(dest.goalId) { SimpleViewModelStoreOwner() }
                    val vm: GoalDetailViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = rememberFactory(container, dest.goalId) {
                            GoalDetailViewModel(
                                goalId = dest.goalId,
                                observeDetail = container.observeGoalDetail,
                                completeGoal = container.completeGoal,
                                archiveGoal = container.archiveGoal,
                                deleteGoal = container.deleteGoal,
                                deleteAllocation = container.deleteGoalAllocation,
                            )
                        },
                    )
                    GoalDetailScreen(
                        viewModel = vm,
                        onBack = { destination = AppDestination.Root },
                        onEdit = { destination = AppDestination.GoalEditor(it) },
                        onAddAmount = {
                            destination = AppDestination.GoalAllocationEditor(it)
                        },
                        onEditAllocation = { goalId, allocationId ->
                            destination = AppDestination.GoalAllocationEditor(goalId, allocationId)
                        },
                        onCompleted = { destination = AppDestination.GoalCompleted(it) },
                    )
                }
                is AppDestination.GoalAllocationEditor -> {
                    val key = "${dest.goalId}-${dest.allocationId ?: "new"}"
                    val owner = remember(key) { SimpleViewModelStoreOwner() }
                    val vm: GoalAllocationViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = rememberFactory(container, key) {
                            GoalAllocationViewModel(
                                goalId = dest.goalId,
                                allocationId = dest.allocationId,
                                allocationRepository = container.goalAllocationRepository,
                                observeAccounts = container.observeAccounts,
                                saveAllocation = container.saveGoalAllocation,
                            )
                        },
                    )
                    GoalAllocationScreen(
                        viewModel = vm,
                        onBack = { destination = AppDestination.GoalDetail(dest.goalId) },
                        onSaved = { destination = AppDestination.GoalDetail(dest.goalId) },
                    )
                }
                is AppDestination.GoalCompleted -> {
                    val owner = remember(dest.goalId) { SimpleViewModelStoreOwner() }
                    val vm: GoalDetailViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = rememberFactory(container, "completed-${dest.goalId}") {
                            GoalDetailViewModel(
                                goalId = dest.goalId,
                                observeDetail = container.observeGoalDetail,
                                completeGoal = container.completeGoal,
                                archiveGoal = container.archiveGoal,
                                deleteGoal = container.deleteGoal,
                                deleteAllocation = container.deleteGoalAllocation,
                            )
                        },
                    )
                    GoalCompletedScreen(
                        viewModel = vm,
                        onBackToGoals = {
                            tab = MainTab.Goals
                            destination = AppDestination.Root
                        },
                        onArchive = {
                            scope.launch {
                                runCatching { container.archiveGoal(dest.goalId) }
                                tab = MainTab.Goals
                                destination = AppDestination.Root
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFactory(
    container: AppContainer,
    key: Any? = null,
    create: () -> ViewModel,
): ViewModelProvider.Factory {
    return remember(container, key) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
                return create() as T
            }
        }
    }
}

private class SimpleViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
