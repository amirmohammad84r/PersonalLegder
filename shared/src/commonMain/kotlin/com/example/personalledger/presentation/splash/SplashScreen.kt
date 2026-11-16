package com.example.personalledger.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.personalledger.presentation.strings.Strings
import com.example.personalledger.presentation.theme.LedgerColors
import com.example.personalledger.presentation.theme.nastaliqFontFamily

private const val FadeOutMillis = 380

/**
 * Brand splash. Dismisses as soon as [appReady] with a short fade/scale out — no artificial hold.
 */
@Composable
fun AppSplashOverlay(
    appReady: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val fontFamily = nastaliqFontFamily()

    LaunchedEffect(appReady) {
        if (!appReady) return@LaunchedEffect
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = FadeOutMillis, easing = FastOutSlowInEasing),
        )
        onFinished()
    }

    val t = progress.value
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - t
                scaleX = 1f - (0.04f * t)
                scaleY = 1f - (0.04f * t)
            }
            .background(LedgerColors.SplashBrown),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = Strings.appName,
            color = LedgerColors.SplashYellow,
            fontSize = 48.sp,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(1f - t),
        )
    }
}
