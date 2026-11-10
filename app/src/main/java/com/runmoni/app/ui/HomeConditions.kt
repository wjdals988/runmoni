package com.runmoni.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runmoni.app.data.RunningWeatherRepository
import com.runmoni.app.domain.RunningWeatherGuidance
import com.runmoni.app.domain.RunningWeatherState
import com.runmoni.app.domain.WeatherCaution
import com.runmoni.app.domain.WeatherPhase
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
    DisposableEffect(context, lifecycle) {
        var registered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now = System.currentTimeMillis()
                clockRevision += 1 // Re-align the next minute after a manual time/zone change.
            }
        }
        fun unregister() {
            if (registered) {
                runCatching { context.unregisterReceiver(receiver) }
                registered = false
            }
        }
        fun register() {
            now = System.currentTimeMillis()
            if (!registered) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_DATE_CHANGED)
                }
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                registered = true
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) register()
            if (event == Lifecycle.Event.ON_STOP) unregister()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { lifecycle.removeObserver(observer); unregister() }
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
            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Navy,
            modifier = Modifier.testTag("home_current_time"))
        if (temperatureC != null && temperatureC.isFinite()) Text(String.format(Locale.KOREAN, "%.0f°C", temperatureC),
            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary,
            modifier = Modifier.testTag("home_header_temperature"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeConditions(state: RunningWeatherState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberHomeClock()
    val uri = LocalUriHandler.current
    val snapshot = state.snapshot
    val loading = state.phase == WeatherPhase.LOCATING || state.phase == WeatherPhase.LOADING
    val current = snapshot?.isCurrent(now) == true
    val failed = state.phase in setOf(WeatherPhase.PERMISSION_REQUIRED, WeatherPhase.LOCATION_DISABLED, WeatherPhase.UNAVAILABLE)
    // A previously valid cache remains visible after failure, but no fresh recommendation is claimed.
    val advice = if (current && state.phase == WeatherPhase.READY) snapshot?.let { RunningWeatherGuidance.from(it, now) } else null
    val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
    Column(modifier.fillMaxWidth().testTag("home_weather"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (snapshot != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(String.format(Locale.KOREAN, "%.0f°C", snapshot.temperatureC), style = MaterialTheme.typography.titleLarge,
                    color = RunmoniColors.Primary, modifier = Modifier.testTag("home_weather_temperature"))
                Text(String.format(Locale.KOREAN, "바람 %.1f m/s", snapshot.windMetersPerSecond),
                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                snapshot.precipitationNextHourMm?.let { rain ->
                    Text(String.format(Locale.KOREAN, "1시간 강수 %.1f mm", rain), style = MaterialTheme.typography.bodyMedium,
                        color = RunmoniColors.Muted)
                }
            }
            val forecast = Instant.ofEpochMilli(snapshot.forecastAtMillis).atZone(ZoneId.systemDefault())
            val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
            val forecastTime = forecast.format(if (forecast.toLocalDate() == today) clock else DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN))
            val place = if (state.phase == WeatherPhase.READY) "현재 위치 근처" else "마지막 확인 위치"
            Text("$place · $forecastTime 예보" + if (!current) " · 이전 정보" else "",
                style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        }
        val status = when (state.phase) {
            WeatherPhase.IDLE -> "지금 달릴 날씨와 옷차림을 확인해 보세요."
            WeatherPhase.LOCATING -> "현재 위치를 확인하고 있어요…"
            WeatherPhase.LOADING -> "근처 날씨를 확인하고 있어요…"
            WeatherPhase.PERMISSION_REQUIRED -> state.message ?: "위치 권한을 허용하면 근처 날씨를 볼 수 있어요."
            WeatherPhase.LOCATION_DISABLED -> state.message ?: "기기의 위치를 켜고 다시 확인해 주세요."
            WeatherPhase.UNAVAILABLE -> if (snapshot == null) state.message ?: "날씨를 불러오지 못했어요."
                else "갱신하지 못했어요. 표시된 이전 예보와 현재 상황을 함께 확인해 주세요."
            WeatherPhase.READY -> if (!current) "이전 예보예요. 현재 날씨를 다시 확인해 주세요." else null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium,
            color = if (failed) RunmoniColors.CoralInk else RunmoniColors.Muted,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("home_weather_status"))
        if (advice != null) {
            Text(advice.headline, style = MaterialTheme.typography.titleSmall,
                color = if (advice.caution == WeatherCaution.HIGH) RunmoniColors.CoralInk else RunmoniColors.Navy,
                modifier = Modifier.testTag("home_running_guidance"))
            Text(advice.attire, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                modifier = Modifier.testTag("home_attire_guidance"))
            advice.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRefresh, enabled = !loading, shape = RunmoniShapes.Action,
                modifier = Modifier.heightIn(min = 48.dp).testTag("refresh_home_weather")) {
                Text(if (loading) "확인 중…" else if (snapshot == null) "현재 위치 날씨 보기" else "날씨 다시 확인")
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
