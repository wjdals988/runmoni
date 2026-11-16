package com.runmoni.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.runmoni.app.R

/** App identity in entry/information pages; launcher and splash use the same vector.
 * The108dp adaptive layer has18dp overscan per side. Cropping that overscan here
 * keeps the mark's visible size consistent instead of drawing a tiny padded icon.
 */
@Composable
internal fun IconMark(size: Dp = 48.dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * (2f / 9f)))
        .background(colorResource(R.color.ic_launcher_background)), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription,
            Modifier.requiredSize(size * 1.5f))
    }
}
