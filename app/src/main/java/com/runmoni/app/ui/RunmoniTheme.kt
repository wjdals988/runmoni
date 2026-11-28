package com.runmoni.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Semantic UI roles. The palette is scoped to this composition, never global state.
 * Capture a role before entering Canvas, remember or native callbacks. */
internal object RunmoniColors {
    val Chalk: Color @Composable get() = MaterialTheme.colorScheme.background
    val Paper: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Ink: Color @Composable get() = MaterialTheme.colorScheme.onBackground
    val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Ocean: Color @Composable get() = MaterialTheme.colorScheme.primary
    val Mist: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Cream: Color @Composable get() = Chalk
    val Primary: Color @Composable get() = Ocean
    val OnPrimary: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val PrimarySoft: Color @Composable get() = Mist
    val Navy: Color @Composable get() = Ink
    val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val Outline: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Mint: Color @Composable get() = Mist
    val Green: Color @Composable get() = Ocean
    val Lime: Color @Composable get() = Mist
    val Coral: Color @Composable get() = LocalRunmoniAccents.current.coral
    val OnDark = Color.White
    val OnDarkMuted = Color(0xFFE7EFF1)
    val DeepGreen: Color @Composable get() = Ocean
    val MintWash: Color @Composable get() = Mist
    val SecondaryMint: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val LimeWash: Color @Composable get() = Chalk
    val CoralWash: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    val CoralInk: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
    val TrackClay: Color @Composable get() = Mist
    val RouteBlue: Color @Composable get() = Ocean
    val BlueWash: Color @Composable get() = Mist
    val EnvironmentGood: Color @Composable get() = LocalRunmoniAccents.current.good
    val EnvironmentGoodWash: Color @Composable get() = LocalRunmoniAccents.current.goodWash
    val EnvironmentCaution: Color @Composable get() = LocalRunmoniAccents.current.caution
    val EnvironmentCautionWash: Color @Composable get() = LocalRunmoniAccents.current.cautionWash
    val EnvironmentPoor: Color @Composable get() = LocalRunmoniAccents.current.poor
    val EnvironmentPoorWash: Color @Composable get() = LocalRunmoniAccents.current.poorWash
    val IsDark: Boolean @Composable get() = LocalRunmoniAccents.current.dark
}

/** Shared finish-line colors for the running display and the Minimi fitting stage. */
internal object RunmoniTrack {
    val Board = Color(0xFF103338)
    val OnBoard = Color(0xFFF2FCF5)
    val Detail = Color(0xFFBCD9D8)
    val Highlight = Color(0xFFD4F781)
    val Stage: Color @Composable get() = if (RunmoniColors.IsDark) Color(0xFF213D3D) else Color(0xFFDCEEE7)
}

private data class RunmoniAccents(val dark: Boolean, val coral: Color, val good: Color, val goodWash: Color,
    val caution: Color, val cautionWash: Color, val poor: Color, val poorWash: Color)
private val DayAccents = RunmoniAccents(false, Color(0xFFF16D55), Color(0xFF246447), Color(0xFFE9F3EC),
    Color(0xFF805000), Color(0xFFFFF1D5), Color(0xFFA32F31), Color(0xFFFFE9E7))
private val NightAccents = RunmoniAccents(true, Color(0xFFF3AA99), Color(0xFF8CD5AF), Color(0xFF223E33),
    Color(0xFFE8C56C), Color(0xFF40391F), Color(0xFFFFB0AC), Color(0xFF482E30))
private val LocalRunmoniAccents = staticCompositionLocalOf { DayAccents }

/** Shared geometry: readable controls and contained surfaces, without pill chrome. */
internal object RunmoniShapes {
    val Action = RoundedCornerShape(8.dp)
    val Input = RoundedCornerShape(8.dp)
    val Surface = RoundedCornerShape(12.dp)
    val Status = RoundedCornerShape(4.dp)
    val Dialog = RoundedCornerShape(16.dp)
}

internal object RunmoniLayout {
    val ContentWidth = 640.dp
    val DialogWidth = 560.dp
    val Gutter = 24.dp
    val NarrowGutter = 20.dp
    val WideGutter = 32.dp
    val PageGap = 32.dp
    val ContentGap = 16.dp
    val RowGap = 16.dp
    val TopInset = 24.dp
    val TightGap = 8.dp
    val ActionHeight = 56.dp
}

private val AlignedText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

