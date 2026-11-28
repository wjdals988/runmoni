package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runmoni.app.domain.PageMotionPolicy

internal val LocalRunmoniMotionEnabled = compositionLocalOf<Boolean?> { null }
private val PageEnterEasing = CubicBezierEasing(.23f, 1f, .32f, 1f)

private fun systemMotionEnabled(context: Context): Boolean {
    // The settings observer can run before ValueAnimator receives the new scale.
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE,
        if (ValueAnimator.areAnimatorsEnabled()) 1f else 0f)
    return scale > 0f && context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true
}

@Composable
internal fun rememberPageAnimationsEnabled(): Boolean {
    LocalRunmoniMotionEnabled.current?.let { return it }
    val context = LocalContext.current.applicationContext
    val resolver = context.contentResolver
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(systemMotionEnabled(context)) }
    DisposableEffect(context, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = systemMotionEnabled(context) }
        }
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = systemMotionEnabled(context)
        }
        val powerSave = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { enabled = systemMotionEnabled(context) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        ContextCompat.registerReceiver(context, powerSave, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(resume)
        enabled = systemMotionEnabled(context)
        onDispose {
            resolver.unregisterContentObserver(observer)
            context.unregisterReceiver(powerSave)
            lifecycle.removeObserver(resume)
        }
    }
    return enabled
}

/** A single current tree: no duplicate saveable-state providers, actions or effects. */
@Composable
internal fun PageTransitionFrame(routeKey: String, modifier: Modifier = Modifier, animateEntrance: Boolean = true,
    content: @Composable () -> Unit) {
    val allowEntrance = remember(routeKey) { animateEntrance }
    val enabled = rememberPageAnimationsEnabled() && allowEntrance
    val progress = remember(routeKey) { Animatable(if (enabled) 0f else 1f) }
    val travel = with(LocalDensity.current) { PageMotionPolicy.TRAVEL_DP.dp.toPx() }
    LaunchedEffect(routeKey, enabled) {
        if (!enabled) progress.snapTo(1f)
        else progress.animateTo(1f, tween(PageMotionPolicy.durationMillis(true), easing = PageEnterEasing))
    }
    Box(modifier.fillMaxSize().graphicsLayer {
        alpha = if (enabled) .86f + .14f * progress.value else 1f
        translationY = if (enabled) (1f - progress.value) * travel else 0f
    }) { content() }
}
