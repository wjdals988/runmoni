package com.runmoni.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal object RunmoniColors {
    val Cream = Color(0xFFF7F4ED)
    val Paper = Color(0xFFFFFEFA)
    val Coral = Color(0xFFF16D55)
    val CoralInk = Color(0xFFB84A39)
    val CoralSoft = Color(0xFFFFE4DA)
    val Navy = Color(0xFF193340)
    val Muted = Color(0xFF5D6D75)
    val Line = Color(0xFFE5E6DF)
    val Mint = Color(0xFFDDEDE6)
    val Green = Color(0xFF2C7560)
}

@Composable
internal fun RunmoniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = RunmoniColors.CoralInk,
            onPrimary = Color.White,
            primaryContainer = RunmoniColors.CoralSoft,
            onPrimaryContainer = RunmoniColors.Navy,
            secondary = RunmoniColors.Green,
            secondaryContainer = RunmoniColors.Mint,
            background = RunmoniColors.Cream,
            onBackground = RunmoniColors.Navy,
            surface = RunmoniColors.Paper,
            onSurface = RunmoniColors.Navy,
            surfaceVariant = RunmoniColors.Line,
            onSurfaceVariant = RunmoniColors.Muted,
            outline = RunmoniColors.Line,
        ),
        typography = Typography(
            displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp),
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
        ),
        content = content,
    )
}
