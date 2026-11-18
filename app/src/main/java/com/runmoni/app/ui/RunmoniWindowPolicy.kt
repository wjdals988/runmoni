package com.runmoni.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** API29 keeps classic fitting and constrains the Android host before Compose measures.
 * Some windows expose IME insets while their content frame remains full height.
 * Padding that frame shortens its Compose child as well as the focused Android view,
 * avoiding a second framework pan around an otherwise full-height focus owner.
 * Each page restores its owned padding/flags/mode and removes its observer on exit.
 * API30+ retains the existing Compose inset handling through a null result.
 */
@Composable
internal fun legacyEditablePageWindowPolicy(): WindowInsets? {
    val view = LocalView.current
    val window = remember(view) {
        if (Build.VERSION.SDK_INT == 29) view.context.editablePageHostActivity()?.window else null
    }
    val content = remember(view, window) {
        window?.findViewById<ViewGroup>(android.R.id.content)?.takeIf { candidate ->
            generateSequence(view as View?) { it.parent as? View }.any { it === candidate }
        }
    }
    DisposableEffect(view, window, content) {
        if (window == null || content == null) {
            onDispose { }
        } else {
            val decor = window.decorView
            // WindowCompat API16 implementation owns only these three layout flags on API29.
            val layoutMask = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val originalLayoutFlags = decor.systemUiVisibility and layoutMask
            val originalSoftInputMode = window.attributes.softInputMode
            val originalLeft = content.paddingLeft
            val originalTop = content.paddingTop
            val originalRight = content.paddingRight
            val originalBottom = content.paddingBottom
            val originalRelativePadding = content.isPaddingRelative
            val originalStart = content.paddingStart
            val originalEnd = content.paddingEnd
            WindowCompat.setDecorFitsSystemWindows(window, true)
            window.setSoftInputMode((originalSoftInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            ViewCompat.requestApplyInsets(decor)
            decor.requestLayout()
            val contentLocation = IntArray(2)
            val decorLocation = IntArray(2)
            val updateGeometry = Runnable {
                if (view.isAttachedToWindow && content.isAttachedToWindow && decor.isAttachedToWindow &&
                    content.height > 0 && decor.height > 0) {
                    ViewCompat.getRootWindowInsets(decor)?.let { insets ->
                        content.getLocationOnScreen(contentLocation)
                        decor.getLocationOnScreen(decorLocation)
                        // The frame's outer bounds do not include our child-padding
                        // correction. Never feed the shortened Compose child's height
                        // back into this calculation. Shared window pan cancels here.
                        val baseBottom = contentLocation[1] - decorLocation[1] + content.height - originalBottom
                        val occupiedBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()).bottom
                        val availableBottom = decor.height - occupiedBottom
                        val usableHeight = (content.height - originalTop - originalBottom).coerceAtLeast(0)
                        val overlap = (baseBottom - availableBottom).coerceIn(0, usableHeight)
                        val bottom = originalBottom + overlap
                        if (content.paddingBottom != bottom) {
                            content.setPadding(originalLeft, originalTop, originalRight, bottom)
                        }
                    }
                }
            }
            val geometryListener = ViewTreeObserver.OnGlobalLayoutListener { updateGeometry.run() }
            val geometryObserver = content.viewTreeObserver
            geometryObserver.addOnGlobalLayoutListener(geometryListener)
            // Composition can precede the first attached/measured host layout.
            view.post(updateGeometry)
            onDispose {
                view.removeCallbacks(updateGeometry)
                val observer = if (geometryObserver.isAlive) geometryObserver else content.viewTreeObserver
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(geometryListener)
                if (originalRelativePadding) {
                    content.setPaddingRelative(originalStart, originalTop, originalEnd, originalBottom)
                } else {
                    content.setPadding(originalLeft, originalTop, originalRight, originalBottom)
                }
                // Preserve any independent status/navigation appearance change during this page.
                decor.systemUiVisibility = (decor.systemUiVisibility and layoutMask.inv()) or originalLayoutFlags
                window.setSoftInputMode(originalSoftInputMode)
                ViewCompat.requestApplyInsets(decor)
                decor.requestLayout()
            }
        }
    }
    // The parent now provides the available physical height; Compose must not
    // subtract the same IME/system-bar region for a second time.
    return if (window != null && content != null) WindowInsets(0, 0, 0, 0) else null
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
