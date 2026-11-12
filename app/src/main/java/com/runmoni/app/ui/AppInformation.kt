@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.runmoni.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.runmoni.app.BuildConfig
import com.runmoni.app.R
import com.runmoni.app.data.AppUpdateRepository
import com.runmoni.app.data.AppUpdateSource
import com.runmoni.app.domain.AppRelease
import com.runmoni.app.domain.bundledAppHistory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.json.JSONArray
import java.util.Locale

data class AppUpdateUiState(
    val release: AppRelease? = null,
    val checkedAtMillis: Long = 0,
    val checking: Boolean = false,
    val error: String? = null,
) {
    val updateAvailable: Boolean get() = release?.isNewerThan(BuildConfig.VERSION_CODE.toLong()) == true
    val description: String get() = when {
        checking -> "업데이트 확인 중"
        updateAvailable && error != null -> "새 버전 있음 · 이전 확인 결과"
        updateAvailable -> "새 버전 ${release!!.version}"
        error != null -> "업데이트 정보를 다시 확인해 주세요"
        release != null && release.versionCode < BuildConfig.VERSION_CODE -> "공개 버전보다 앞선 개발 버전"
        release != null -> "현재 공개 버전"
        else -> "업데이트 내역 · 버전 확인"
    }
}

class AppUpdateController(private val repository: AppUpdateSource, private val scope: CoroutineScope, private val nowMillis: () -> Long = System::currentTimeMillis) {
    private val cached = repository.cached().let { cache ->
        cache.copy(release = cache.release?.takeIf { BuildConfig.VERSION_NAME.contains('-') || !it.version.contains('-') })
    }
    var state by mutableStateOf(AppUpdateUiState(cached.release, cached.checkedAtMillis))
        private set
    private var lastAttempt = 0L

    fun check(force: Boolean = false) {
        val now = nowMillis()
        if (state.checking) return
        if (now - lastAttempt in 0..29_999) {
            if (force) state = state.copy(error = "방금 확인했어요. 30초 뒤에 다시 확인할 수 있어요.")
            return
        }
        if (!force && state.release != null && now - state.checkedAtMillis in 0 until 6 * 60 * 60 * 1000L) return
        lastAttempt = now
        state = state.copy(checking = true, error = null)
        scope.launch {
            try {
                val result = repository.check(BuildConfig.VERSION_NAME.contains("-"))
                state = AppUpdateUiState(result.release, result.checkedAtMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = state.copy(error = "업데이트를 확인하지 못했어요. 연결 후 다시 확인해 주세요.")
            } finally {
                state = state.copy(checking = false)
            }
        }
    }
}

/** Production entry point only; UI fixtures receive an inert AppUpdateUiState. */
@Composable
fun rememberAppUpdates(): AppUpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) { AppUpdateController(AppUpdateRepository(context.applicationContext), scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.check() }
    return controller
}

@Composable
internal fun AppVersionShortcut(updates: AppUpdateUiState, onClick: () -> Unit) {
    TextButton(shape = RunmoniShapes.Action, onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag("top_app_version").semantics {
            if (updates.updateAvailable) stateDescription = "새 버전 업데이트 가능"
        }) {
        // Reserve layout space for the dot. A floating badge on the text can
        // overlap the last glyph or escape into the adjacent settings action.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                maxLines = 1, softWrap = false, modifier = Modifier.testTag("top_app_version_label"))
            if (updates.updateAvailable) Badge(
                Modifier.size(6.dp).testTag("top_app_version_red_dot"),
                containerColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun AppVersionCard(updates: AppUpdateUiState, onClick: () -> Unit, tag: String = "app_version") {
    Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "버전 및 업데이트 정보 보기", onClick = onClick).testTag(tag).heightIn(min = 72.dp).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(RunmoniColors.BlueWash, RunmoniShapes.Action), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(24.dp), tint = RunmoniColors.RouteBlue)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("버전 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(updates.description, style = MaterialTheme.typography.bodyMedium, color = if (updates.updateAvailable) RunmoniColors.Primary else RunmoniColors.Muted)
            }
            if (updates.updateAvailable) Badge(Modifier.size(6.dp).testTag("${tag}_red_dot")
                .semantics { contentDescription = "새 버전 업데이트 가능" }, containerColor = MaterialTheme.colorScheme.error)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(24.dp))
        }
    }
}

