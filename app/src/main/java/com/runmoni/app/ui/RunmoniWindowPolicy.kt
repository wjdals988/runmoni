package com.runmoni.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/** API29 editable pages let the framework fit both system bars and the IME.
 * Adding raw Compose IME insets after that fit would subtract the keyboard twice.
 * Each exclusive page restores only the window flags and mode it owns on exit.
 * API30+ retains the existing Compose inset handling.
 */
@Composable
internal fun legacyEditablePageWindowPolicy(): Boolean {
    val view = LocalView.current
    val window = remember(view) {
        if (Build.VERSION.SDK_INT == 29) view.context.editablePageHostActivity()?.window else null
    }
    DisposableEffect(window) {
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
            onDispose {
                // Preserve any independent status/navigation appearance change during this page.
                decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or originalLayoutFlags
                window.setSoftInputMode(originalSoftInputMode)
                ViewCompat.requestApplyInsets(decor)
                decor.requestLayout()
            }
        }
    }
    return window != null
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
