package com.runmoni.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

internal object RunmoniColors {
    // Six base roles, shared with the native widget palette.
    val Chalk = Color(0xFFF7F9FA)
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF24343D)
    val Muted = Color(0xFF5B6D73)
    val Ocean = Color(0xFF356F78)
    val Mist = Color(0xFFE7EFF1)
    // Existing call sites keep their role names; garment palettes are separate.
    val Cream = Chalk
    val Primary = Ocean
    val PrimarySoft = Mist
    val Navy = Ink
    val Line = Color(0xFFDCE4E7)
    val Outline = Color(0xFF71858D)
    val Mint = Mist
    val Green = Ocean
    val Lime = Mist
    val Coral = Color(0xFFF16D55)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Mist
    val DeepGreen = Ocean
    val MintWash = Mist
    val SecondaryMint = Color(0xFFD5E4E7)
    val LimeWash = Chalk
    val CoralWash = Color(0xFFF8EFED)
    val CoralInk = Color(0xFF974A43)
    val TrackClay = Mist
    val RouteBlue = Ocean
    val BlueWash = Mist
}

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
    val ActionHeight = 56.dp
}

private val AlignedText = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

@Composable
internal fun RunmoniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RunmoniColors.Primary,
            onPrimary = Color.White,
            primaryContainer = RunmoniColors.PrimarySoft,
            onPrimaryContainer = RunmoniColors.Navy,
            secondary = RunmoniColors.Green,
            onSecondary = Color.White,
            secondaryContainer = RunmoniColors.Mint,
            onSecondaryContainer = RunmoniColors.Navy,
            tertiary = RunmoniColors.RouteBlue,
            onTertiary = Color.White,
            tertiaryContainer = RunmoniColors.BlueWash,
            onTertiaryContainer = RunmoniColors.Navy,
            background = RunmoniColors.Cream,
            onBackground = RunmoniColors.Navy,
            surface = RunmoniColors.Paper,
            surfaceBright = RunmoniColors.Paper,
            surfaceDim = RunmoniColors.Line,
            surfaceContainerLowest = RunmoniColors.Paper,
            surfaceContainerLow = RunmoniColors.Cream,
            surfaceContainer = RunmoniColors.Paper,
            surfaceContainerHigh = RunmoniColors.Paper,
            surfaceContainerHighest = RunmoniColors.Mint,
            onSurface = RunmoniColors.Navy,
            surfaceVariant = RunmoniColors.Line,
            onSurfaceVariant = RunmoniColors.Muted,
            outline = RunmoniColors.Outline,
            outlineVariant = RunmoniColors.Line,
            surfaceTint = RunmoniColors.Primary,
            inverseSurface = RunmoniColors.Navy,
            inverseOnSurface = RunmoniColors.Paper,
            inversePrimary = RunmoniColors.Lime,
            error = Color(0xFFA8322A),
            onError = Color.White,
            errorContainer = Color(0xFFFFEAE5),
            onErrorContainer = Color(0xFF74221C),
        ),
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
