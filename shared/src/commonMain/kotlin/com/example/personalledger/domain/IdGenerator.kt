package com.example.personalledger.domain

import kotlin.random.Random

object IdGenerator {
    fun newId(): String {
        val time = currentEpochMilliseconds().toString(16)
        val random = Random.nextLong().toULong().toString(16).padStart(16, '0')
        return "$time-$random"
    }
}
