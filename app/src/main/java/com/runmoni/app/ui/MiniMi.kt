package com.runmoni.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A small code-drawn avatar: no downloaded assets, fake people, or tracking data. */
@Composable
internal fun MiniMi(
    modifier: Modifier = Modifier,
    shirtColor: Color = RunmoniColors.Coral,
    accessory: String = "none",
    running: Boolean = false,
    hairVariant: Int = 0,
    bottomVariant: Int = 0,
    shoesVariant: Int = 0,
) {
    Canvas(modifier.semantics { contentDescription = "러닝 미니미" }) {
        val unit = minOf(size.width / 180f, size.height / 190f)
        withTransform({
            translate((size.width - 180f * unit) / 2f, (size.height - 190f * unit) / 2f)
            scale(unit, unit, Offset.Zero)
        }) {
            val ink = RunmoniColors.Navy
            val skin = Color(0xFFF0C2A0)
            val hairColor = when (hairVariant % 4) { 1 -> Color(0xFF795844); 2 -> Color(0xFFD8AC58); 3 -> Color(0xFF75638B); else -> ink }
            val shortsColor = when (bottomVariant % 4) { 1 -> Color(0xFF536E61); 2 -> Color(0xFF7C8FAC); 3 -> Color(0xFFC57E68); else -> ink }
            val shoeColor = when (shoesVariant % 4) { 1 -> Color(0xFFDFBE54); 2 -> RunmoniColors.Coral; 3 -> Color(0xFF83ADD1); else -> Color(0xFF55AA99) }
            drawOval(Color(0x170C2935), Offset(42f, 167f), Size(100f, 15f))
            drawLine(ink, Offset(82f, 122f), Offset(if (running) 59f else 73f, 153f), 15f, StrokeCap.Round)
            drawLine(ink, Offset(102f, 123f), Offset(if (running) 128f else 105f, 153f), 15f, StrokeCap.Round)
            drawLine(shoeColor, Offset(if (running) 48f else 61f, 162f), Offset(if (running) 67f else 83f, 162f), 14f, StrokeCap.Round)
            drawLine(shoeColor, Offset(if (running) 123f else 99f, 162f), Offset(if (running) 143f else 121f, 162f), 14f, StrokeCap.Round)
            drawLine(skin, Offset(68f, 82f), Offset(49f, if (running) 70f else 107f), 13f, StrokeCap.Round)
            drawLine(skin, Offset(113f, 82f), Offset(134f, if (running) 62f else 105f), 13f, StrokeCap.Round)
            drawRoundRect(shirtColor, Offset(65f, 75f), Size(53f, 48f), androidx.compose.ui.geometry.CornerRadius(15f, 15f))
            drawRoundRect(shortsColor, Offset(69f, 113f), Size(45f, 17f), androidx.compose.ui.geometry.CornerRadius(5f, 5f))
            drawCircle(skin, 30f, Offset(91f, 47f))
            val hair = Path().apply {
                moveTo(62f, 40f); cubicTo(54f, 5f, 110f, -1f, 119f, 36f)
                lineTo(103f, 28f); lineTo(86f, 33f); lineTo(75f, 28f); close()
            }
            drawPath(hair, hairColor)
            drawCircle(ink, 2.7f, Offset(81f, 47f))
            drawCircle(ink, 2.7f, Offset(102f, 47f))
            drawLine(ink, Offset(87f, 59f), Offset(95f, 59f), 2.5f, StrokeCap.Round)
            drawCircle(Color(0xFFD99980), 4.3f, Offset(72f, 57f))
            drawCircle(Color(0xFFD99980), 4.3f, Offset(112f, 57f))
            if (accessory == "headband" || accessory.contains("밴드")) {
                drawRoundRect(Color(0xFF8ABFC1), Offset(62f, 32f), Size(58f, 8f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            }
            if (accessory.contains("cap", true) || accessory.contains("모자")) {
                drawArc(shirtColor, 180f, 180f, true, Offset(62f, 9f), Size(57f, 42f))
                drawLine(shirtColor, Offset(96f, 30f), Offset(131f, 30f), 9f, StrokeCap.Round)
            }
            if (accessory == "headphones") {
                drawArc(Color(0xFF91B8CD), 180f, 180f, false, Offset(57f, 14f), Size(69f, 67f), style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
                drawRoundRect(Color(0xFF91B8CD), Offset(54f, 41f), Size(12f, 22f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
                drawRoundRect(Color(0xFF91B8CD), Offset(117f, 41f), Size(12f, 22f), androidx.compose.ui.geometry.CornerRadius(4f, 4f))
            }
            drawCircle(Color.White, 4f, Offset(91f, 93f))
        }
    }
}