@Composable
internal fun AppInformationDialog(updates: AppUpdateUiState, onCheck: () -> Unit, onDismiss: () -> Unit, promptUpdateInitially: Boolean = false, onExternalLink: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(false) }
    var licenses by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf(promptUpdateInitially && updates.updateAvailable) }
    var linkError by remember { mutableStateOf<String?>(null) }
    val infoScroll = rememberScrollState()
    val updateRelease = updates.release?.takeIf { it.isNewerThan(BuildConfig.VERSION_CODE.toLong()) }
    val showingUpdatePrompt = updatePrompt && updateRelease != null
    val open: (String) -> Unit = { url ->
        if (onExternalLink != null) onExternalLink(url)
        else if (!openAppLink(context, url)) linkError = "링크를 열 앱이 없어요. 브라우저를 사용할 수 있는지 확인해 주세요."
    }
    if (!history && !licenses && !showingUpdatePrompt && linkError == null) FormDialog("앱 정보", onDismiss, dismissLabel = null, closeTag = "close_app_information", scrollState = infoScroll) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(64.dp), shape = RunmoniShapes.Surface, color = colorResource(R.color.ic_launcher_background)) {
                Box(contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(96.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("런모니", style = MaterialTheme.typography.headlineSmall)
                if (BuildConfig.VERSION_NAME.contains('-')) Text("개발 프리뷰", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                Text("러닝 기록과 나의 미니미", style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
            }
        }
        AppVersionCard(updates, onClick = { if (updates.updateAvailable) updatePrompt = true else history = true }, tag = "about_app_version")
        Text("빌드 ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
        if (updates.error != null) Text(updates.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        AdaptiveActions(
            first = { modifier -> Button(shape = RunmoniShapes.Action, onClick = { if (updates.updateAvailable) updatePrompt = true else onCheck() }, enabled = !updates.checking, modifier = modifier.heightIn(min = 48.dp).testTag("check_app_update")) { Text(if (updates.checking) "확인 중" else if (updates.updateAvailable) "새 버전 다운로드" else "업데이트 확인") } },
            second = { modifier -> OutlinedButton(shape = RunmoniShapes.Action, onClick = { history = true }, modifier = modifier.heightIn(min = 48.dp).testTag("open_version_history")) { Text("업데이트 내역") } },
        )
        if (updates.updateAvailable) TextButton(shape = RunmoniShapes.Action, onClick = onCheck, enabled = !updates.checking, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("recheck_app_update")) { Text("버전 정보 다시 확인") }
        HorizontalDivider(color = RunmoniColors.Line)
        Text("앱 지원", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        AppLinkRow(Icons.Outlined.Dashboard, "런모니 웹사이트", "앱 소개와 다운로드", "link_dashboard") { open("https://coldbrewventi.vercel.app/projects/runmoni") }
        AppLinkRow(Icons.Outlined.Code, "GitHub", "소스 코드와 공개 릴리스", "link_github") { open(AppRelease.REPOSITORY) }
        AppLinkRow(Icons.Outlined.Description, "오픈소스 라이선스", "라이브러리와 SDK 고지", "open_licenses", external = false) { licenses = true }
        AppLinkRow(Icons.Outlined.BugReport, "문제 신고", "GitHub에서 작성", "link_issue") {
            val body = "앱 버전: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\nAndroid API: ${android.os.Build.VERSION.SDK_INT}\n\n문제 상황:\n\n재현 순서:\n1. \n\n기대 결과:\n\n실제 결과:\n\n개인 좌표·비밀번호·인증번호·토큰은 작성하지 마세요."
            open("${AppRelease.REPOSITORY}/issues/new?title=${Uri.encode("[Android] 문제 신고")}&body=${Uri.encode(body)}")
        }
    }
    if (history) AppVersionHistoryDialog(updates) { history = false }
    else if (licenses) OpenSourceLicensesDialog { licenses = false }
    else if (showingUpdatePrompt) updateRelease?.let { release ->
        DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { updatePrompt = false }, title = { Text("업데이트할까요?") },
            text = { DialogMessage("현재 ${BuildConfig.VERSION_NAME}\n새 버전 ${release.version}\nAPK ${String.format(Locale.KOREA, "%.1f", release.sizeBytes / 1_048_576.0)} MB\n\nGitHub에서 APK를 다운로드해요. 다운로드 후 직접 설치해 주세요.") },
            confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { open(release.downloadUrl); updatePrompt = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_download_update")) { Text("APK 다운로드") } },
            dismissButton = { TextButton(shape = RunmoniShapes.Action, onClick = { updatePrompt = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_app_update")) { Text("나중에") } })
    }
    else linkError?.let { DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { linkError = null }, title = { Text("링크를 열지 못했어요") }, text = { DialogMessage(it) }, confirmButton = { TextButton(shape = RunmoniShapes.Action, onClick = { linkError = null }) { Text("확인") } }) }
}

@Composable
private fun AppVersionHistoryDialog(updates: AppUpdateUiState, onDismiss: () -> Unit) {
    FormDialog("업데이트 내역", onDismiss, dismissLabel = null, closeTag = "close_version_history") {
        Text("현재 ${BuildConfig.VERSION_NAME} · 빌드 ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.titleSmall)
        updates.release?.takeIf { it.isNewerThan(BuildConfig.VERSION_CODE.toLong()) && it.notes.isNotBlank() }?.let {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("새 버전 ${it.version}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(it.notes, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = RunmoniColors.Line)
        }
        bundledAppHistory.forEach { entry ->
            Surface(color = RunmoniColors.Paper, shape = RunmoniShapes.Surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.version, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(entry.date, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                Text(entry.status, style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Primary)
                entry.changes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            }
            }
        }
    }
}

@Composable
internal fun MileageExplanationDialog(onDismiss: () -> Unit, serverWallet: Boolean = false) {
    FormDialog("러닝 마일리지", onDismiss, dismissLabel = null, closeTag = "close_mileage_help") {
        Text("달리면 쌓이는 포인트", style = MaterialTheme.typography.titleMedium)
        Text("${if (serverWallet) "서버가 검증한 러닝" else "이 기기에 완료한 러닝"} 10m당 1P를 받아요. 지금은 모든 아이템이 무료라 포인트를 쓰지 않고 선택할 수 있어요.", style = MaterialTheme.typography.bodyLarge)
        if (serverWallet) SmallNote("지급 포인트는 GPS 검증 결과와 서버의 일일 한도에 따라 달라질 수 있어요.")
        Surface(color = RunmoniColors.MintWash, shape = RunmoniShapes.Surface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("거리별 적립 예시", style = MaterialTheme.typography.titleSmall)
                listOf("1km" to "100P", "5km" to "500P", "10km" to "1,000P").forEach { (km, points) ->
                    Text("$km → $points", style = MaterialTheme.typography.titleSmall,
                        color = RunmoniColors.Primary)
                }
            }
        }
        SmallNote("${if (serverWallet) "이 포인트는 테스트 서버에 보관되며 기기 포인트와 합산되지 않아요." else "이 포인트는 기기에 보관되며 테스트 서버 포인트와 합산되지 않아요."} Google 기록 요약 백업에는 포인트와 아이템이 포함되지 않아요. 현금 전환·결제·출금은 지원하지 않아요.")
    }
}

@Composable
private fun AppLinkRow(icon: ImageVector, title: String, detail: String, tag: String, external: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = title, onClick = onClick).testTag(tag).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(RunmoniColors.BlueWash, RunmoniShapes.Action), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = RunmoniColors.RouteBlue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
        }
        Icon(if (external) Icons.Outlined.OpenInNew else Icons.Outlined.ChevronRight, null,
            Modifier.size(20.dp), tint = RunmoniColors.Muted)
    }
}

@Composable
private fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { runCatching { context.assets.open("open-source-licenses.json").bufferedReader().use { JSONArray(it.readText()) } }.getOrElse { JSONArray() } }
    var selectedText by remember { mutableStateOf<String?>(null) }
    val listScroll = rememberScrollState()
    if (selectedText == null) FormDialog("오픈소스 라이선스", onDismiss, dismissLabel = null, closeTag = "close_licenses", scrollState = listScroll) {
        Text("앱에 포함된 라이브러리와 SDK", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        SmallNote("오픈소스와 별도 약관을 사용하는 SDK를 구분해 표시해요. 각 항목을 선택하면 고지와 원문 링크를 볼 수 있어요.")
        if (entries.length() == 0) Text("고지 목록을 읽지 못했어요. GitHub의 THIRD_PARTY_NOTICES.md를 확인해 주세요.")
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            AppLinkRow(Icons.Outlined.Description, entry.getString("name"), entry.optString("license"),
                "license_entry_$index", external = false) { selectedText = entry.toString() }
            HorizontalDivider(color = RunmoniColors.Line)
        }
    }
    selectedText?.let { raw ->
        val entry = JSONObjectCompat(raw)
        FormDialog(entry.name, { selectedText = null }, dismissLabel = null, closeTag = "close_license_detail") {
            Text(entry.license, style = MaterialTheme.typography.titleSmall)
            Text(entry.notice, style = MaterialTheme.typography.bodyMedium)
            if (entry.url.startsWith("https://")) OutlinedButton(shape = RunmoniShapes.Action, onClick = { openAppLink(context, entry.url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("고지 원문 열기") }
        }
    }
}

private class JSONObjectCompat(raw: String) {
    private val json = org.json.JSONObject(raw)
    val name = json.getString("name")
    val license = json.optString("license")
    val notice = json.optString("notice")
    val url = json.optString("url")
}

private fun openAppLink(context: Context, url: String): Boolean = runCatching {
    val uri = Uri.parse(url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank())
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)
