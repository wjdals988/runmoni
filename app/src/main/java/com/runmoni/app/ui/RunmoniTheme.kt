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
    val Cream = Color(0xFFF7F7F2)
    val Paper = Color(0xFFFFFFFF)
    val Coral = Color(0xFFF16D55)
    val Primary = Color(0xFF205641)
    val PrimarySoft = Color(0xFFE9EFDF)
    val Navy = Color(0xFF192D25)
    val Muted = Color(0xFF556158)
    val Line = Color(0xFFDFE3DA)
    val Outline = Color(0xFF748071)
    val Mint = Color(0xFFE9EFDF)
    val Green = Primary
    val Lime = Color(0xFFD7F58A)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xFFD6E5DA)
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
            tertiary = RunmoniColors.Primary,
            onTertiary = Color.White,
            tertiaryContainer = RunmoniColors.Mint,
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
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)),
        typography = Typography(
            displayLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp),
            displayMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 56.sp),
            displaySmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
            headlineLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            headlineSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
            titleMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 20.sp),
            labelLarge = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
            labelMedium = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
            labelSmall = AlignedText.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}
