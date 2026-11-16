package com.example.personalledger.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.personalledger.resources.Res
import com.example.personalledger.resources.NotoNastaliqUrdu
import org.jetbrains.compose.resources.Font

@Composable
fun nastaliqFontFamily(): FontFamily =
    FontFamily(Font(Res.font.NotoNastaliqUrdu, weight = FontWeight.Normal))
