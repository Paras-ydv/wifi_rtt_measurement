package com.example.wifirttmeasurement.presentation.ui.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.wifirttmeasurement.presentation.components.ScreenPlaceholder
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
) {
    LaunchedEffect(onContinue) {
        delay(SplashDelayMillis)
        onContinue()
    }

    ScreenPlaceholder(
        title = "Wi-Fi RTT Lab",
        subtitle = "Real-time ranging research prototype",
    )
}

private const val SplashDelayMillis = 650L
