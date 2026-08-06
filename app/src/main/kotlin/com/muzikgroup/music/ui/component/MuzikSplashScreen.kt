package com.muzikgroup.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.muzikgroup.music.R
import kotlinx.coroutines.delay

@Composable
fun MuzikSplashHost(content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(900)
        showSplash = false
    }

    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(animationSpec = tween(350)),
            modifier = Modifier.fillMaxSize(),
        ) {
            MuzikSplashScreen()
        }
    }
}

@Composable
fun MuzikSplashScreen() {
    val transition = rememberInfiniteTransition(label = "muzikSplashSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 2500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "rotation",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.muzik_m),
            contentDescription = null,
            modifier = Modifier.size(200.dp).rotate(rotation),
        )
    }
}
