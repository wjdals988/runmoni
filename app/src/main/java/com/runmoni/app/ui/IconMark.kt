package com.runmoni.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.runmoni.app.R

/** App identity with the launcher mark sized to its visible tile. */
@Composable
internal fun IconMark(size: Dp = 48.dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(painterResource(R.drawable.ic_runmoni), contentDescription, modifier.size(size))
}
