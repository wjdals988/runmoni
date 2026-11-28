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
    val OnPrimarySoft: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
    val Navy: Color @Composable get() = Ink
    val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val Outline: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Mint: Color @Composable get() = MintWash
    val Green: Color @Composable get() = EnvironmentGood
    val Lime: Color @Composable get() = Mist
    val Coral: Color @Composable get() = LocalRunmoniAccents.current.coral
    val OnDark: Color get() = RunmoniTrack.OnBoard
    val OnDarkMuted: Color get() = RunmoniTrack.Detail
    val DeepGreen: Color @Composable get() = Ocean
    val MintWash: Color @Composable get() = LocalRunmoniAccents.current.mintWash
    val SecondaryMint: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val LimeWash: Color @Composable get() = Chalk
    val CoralWash: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    val CoralInk: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
    val TrackClay: Color @Composable get() = Mist
    val RouteBlue: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val BlueWash: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
    val EnvironmentGood: Color @Composable get() = LocalRunmoniAccents.current.good
    val EnvironmentGoodWash: Color @Composable get() = LocalRunmoniAccents.current.goodWash
    val EnvironmentCaution: Color @Composable get() = LocalRunmoniAccents.current.caution
    val EnvironmentCautionWash: Color @Composable get() = LocalRunmoniAccents.current.cautionWash
    val EnvironmentPoor: Color @Composable get() = LocalRunmoniAccents.current.poor
    val EnvironmentPoorWash: Color @Composable get() = LocalRunmoniAccents.current.poorWash
    val IsDark: Boolean @Composable get() = LocalRunmoniAccents.current.dark
}

/** Both themes use the same high-contrast running surface for a quick glance outside. */
internal object RunmoniTrack {
    val Board = Color(0xFF172824)
    val OnBoard = Color(0xFFF2FCF5)
    val Detail = Color(0xFFC1D5CB)
    val Highlight = Color(0xFFD4F781)
    val Recovery = Color(0xFFA6DDCE)
    val Attention = Color(0xFFF3CC7C)
    val Stage: Color @Composable get() = if (RunmoniColors.IsDark) Color(0xFF2A463C) else Color(0xFFDBEBE4)
}

private data class RunmoniAccents(
    val dark: Boolean,
    val mintWash: Color,
    val coral: Color,
    val good: Color,
    val goodWash: Color,
    val caution: Color,
    val cautionWash: Color,
    val poor: Color,
    val poorWash: Color,
)

private val DayAccents = RunmoniAccents(
    dark = false,
    mintWash = Color(0xFFE8EFE6),
    coral = Color(0xFF9D3E36),
    good = Color(0xFF27664C),
    goodWash = Color(0xFFE4F1E8),
    caution = Color(0xFF775016),
    cautionWash = Color(0xFFFAEED1),
    poor = Color(0xFF9D3E36),
    poorWash = Color(0xFFF9E9E4),
)
private val NightAccents = RunmoniAccents(
    dark = true,
    mintWash = Color(0xFF2B3731),
    coral = Color(0xFFFFB4A5),
    good = Color(0xFF9EDEBB),
    goodWash = Color(0xFF223C30),
    caution = Color(0xFFECCC80),
    cautionWash = Color(0xFF443921),
    poor = Color(0xFFFFB4A5),
    poorWash = Color(0xFF48302B),
)
private val LocalRunmoniAccents = staticCompositionLocalOf { DayAccents }

private val DayColors = lightColorScheme(
    primary = Color(0xFF23533D), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEADE), onPrimaryContainer = Color(0xFF173928),
    secondary = Color(0xFF535D44), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7EFDF), onSecondaryContainer = Color(0xFF323C29),
    tertiary = Color(0xFF285E76), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3EFF2), onTertiaryContainer = Color(0xFF18485D),
    background = Color(0xFFF1F4F0), onBackground = Color(0xFF1C2A23),
    surface = Color(0xFFFDFEFA), onSurface = Color(0xFF1C2A23),
    surfaceBright = Color(0xFFFDFEFA), surfaceDim = Color(0xFFDDE6DF),
    surfaceContainerLowest = Color(0xFFFDFEFA), surfaceContainerLow = Color(0xFFF5F7F2),
    surfaceContainer = Color(0xFFEBF0EA), surfaceContainerHigh = Color(0xFFE6ECE5),
    surfaceContainerHighest = Color(0xFFDDE6DF), surfaceVariant = Color(0xFFDDE6DF),
    onSurfaceVariant = Color(0xFF51635A), outline = Color(0xFF687A70), outlineVariant = Color(0xFFC9D5CC),
    surfaceTint = Color(0xFF23533D), inverseSurface = Color(0xFF25352D),
    inverseOnSurface = Color(0xFFEAF4EB), inversePrimary = Color(0xFFA6DAB8),
    error = DayAccents.poor, onError = Color.White,
    errorContainer = DayAccents.poorWash, onErrorContainer = DayAccents.poor,
)
private val NightColors = darkColorScheme(
    primary = Color(0xFFA6DAB8), onPrimary = Color(0xFF143423),
    primaryContainer = Color(0xFF294538), onPrimaryContainer = Color(0xFFD6EFDC),
    secondary = Color(0xFFC3CFAD), onSecondary = Color(0xFF2A3425),
    secondaryContainer = Color(0xFF354132), onSecondaryContainer = Color(0xFFE0EACC),
    tertiary = Color(0xFFA0CFE0), onTertiary = Color(0xFF163644),
    tertiaryContainer = Color(0xFF233C46), onTertiaryContainer = Color(0xFFC8E7F3),
    background = Color(0xFF141B19), onBackground = Color(0xFFEAF4EB),
    surface = Color(0xFF1D2723), onSurface = Color(0xFFEAF4EB),
    surfaceBright = Color(0xFF344039), surfaceDim = Color(0xFF141B19),
    surfaceContainerLowest = Color(0xFF101715), surfaceContainerLow = Color(0xFF18211D),
    surfaceContainer = Color(0xFF1D2723), surfaceContainerHigh = Color(0xFF27322D),
    surfaceContainerHighest = Color(0xFF344039), surfaceVariant = Color(0xFF344039),
    onSurfaceVariant = Color(0xFFB0C1B7), outline = Color(0xFF83998D), outlineVariant = Color(0xFF42554C),
    surfaceTint = Color(0xFFA6DAB8), inverseSurface = Color(0xFFEAF4EB),
    inverseOnSurface = Color(0xFF1C2A23), inversePrimary = Color(0xFF23533D),
    error = NightAccents.poor, onError = Color(0xFF4F251D),
    errorContainer = NightAccents.poorWash, onErrorContainer = NightAccents.poor,
)

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
    val SectionGap = 24.dp
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
    val scheme = if (dark) NightColors else DayColors
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
    val motionEnabled = rememberPageAnimationsEnabled()
    CompositionLocalProvider(LocalRunmoniAccents provides if (dark) NightAccents else DayAccents,
        LocalRunmoniMotionEnabled provides motionEnabled) {
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
