package com.example.personalledger.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.domain.calendar.AppCalendar
import com.example.personalledger.domain.model.DashboardSummary
import com.example.personalledger.domain.todayLocalDate
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
    private val displayPreferences: DisplayPreferences,
) : ViewModel() {
    /** null = همه حساب‌ها */
    private val selectedAccountId = MutableStateFlow<String?>(null)

    /** 0 = ماه جاری؛ منفی = ماه‌های قبل؛ مثبت = ماه‌های بعد */
    private val monthOffset = MutableStateFlow(0)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedAccountId,
        monthOffset,
        displayPreferences.observeCalendar(),
    ) { accountId, offset, calendar ->
        Triple(accountId, offset, calendar)
    }
        .flatMapLatest { (accountId, offset, calendar) ->
            val anchor = AppCalendar.plusMonths(todayLocalDate(), offset, calendar)
            observeDashboard(
                accountId = accountId,
                calendar = calendar,
                today = anchor,
            )
        }
        .map<DashboardSummary, HomeUiState> { HomeUiState.Ready(it) }
        .catch { emit(HomeUiState.Error(it.message ?: "error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    fun onAccountFilterChange(accountId: String?) {
        selectedAccountId.value = accountId
    }

    fun shiftMonth(delta: Int) {
        monthOffset.value += delta
    }
}
