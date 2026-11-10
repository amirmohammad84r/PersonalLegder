package com.example.personalledger.presentation.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.personalledger.data.prefs.DisplayPreferences
import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.stats.StatsBucket
import com.example.personalledger.domain.stats.StatsCalculator
import com.example.personalledger.domain.stats.StatsReport
import com.example.personalledger.domain.stats.StatsTimeRange
import com.example.personalledger.domain.stats.StatsTypeFilter
import com.example.personalledger.domain.todayLocalDate
import com.example.personalledger.domain.usecase.ObserveStatsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class StatsUiState(
    val typeFilter: StatsTypeFilter = StatsTypeFilter.ALL,
    val timeRange: StatsTimeRange = StatsTimeRange.MONTHLY,
    val calendar: CalendarSystem = CalendarSystem.JALALI,
    val periodLabel: String = "",
    val totalIncomeRial: Long = 0L,
    val totalExpenseRial: Long = 0L,
    val buckets: List<StatsBucket> = emptyList(),
    val selectedIndex: Int? = null,
    val hasAnyTransactionInApp: Boolean = false,
    val hasDataInPeriod: Boolean = false,
    val hasVisibleSeriesData: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    private val observeStats: ObserveStatsUseCase,
    displayPreferences: DisplayPreferences,
) : ViewModel() {
    private val typeFilter = MutableStateFlow(StatsTypeFilter.ALL)
    private val timeRange = MutableStateFlow(StatsTimeRange.MONTHLY)
    private val anchor = MutableStateFlow(todayLocalDate())
    private val selectedIndex = MutableStateFlow<Int?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val calendarFlow = displayPreferences.observeCalendar()

    private val reportFlow = combine(timeRange, anchor, calendarFlow) { range, date, cal ->
        Triple(range, date, cal)
    }.flatMapLatest { (range, date, cal) -> observeStats(range, date, cal) }

    private val controlsFlow = combine(typeFilter, timeRange, calendarFlow, selectedIndex, error) {
            type, range, cal, selected, err ->
        Controls(type, range, cal, selected, err)
    }

    val uiState: StateFlow<StatsUiState> = combine(controlsFlow, reportFlow) { controls, report ->
        toUiState(controls, report)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun onTypeFilterChange(filter: StatsTypeFilter) {
        typeFilter.value = filter
        selectedIndex.value = null
    }

    fun onTimeRangeChange(range: StatsTimeRange) {
        timeRange.value = range
        selectedIndex.value = null
    }

    fun previousPeriod() {
        shift(-1)
    }

    fun nextPeriod() {
        shift(1)
    }

    fun selectBucket(index: Int?) {
        selectedIndex.value = index
    }

    fun clearError() {
        error.value = null
    }

    private fun shift(delta: Int) {
        val cal = uiState.value.calendar
        anchor.update { current ->
            StatsCalculator.shiftAnchor(current, timeRange.value, delta, cal)
        }
        selectedIndex.value = null
    }

    private data class Controls(
        val type: StatsTypeFilter,
        val range: StatsTimeRange,
        val calendar: CalendarSystem,
        val selected: Int?,
        val error: String?,
    )

    private fun toUiState(controls: Controls, report: StatsReport): StatsUiState {
        val visibleData = when (controls.type) {
            StatsTypeFilter.ALL -> report.buckets.any { it.hasData }
            StatsTypeFilter.INCOME -> report.buckets.any { it.incomeRial > 0L }
            StatsTypeFilter.EXPENSE -> report.buckets.any { it.expenseRial > 0L }
        }
        return StatsUiState(
            typeFilter = controls.type,
            timeRange = controls.range,
            calendar = controls.calendar,
            periodLabel = report.periodLabel,
            totalIncomeRial = report.totalIncomeRial,
            totalExpenseRial = report.totalExpenseRial,
            buckets = report.buckets,
            selectedIndex = controls.selected?.takeIf { it in report.buckets.indices },
            hasAnyTransactionInApp = report.hasAnyTransactionInApp,
            hasDataInPeriod = report.hasDataInPeriod,
            hasVisibleSeriesData = visibleData,
            loading = false,
            error = controls.error,
        )
    }
}
