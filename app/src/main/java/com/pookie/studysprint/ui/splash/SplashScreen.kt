package com.pookie.studysprint.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pookie.studysprint.StudySprintApp
import com.pookie.studysprint.ui.theme.Radius
import com.pookie.studysprint.ui.theme.Spacing
import com.pookie.studysprint.ui.theme.neuRaised
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Splash kam se kam itni der dikhta hai, taaki jhalak na lage. */
private const val MIN_SHOW_MS = 900L

/** Safety: recovery kisi wajah se atak jaye to splash itne ke baad aage badh jaata hai. */
private const val RECOVERY_TIMEOUT_MS = 3_000L

/**
 * Splash screen. Do kaam ek saath (async):
 *  1. kam se kam MIN_SHOW_MS ka delay
 *  2. engine.recoveryFinished true hone ka intezaar (safety timeout ke saath)
 * Dono khatam hone par onFinished(hasLiveSession) bulata hai.
 * Session na ho to splash MIN_SHOW_MS par hi khatam: koi fixed lamba intezaar nahi.
 * hasLiveSession = true matlab app kill ke baad adhoora session wapas aaya hai.
 */
@Composable
fun SplashScreen(onFinished: (hasLiveSession: Boolean) -> Unit) {
    val engine = (LocalContext.current.applicationContext as StudySprintApp)
        .container.sessionEngine
    val onFinishedLatest by rememberUpdatedState(onFinished)

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "splashAlpha"
    )

    LaunchedEffect(Unit) {
        visible = true
        val hasLiveSession = coroutineScope {
            val minimumShow = async { delay(MIN_SHOW_MS) }
            val recovery = async {
                withTimeoutOrNull(RECOVERY_TIMEOUT_MS) {
                    engine.recoveryFinished.first { finished -> finished }
                }
            }
            minimumShow.await()
            recovery.await()
            val current = engine.state.value
            current.isRunning || current.isPaused
        }
        onFinishedLatest(hasLiveSession)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .neuRaised(shape = Radius.card, elevation = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Box(modifier = Modifier.size(Spacing.lg))
            Text(
                text = "StudySprint",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Intelligent Study Session Engine",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}