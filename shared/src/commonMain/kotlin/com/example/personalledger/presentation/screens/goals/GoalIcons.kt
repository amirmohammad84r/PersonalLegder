package com.example.personalledger.presentation.screens.goals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

object GoalIcons {
    data class Option(val key: String, val icon: ImageVector)

    val options: List<Option> = listOf(
        Option("star", Icons.Default.Star),
        Option("home", Icons.Default.Home),
        Option("favorite", Icons.Default.Favorite),
        Option("person", Icons.Default.Person),
    )

    fun iconFor(key: String?): ImageVector =
        options.find { it.key == key }?.icon ?: Icons.Default.Star
}
