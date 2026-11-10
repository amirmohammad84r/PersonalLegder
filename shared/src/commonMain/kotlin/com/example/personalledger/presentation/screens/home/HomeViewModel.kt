package com.example.personalledger.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.domain.model.DashboardSummary
import com.example.personalledger.domain.usecase.ObserveDashboardUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val summary: DashboardSummary) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val observeDashboard: ObserveDashboardUseCase,
    displayPreferences: DisplayPreferences,
) : ViewModel() {
    /** null = کل حساب‌ها */
    private val selectedAccountId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedAccountId,
        displayPreferences.observeCalendar(),
    ) { accountId, calendar -> accountId to calendar }
        .flatMapLatest { (accountId, calendar) ->
            observeDashboard(accountId = accountId, calendar = calendar)
        }
        .map<DashboardSummary, HomeUiState> { HomeUiState.Ready(it) }
        .catch { emit(HomeUiState.Error(it.message ?: "error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun onAccountFilterChange(accountId: String?) {
        selectedAccountId.value = accountId
    }
}
