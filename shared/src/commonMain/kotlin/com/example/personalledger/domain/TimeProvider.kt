package com.example.personalledger.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal expect fun currentEpochMilliseconds(): Long

internal fun nowInstant(): Instant =
    Instant.fromEpochMilliseconds(currentEpochMilliseconds())

internal fun todayLocalDate(): LocalDate =
    nowInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date
