package com.runmoni.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.data.RunningWeatherRepository
import com.runmoni.app.domain.RunningWeatherAdvice
import com.runmoni.app.domain.RunningWeatherGuidance
import com.runmoni.app.domain.RunningWeatherState
import com.runmoni.app.domain.RunningEnvironmentScore
import com.runmoni.app.domain.RunningEnvironmentAssessment
import com.runmoni.app.domain.RunningEnvironmentBand
import com.runmoni.app.domain.WeatherCaution
import com.runmoni.app.domain.WeatherPhase
import com.runmoni.app.domain.nearbyCoordinates
import com.runmoni.app.domain.millisUntilNextHomeMinute
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Visible-screen clock only. Refresh immediately after resume and a system time/zone change. */
@Composable
internal fun rememberHomeClock(): Long {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var clockRevision by remember { mutableIntStateOf(0) }
    // Keep receiver ownership inside STARTED and on Android's main dispatcher.
    // Cancellation can originate from a different dispatcher in UI tests.
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                var registered = false
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        now = System.currentTimeMillis()
                        clockRevision += 1
                    }
                }
                try {
                    val filter = IntentFilter().apply {
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                        addAction(Intent.ACTION_DATE_CHANGED)
                    }
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    registered = true
                    now = System.currentTimeMillis()
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    if (registered) runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }
    }
    LaunchedEffect(lifecycle, clockRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                now = System.currentTimeMillis()
                delay(millisUntilNextHomeMinute(now))
            }
        }
    }
    return now
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeDateTime(nowMillis: Long, modifier: Modifier = Modifier, temperatureC: Double? = null) {
    val time = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
    FlowRow(modifier.testTag("home_date_time"), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(time.format(DateTimeFormatter.ofPattern("M월 d일 E요일", Locale.KOREAN)),
            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        Text(time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
            color = RunmoniColors.RouteBlue, modifier = Modifier.testTag("home_current_time"))
        if (temperatureC != null && temperatureC.isFinite()) Text(String.format(Locale.KOREAN, "%.0f°C", temperatureC),
            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary,
            modifier = Modifier.testTag("home_header_temperature"))
    }
}

/** Standalone composition kept for isolated state/error verification. The home
 * hero uses the same values beside the avatar and the full-width details below
 * its primary action; neither surface starts another location request. */
@Composable
internal fun HomeConditions(state: RunningWeatherState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberHomeClock()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeWeatherSummary(state, now)
        HomeWeatherDetails(state, now, onRefresh)
    }
}

