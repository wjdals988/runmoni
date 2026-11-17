package com.runmoni.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** API29 keeps classic fitting and corrects only occlusion left on the actual host.
 * Some windows expose IME insets while their Compose host remains full height.
 * A correctly resized host has no remaining overlap, so it receives no extra inset.
 * Each page restores its owned flags/mode and removes its geometry observer on exit.
 * API30+ retains the existing Compose inset handling through a null result.
 */
@Composable
internal fun legacyEditablePageWindowPolicy(): WindowInsets? {
    val view = LocalView.current
    val window = remember(view) {
        if (Build.VERSION.SDK_INT == 29) view.context.editablePageHostActivity()?.window else null
    }
    var remainingBottom by remember(view, window) { mutableIntStateOf(0) }
    DisposableEffect(view, window) {
        if (window == null) {
            onDispose { }
        } else {
            val decor = window.decorView
            // WindowCompat API16 implementation owns only these three layout flags on API29.
            val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val originalLayoutFlags = decor.systemUiVisibility and layoutMask
            val originalSoftInputMode = window.attributes.softInputMode
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setSoftInputMode((originalSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            ViewCompat.requestApplyInsets(decor)
            decor.requestLayout()
            val hostLocation = IntArray(2)
            val decorLocation = IntArray(2)
            val updateGeometry = Runnable {
                if (view.isAttachedToWindow && decor.isAttachedToWindow && view.height > 0 && decor.height > 0) {
                    ViewCompat.getRootWindowInsets(decor)?.let { insets ->
                        view.getLocationOnScreen(hostLocation)
                        decor.getLocationOnScreen(decorLocation)
                        // Both locations share screen coordinates. Their difference
                        // cancels a common window pan; this host is outside our padding.
                        val hostBottom = hostLocation[1] - decorLocation[1] + view.height
                        val occupiedBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()).bottom
                        val availableBottom = decor.height - occupiedBottom
                        val overlap = (hostBottom - availableBottom).coerceIn(0, view.height)
                        if (remainingBottom != overlap) remainingBottom = overlap
                    }
                }
            }
            val geometryListener = ViewTreeObserver.OnGlobalLayoutListener { updateGeometry.run() }
            val geometryObserver = view.viewTreeObserver
            geometryObserver.addOnGlobalLayoutListener(geometryListener)
            // Composition can precede the first attached/measured host layout.
            view.post(updateGeometry)
            onDispose {
                view.removeCallbacks(updateGeometry)
                val observer = if (geometryObserver.isAlive) geometryObserver else view.viewTreeObserver
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(geometryListener)
                // Preserve any independent status/navigation appearance change during this page.
                decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or originalLayoutFlags
                window.setSoftInputMode(originalSoftInputMode)
                ViewCompat.requestApplyInsets(decor)
                decor.requestLayout()
            }
        }
    }
    return if (window != null) WindowInsets(0, 0, 0, remainingBottom) else null
}

private fun Context.editablePageHostActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) return null
        current = next
    }
    return current as? Activity
}
