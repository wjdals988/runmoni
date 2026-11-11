package com.runmoni.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*

/** A local device-position label, independent of the map's viewport and public runner sharing. */
@Composable
internal fun CurrentAreaLabel(area: CurrentAreaUi, nowMillis: Long, modifier: Modifier = Modifier) {
    val location = area.location
    val title = if (area.isRecent(nowMillis) && area.phase != CurrentAreaPhase.IDLE) "현재 위치" else "마지막 확인 위치"
    val text = if (location != null) "$title · ${area.name?.let { "$it 부근" } ?: location.nearbyCoordinates()}" else when (area.phase) {
        CurrentAreaPhase.LOCATING, CurrentAreaPhase.RESOLVING -> "현재 위치를 확인하고 있어요…"
        CurrentAreaPhase.PERMISSION_REQUIRED -> "위치 권한을 허용하면 현재 위치를 표시해요."
        CurrentAreaPhase.LOCATION_DISABLED -> "기기의 위치를 켜 주세요."
        CurrentAreaPhase.UNAVAILABLE -> "현재 위치를 찾지 못했어요. 다시 확인해 주세요."
        else -> "내 위치로 이동하면 지역명을 표시해요."
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
            modifier = Modifier.testTag("explore_current_area"))
        if (location != null && area.phase == CurrentAreaPhase.UNAVAILABLE) Text(
            "지역명을 확인하지 못해 근처 좌표로 표시했어요.", style = MaterialTheme.typography.bodySmall,
            color = RunmoniColors.Muted, modifier = Modifier.testTag("area_name_unavailable"))
    }
}
