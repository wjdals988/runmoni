package com.runmoni.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

internal object RunmoniColors {
    val Cream = Color(0xFFF4F6F2)
    val Paper = Color(0xFFFFFFFF)
    val Coral = Color(0xFFF16D55)
    val Primary = Color(0xFF245C45)
    val PrimarySoft = Color(0xFFE6F0E8)
    val Navy = Color(0xFF182A23)
    val Muted = Color(0xFF526158)
    val Line = Color(0xFFD8E1D9)
    val Outline = Color(0xFF748278)
    val Mint = Color(0xFFE6F0E8)
    val Green = Primary
    val Lime = Color(0xFFCEEF92)
    val OnDark = Color(0xFFFFFFFF)
    val OnDarkMuted = Color(0xFFD6E5DA)
}

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
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = Typography(
            displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 64.sp, lineHeight = 72.sp),
            displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 56.sp),
            displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 30.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
            titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 22.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}
