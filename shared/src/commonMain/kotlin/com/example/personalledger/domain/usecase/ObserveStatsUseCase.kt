package com.example.personalledger.domain.usecase

import com.example.personalledger.domain.calendar.CalendarSystem
import com.example.personalledger.domain.repository.TransactionRepository
import com.example.personalledger.domain.stats.StatsCalculator
import com.example.personalledger.domain.stats.StatsReport
import com.example.personalledger.domain.stats.StatsTimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

class ObserveStatsUseCase(
    private val transactions: TransactionRepository,
) {
    operator fun invoke(
        range: StatsTimeRange,
        anchor: LocalDate,
        calendar: CalendarSystem = CalendarSystem.JALALI,
    ): Flow<StatsReport> =
        transactions.observeAll().map { list ->
            StatsCalculator.buildReport(list, range, anchor, calendar)
        }
}