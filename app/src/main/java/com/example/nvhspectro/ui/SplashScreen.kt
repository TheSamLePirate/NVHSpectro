package com.example.nvhspectro.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.nvhspectro.R
import com.example.nvhspectro.theme.NvhBackground
import com.example.nvhspectro.theme.NvhPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // Minuteur de 2 secondes pour fermer le splash screen
    LaunchedEffect(Unit) {
        delay(2000L)
        onSplashFinished()
    }

    // Animation infinie des ondes aquatiques pendant les 2s
    val transition = rememberInfiniteTransition(label = "WaterRippleTransition")

    // Onde 1
    val waveProgress1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Wave1",
    )

    // Onde 2 (décalée)
    val waveProgress2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, delayMillis = 450, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Wave2",
    )

    // Onde 3 (décalée)
    val waveProgress3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1400, delayMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "Wave3",
    )

    // Pulsation très légère du logo au centre
    val logoScale by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "LogoPulse",
    )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(NvhBackground),
        contentAlignment = Alignment.Center,
    ) {
        // Canvas de dessin des ondes aquatiques concentriques
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPoint = center
            val maxRadius = size.minDimension * 0.7f

            val waveColor = NvhPrimary

            listOf(waveProgress1, waveProgress2, waveProgress3).forEach { progress ->
                val currentRadius = maxRadius * progress
                val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
                val strokeWidth = (6.dp.toPx() * (1f - progress * 0.5f)).coerceAtLeast(1.dp.toPx())

                if (currentRadius > 0f && alpha > 0f) {
                    drawCircle(
                        color = waveColor.copy(alpha = alpha),
                        radius = currentRadius,
                        center = centerPoint,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
        }

        // Logo central
        Image(
            painter = painterResource(id = R.drawable.logo_vibratec),
            contentDescription = "Logo Vibratec",
            modifier =
                Modifier
                    .width(220.dp)
                    .graphicsLayer(
                        scaleX = logoScale,
                        scaleY = logoScale,
                    ),
        )
    }
}