@Composable
internal fun RunmoniTheme(content: @Composable () -> Unit) = RunmoniTheme(darkTheme = null, content = content)

@Composable
internal fun RunmoniTheme(darkTheme: Boolean?, content: @Composable () -> Unit) {
    val mode = rememberAppearanceMode()
    val dark = darkTheme ?: mode.isDark(isSystemInDarkTheme())
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF9BCDD1), onPrimary = Color(0xFF17393F),
        primaryContainer = Color(0xFF29454C), onPrimaryContainer = Color(0xFFD3EAED),
        secondary = Color(0xFF9BCDD1), onSecondary = Color(0xFF17393F),
        secondaryContainer = Color(0xFF314C52), onSecondaryContainer = Color(0xFFD3EAED),
        tertiary = Color(0xFF9BCDD1), onTertiary = Color(0xFF17393F),
        tertiaryContainer = Color(0xFF29454C), onTertiaryContainer = Color(0xFFD3EAED),
        background = Color(0xFF142128), onBackground = Color(0xFFE2ECEF),
        surface = Color(0xFF1D2E36), onSurface = Color(0xFFE2ECEF),
        surfaceBright = Color(0xFF29404A), surfaceDim = Color(0xFF142128),
        surfaceContainerLowest = Color(0xFF142128), surfaceContainerLow = Color(0xFF192830),
        surfaceContainer = Color(0xFF1D2E36), surfaceContainerHigh = Color(0xFF233740),
        surfaceContainerHighest = Color(0xFF29404A), surfaceVariant = Color(0xFF334A54),
        onSurfaceVariant = Color(0xFFADBEC5), outline = Color(0xFF8399A3), outlineVariant = Color(0xFF3B515B),
        surfaceTint = Color(0xFF9BCDD1), inverseSurface = Color(0xFFE2ECEF),
        inverseOnSurface = Color(0xFF24343D), inversePrimary = Color(0xFF356F78),
        error = Color(0xFFFFB4A8), onError = Color(0xFF57221E),
        errorContainer = Color(0xFF482E30), onErrorContainer = Color(0xFFFFB4A8),
    ) else lightColorScheme(
        primary = Color(0xFF356F78), onPrimary = Color.White,
        primaryContainer = Color(0xFFE7EFF1), onPrimaryContainer = Color(0xFF24343D),
        secondary = Color(0xFF356F78), onSecondary = Color.White,
        secondaryContainer = Color(0xFFD5E4E7), onSecondaryContainer = Color(0xFF24343D),
        tertiary = Color(0xFF356F78), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE7EFF1), onTertiaryContainer = Color(0xFF24343D),
        background = Color(0xFFF7F9FA), onBackground = Color(0xFF24343D),
        surface = Color.White, onSurface = Color(0xFF24343D),
        surfaceBright = Color.White, surfaceDim = Color(0xFFDCE4E7),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F9FA),
        surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color(0xFFE7EFF1), surfaceVariant = Color(0xFFDCE4E7),
        onSurfaceVariant = Color(0xFF5B6D73), outline = Color(0xFF71858D), outlineVariant = Color(0xFFDCE4E7),
        surfaceTint = Color(0xFF356F78), inverseSurface = Color(0xFF24343D),
        inverseOnSurface = Color.White, inversePrimary = Color(0xFFE7EFF1),
        error = Color(0xFFA8322A), onError = Color.White,
        errorContainer = Color(0xFFF8EFED), onErrorContainer = Color(0xFF974A43),
    )
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        context.findAppearanceActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            // In manual dark mode the OS may still have a light appearance.
            // Its automatic navigation contrast scrim must not stay light over
            // our dark canvas; Compose already paints the safe drawing area.
            window.isNavigationBarContrastEnforced = false
            window.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
        }
    }
    CompositionLocalProvider(LocalRunmoniAccents provides if (dark) NightAccents else DayAccents) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(extraSmall = RunmoniShapes.Status, small = RunmoniShapes.Action,
            medium = RunmoniShapes.Surface, large = RunmoniShapes.Dialog, extraLarge = RunmoniShapes.Dialog),
        typography = Typography(
            displayLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp),
            displayMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 56.sp),
            displaySmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
            headlineLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            headlineSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
            labelLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            labelMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}
}

private tailrec fun Context.findAppearanceActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext === this) null else baseContext.findAppearanceActivity()
    else -> null
}