/** Essential values and clothing fit beside the avatar without fixed line caps. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeWeatherSummary(state: RunningWeatherState, nowMillis: Long, modifier: Modifier = Modifier) {
    val snapshot = state.snapshot
    val advice = weatherAdvice(state, nowMillis)
    Column(modifier.fillMaxWidth().testTag("home_weather_summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshot != null) {
            val area = state.area
            val areaTitle = if (state.phase == WeatherPhase.READY && area?.isRecent(nowMillis) == true) "현재 위치" else "예보 위치"
            Text("$areaTitle · ${area?.name?.let { "$it 부근" } ?: snapshot.location.nearbyCoordinates()}",
                style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("home_current_area"))
            Text(String.format(Locale.KOREAN, "%.0f°C", snapshot.temperatureC),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                color = RunmoniColors.Primary, modifier = Modifier.testTag("home_weather_temperature"))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(String.format(Locale.KOREAN, "바람 %.1f m/s", snapshot.windMetersPerSecond),
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                    modifier = Modifier.testTag("home_weather_wind"))
                snapshot.precipitationNextHourMm?.let { rain ->
                    Text(String.format(Locale.KOREAN, "1시간 강수 %.1f mm", rain), style = MaterialTheme.typography.bodySmall,
                        color = RunmoniColors.Muted, modifier = Modifier.testTag("home_weather_rain"))
                }
            }
            if (advice != null) Text(advice.attire, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Navy,
                modifier = Modifier.testTag("home_attire_guidance"))
            val forecast = Instant.ofEpochMilli(snapshot.forecastAtMillis).atZone(ZoneId.systemDefault())
            val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            val format = DateTimeFormatter.ofPattern(if (forecast.toLocalDate() == today) "HH:mm" else "M/d HH:mm", Locale.KOREAN)
            Text("${forecast.format(format)} 예보" + if (!snapshot.isCurrent(nowMillis)) " · 이전 정보" else "",
                style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("home_weather_forecast"))
        } else WeatherStatus(state, nowMillis)
    }
}

/** Longer warnings and 48dp controls retain the full page width, rather than
 * forcing a provider button or safety guidance into the avatar's narrow column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeWeatherDetails(state: RunningWeatherState, nowMillis: Long, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val snapshot = state.snapshot
    val advice = weatherAdvice(state, nowMillis)
    val assessment = RunningEnvironmentScore.from(state, nowMillis)
    val loading = state.phase == WeatherPhase.LOCATING || state.phase == WeatherPhase.LOADING
    Column(modifier.fillMaxWidth().testTag("home_weather"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshot != null) WeatherStatus(state, nowMillis)
        if (assessment != null) {
            RunningEnvironmentPanel(assessment, advice?.detail)
        } else if (advice != null) {
            val caution = advice.caution == WeatherCaution.HIGH
            Surface(color = if (caution) RunmoniColors.CoralWash else RunmoniColors.BlueWash,
                shape = RunmoniShapes.Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(if (caution) Icons.Outlined.WarningAmber else Icons.Outlined.DirectionsRun,
                        null, Modifier.size(24.dp),
                        tint = if (caution) RunmoniColors.CoralInk else RunmoniColors.RouteBlue)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(advice.headline, style = MaterialTheme.typography.titleSmall,
                            color = if (caution) RunmoniColors.CoralInk else RunmoniColors.Navy,
                            modifier = Modifier.testTag("home_running_guidance"))
                        advice.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted) }
                        Text("환경 지수에 필요한 예보가 부족해요.", style = MaterialTheme.typography.bodySmall,
                            color = RunmoniColors.Muted, modifier = Modifier.testTag("home_environment_incomplete"))
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRefresh, enabled = !loading, shape = RunmoniShapes.Action,
                modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_home_weather")) {
                Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp))
                Text(if (loading) "확인 중…" else if (snapshot == null) "현재 위치 날씨 보기" else "날씨 다시 확인",
                    modifier = Modifier.padding(start = 8.dp))
            }
            if (snapshot != null) TextButton(onClick = { uri.openUri(RunningWeatherRepository.LICENSE_URL) },
                shape = RunmoniShapes.Action, modifier = Modifier.heightIn(min = 48.dp).testTag("home_weather_source")) {
                Text("예보: ${RunningWeatherRepository.SOURCE_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (snapshot != null || state.phase == WeatherPhase.IDLE || state.phase == WeatherPhase.PERMISSION_REQUIRED) {
            Text(if (snapshot == null) "약 1km 단위의 위치로 예보를 확인해요. 러너 위치 공유와는 별개예요."
                else "기온·바람·강수 기준 안내예요. 미세먼지·노면·컨디션은 따로 살펴주세요.",
                style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        }
    }
}

/** A single, accessible departure cue. The scale is deliberately not a health
 * gauge; its rules expand in the page and never cover the primary run action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunningEnvironmentPanel(assessment: RunningEnvironmentAssessment, detail: String?) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val good = RunmoniColors.EnvironmentGood
    val caution = RunmoniColors.EnvironmentCaution
    val poor = RunmoniColors.EnvironmentPoor
    val tint = when (assessment.band) {
        RunningEnvironmentBand.GOOD -> good
        RunningEnvironmentBand.CAUTION -> caution
        RunningEnvironmentBand.POOR -> poor
    }
    val wash = when (assessment.band) {
        RunningEnvironmentBand.GOOD -> RunmoniColors.EnvironmentGoodWash
        RunningEnvironmentBand.CAUTION -> RunmoniColors.EnvironmentCautionWash
        RunningEnvironmentBand.POOR -> RunmoniColors.EnvironmentPoorWash
    }
    val paper = RunmoniColors.Paper
    Surface(color = wash, shape = RunmoniShapes.Surface, modifier = Modifier.testTag("home_environment_panel")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (assessment.band == RunningEnvironmentBand.GOOD) Icons.Outlined.DirectionsRun
                    else Icons.Outlined.WarningAmber, null, Modifier.size(24.dp), tint = tint)
                Text("러닝 환경", style = MaterialTheme.typography.titleSmall, color = RunmoniColors.Navy)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${assessment.score}점 / 100", color = tint,
                    style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag("home_environment_score"))
                Text(assessment.band.label, color = tint, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("home_environment_band"))
            }
            Canvas(Modifier.fillMaxWidth().height(24.dp).testTag("home_environment_scale").semantics {
                contentDescription = "러닝 환경 척도: ${assessment.band.label}"
                progressBarRangeInfo = ProgressBarRangeInfo(assessment.score.toFloat(), 0f..100f)
            }) {
                val gap = 4.dp.toPx()
                val trackHeight = 8.dp.toPx()
                val trackY = (size.height - trackHeight) / 2
                val available = (size.width - gap * 2).coerceAtLeast(0f)
                val ranges = listOf(0.40f to poor, 0.35f to caution, 0.25f to good)
                var x = 0f
                ranges.forEach { (portion, color) ->
                    val width = available * portion
                    drawRoundRect(color.copy(alpha = .38f), Offset(x, trackY), Size(width, trackHeight),
                        CornerRadius(2.dp.toPx()))
                    x += width + gap
                }
                val radius = 6.dp.toPx().coerceAtMost(size.width / 2)
                val precedingGaps = when {
                    assessment.score < 40 -> 0
                    assessment.score < 75 -> 1
                    else -> 2
                }
                val markerX = available * assessment.score / 100f + gap * precedingGaps
                val center = Offset(markerX.coerceIn(radius, size.width - radius), size.height / 2)
                drawCircle(paper, radius, center)
                drawCircle(tint, radius, center, style = Stroke(3.dp.toPx()))
            }
            Text(assessment.headline, style = MaterialTheme.typography.titleSmall,
                color = RunmoniColors.Navy, modifier = Modifier.testTag("home_running_guidance"))
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted) }
            TextButton(onClick = { expanded = !expanded }, shape = RunmoniShapes.Action,
                modifier = Modifier.heightIn(min = 48.dp).testTag("home_environment_criteria_toggle")) {
                Text(if (expanded) "산정 기준 접기" else "산정 기준 보기")
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    Modifier.padding(start = 8.dp).size(20.dp))
            }
            if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("home_environment_criteria")) {
                Text("런모니 자체 예보 기준\n75–100 좋음 / 40–74 주의 / 0–39 실내 권장",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Navy)
                Text("기온 8–20°C 기준, 바람 3m/s 초과·강수는 감점해요.",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                Text("뇌우 등 극단적인 예보는 25점 이하예요.",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                Text(if (assessment.humidityIncluded) "더운 날 습도도 반영해요." else "습도 정보는 없어 제외했어요.",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                Text("건강·안전 점수가 아니에요.\n미세먼지·노면·몸 상태는 따로 살펴요.",
                    style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
            }
        }
    }
}

private fun weatherAdvice(state: RunningWeatherState, nowMillis: Long): RunningWeatherAdvice? {
    val snapshot = state.snapshot ?: return null
    return if (state.phase == WeatherPhase.READY && snapshot.isCurrent(nowMillis))
        RunningWeatherGuidance.from(snapshot, nowMillis) else null
}

@Composable
private fun WeatherStatus(state: RunningWeatherState, nowMillis: Long) {
    val snapshot = state.snapshot
    val status = when (state.phase) {
        WeatherPhase.IDLE -> "지금 달릴 날씨와 옷차림을 확인해 보세요."
        WeatherPhase.LOCATING -> "현재 위치를 확인하고 있어요…"
        WeatherPhase.LOADING -> "근처 날씨를 확인하고 있어요…"
        WeatherPhase.PERMISSION_REQUIRED -> state.message ?: "위치 권한을 허용하면 근처 날씨를 볼 수 있어요."
        WeatherPhase.LOCATION_DISABLED -> state.message ?: "기기의 위치를 켜고 다시 확인해 주세요."
        WeatherPhase.UNAVAILABLE -> if (snapshot == null) state.message ?: "날씨를 불러오지 못했어요."
            else "갱신하지 못했어요. 표시된 이전 예보와 현재 상황을 함께 확인해 주세요."
        WeatherPhase.READY -> if (snapshot?.isCurrent(nowMillis) != true) "이전 예보예요. 현재 날씨를 다시 확인해 주세요." else null
    }
    if (status != null) {
        val failed = state.phase in setOf(WeatherPhase.PERMISSION_REQUIRED, WeatherPhase.LOCATION_DISABLED, WeatherPhase.UNAVAILABLE)
        Text(status, style = MaterialTheme.typography.bodyMedium,
            color = if (failed) RunmoniColors.CoralInk else RunmoniColors.Muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("home_weather_status"))
    }
}
