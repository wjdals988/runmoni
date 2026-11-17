package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runmoni.app.domain.PageMotionPolicy

@Composable
internal fun rememberPageAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(resolver, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = ValueAnimator.areAnimatorsEnabled()
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        lifecycle.addObserver(resume)
        enabled = ValueAnimator.areAnimatorsEnabled()
        onDispose { resolver.unregisterContentObserver(observer); lifecycle.removeObserver(resume) }
    }
    return enabled
}

/** A single current tree: no duplicate saveable-state providers, actions or effects. */
@Composable
internal fun PageTransitionFrame(routeKey: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val enabled = rememberPageAnimationsEnabled()
    val progress = remember(routeKey) { Animatable(if (enabled) 0f else 1f) }
    val travel = with(LocalDensity.current) { PageMotionPolicy.TRAVEL_DP.dp.toPx() }
    LaunchedEffect(routeKey, enabled) {
        if (!enabled) progress.snapTo(1f)
        else progress.animateTo(1f, tween(PageMotionPolicy.durationMillis(true)))
    }
    Box(modifier.fillMaxSize().graphicsLayer {
        alpha = if (enabled) progress.value else 1f
        translationY = if (enabled) (1f - progress.value) * travel else 0f
    }) { content() }
}
