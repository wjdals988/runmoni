package com.runmoni.app.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.runmoni.app.data.AppearancePreferences
import com.runmoni.app.domain.AppAppearance

@Composable
internal fun rememberAppearancePreferences(): AppearancePreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppearancePreferences(context) }
}

@Composable
internal fun rememberAppearanceMode(): AppAppearance {
    val store = rememberAppearancePreferences()
    var mode by remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppearancePreferences.KEY || key == null) mode = store.read()
        }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        // Reread after registration so a change between initial read and listener
        // registration cannot leave this route in a stale theme.
        mode = store.read()
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return mode
}

/** Inline settings rows; no modal, extra profile or additional account state. */
@Composable
internal fun AppearanceSettingsSection(modifier: Modifier = Modifier) {
    val store = rememberAppearancePreferences()
    val selected = rememberAppearanceMode()
    Column(modifier.fillMaxWidth().testTag("appearance_settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("화면 테마", style = MaterialTheme.typography.titleLarge)
        Text("이 기기에 저장돼요", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            AppAppearance.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { store.write(mode) })
                    .testTag("appearance_${mode.wireValue}").padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(when (mode) {
                        AppAppearance.SYSTEM -> Icons.Outlined.BrightnessAuto
                        AppAppearance.LIGHT -> Icons.Outlined.LightMode
                        AppAppearance.DARK -> Icons.Outlined.DarkMode
                    }, null, Modifier.size(24.dp), tint = if (mode == selected) RunmoniColors.Primary else RunmoniColors.Muted)
                    Text(mode.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    RadioButton(selected = mode == selected, onClick = null)
                }
            }
        }
    }
}
