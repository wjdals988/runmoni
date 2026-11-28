package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.CloudAccountState
import com.runmoni.app.domain.AutomaticBackupRecordState
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunRecord
import com.runmoni.app.domain.RouteGeometry
import com.runmoni.app.domain.RunResultAnalysis
import com.runmoni.app.domain.lapRoute
import kotlinx.coroutines.launch

// A stable product callback for late-event regression checks. Foundation's
// OnClick SemanticsAction is a live ClickableNode accessor, not a snapshot.
// This private semantic value adds no platform accessibility action or label.
internal val RecordActionSnapshot = SemanticsPropertyKey<() -> Unit>("RunmoniRecordActionSnapshot")

internal fun Modifier.recordActionSnapshot(action: () -> Unit) = semantics { this[RecordActionSnapshot] = action }

/** A record is a page; only its destructive/account-specific choices are dialogs. */
@Composable
internal fun RunRecordScreen(
    record: RunRecord,
    actions: AppActions,
    cloud: CloudAccountState = CloudAccountState(),
    persistence: RunPersistenceState = RunPersistenceState(initialized = true),
    operationBusy: Boolean = false,
    onShare: (() -> Unit)? = null,
    showNewlySavedMotion: Boolean = false,
    onNewlySavedMotionShown: () -> Unit = {},
    comparisonRecords: List<RunRecord> = emptyList(),
    onPrepareAgain: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val scroll = key(record.id) { rememberScrollState() }
    // Each entity/account owns its page and Dialog windows. Preserve the local
    // record scroll across account changes while disposing all old actions.
    key(record.id, cloud.uid) {
        RunRecordPageContent(record, actions, cloud, persistence, operationBusy, scroll, onShare,
            showNewlySavedMotion, onNewlySavedMotionShown, comparisonRecords, onPrepareAgain, onBack)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
private fun RunRecordPageContent(
    record: RunRecord,
    actions: AppActions,
    cloud: CloudAccountState,
    persistence: RunPersistenceState,
    operationBusy: Boolean,
    scroll: ScrollState,
    onShare: (() -> Unit)?,
    showNewlySavedMotion: Boolean,
    onNewlySavedMotionShown: () -> Unit,
    comparisonRecords: List<RunRecord>,
    onPrepareAgain: (() -> Unit)?,
    onBack: () -> Unit,
) {
    // A confirmation never carries over to another record or account. Keep the
    // expected identifiers, rather than authorizing whichever entity is current
    // when an asynchronous account/list update arrives.
    var backupFor by remember(record.id, cloud.uid) { mutableStateOf<String?>(null) }
    var deleteFor by remember(record.id, cloud.uid) { mutableStateOf<String?>(null) }
    var backupRequest by remember { mutableStateOf<Any?>(null) }
    var deleteRequest by remember { mutableStateOf<Any?>(null) }
    var pageAlive by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        onDispose { pageAlive = false; backupRequest = null; deleteRequest = null }
    }
    val hasRecordId = record.id.isNotBlank()
    val backupState = cloud.automaticBackup.record(record.id)
    val canBackUp = hasRecordId && record.finishedAtMillis > 0 && cloud.uid.isNotBlank() && !cloud.busy && !operationBusy &&
        backupState != AutomaticBackupRecordState.EXCLUDED && backupState != AutomaticBackupRecordState.SAVED
    LaunchedEffect(backupState) {
        if (backupState == AutomaticBackupRecordState.SAVED || backupState == AutomaticBackupRecordState.EXCLUDED) {
            backupFor = null; backupRequest = null
        }
    }
    // A removed dialog can still have a queued click. Immutable values captured
    // by its old callback cannot establish the currently selected record/account.
    val confirmationContext = remember(record.id, cloud.uid) { Any() }
    val latestContext by rememberUpdatedState(confirmationContext)
    val latestRecordId by rememberUpdatedState(record.id)
    val latestUid by rememberUpdatedState(cloud.uid)
    val latestCanBackUp by rememberUpdatedState(canBackUp)
    val latestBusy by rememberUpdatedState(operationBusy)
    val latestActions by rememberUpdatedState(actions)
    val latestShare by rememberUpdatedState(onShare)
    val latestPrepareAgain by rememberUpdatedState(onPrepareAgain)
    val latestTraining by rememberUpdatedState(record.goal to record.interval)
    val shareAction: () -> Unit = {
        if (pageAlive && latestContext === confirmationContext && record.id == latestRecordId && !latestBusy &&
            latestActions.canShareRun(record)) latestShare?.invoke()
    }
    val exportAction: () -> Unit = {
        val expectedId = record.id
        if (pageAlive && expectedId.isNotBlank() && expectedId == latestRecordId && !latestBusy)
            latestActions.exportRun(expectedId)
    }
    val prepareAgainAction: () -> Unit = {
        if (pageAlive && latestContext === confirmationContext && hasRecordId && record.id == latestRecordId && !latestBusy &&
            latestTraining == (record.goal to record.interval))
            latestPrepareAgain?.invoke()
    }
    val analysis = remember(record) { RunResultAnalysis.from(record) }
    val geometry = remember(record.points) { RouteGeometry.from(record.points) }
    val hasRoute = geometry.segments.any { it.size >= 2 }
    var selectedKilometer by rememberSaveable { mutableIntStateOf(0) }
    var mapKilometer by rememberSaveable { mutableIntStateOf(0) }
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var lapMapOrigin by remember(configuration.screenWidthDp, configuration.screenHeightDp, density.density, density.fontScale) {
        mutableIntStateOf(-1)
    }
    val scope = rememberCoroutineScope()
    val selectedRoute = remember(geometry, record.lapAnchors, selectedKilometer) {
        geometry.lapRoute(selectedKilometer, record.lapAnchors)
    }
    val highlightedRoute = remember(geometry, record.lapAnchors, mapKilometer) {
        geometry.lapRoute(mapKilometer, record.lapAnchors)
    }
    val analysisAnchor = remember { BringIntoViewRequester() }
    val lapAnchor = remember { BringIntoViewRequester() }
    val mapAnchor = remember { BringIntoViewRequester() }
    var analysisRequest by remember { mutableIntStateOf(0) }
    var mapRequest by remember { mutableIntStateOf(0) }
    var lapRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(analysisRequest) { if (analysisRequest > 0) analysisAnchor.bringIntoView() }
    LaunchedEffect(mapRequest) { if (mapRequest > 0 && mapOpen) mapAnchor.bringIntoView() }
    LaunchedEffect(lapRequest) { if (lapRequest > 0) lapAnchor.bringIntoView() }
    val openMap: (Int) -> Unit = { kilometer ->
        lapMapOrigin = if (kilometer > 0) scroll.value else -1
        mapKilometer = kilometer
        mapOpen = true
        mapRequest++
    }
    BackHandler(enabled = backupFor == null && deleteFor == null, onBack = onBack)

    Surface(Modifier.fillMaxSize().testTag("record_detail_page"), color = RunmoniColors.Cream) {
        // This root is the sole owner of status/navigation/IME space. There is
        // no main bottom navigation underneath the detail page.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .testTag("record_safe_content")) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                BoxWithConstraints(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()) {
                    val separateShare = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
                    val gutter = pageGutter(maxWidth)
                    val shareButton: @Composable () -> Unit = {
                        FilledTonalButton(onClick = shareAction, shape = RunmoniShapes.Action,
                            enabled = !operationBusy && actions.canShareRun(record),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("share_run_record")
                                .semantics { contentDescription = "공유 이미지 만들기" }.recordActionSnapshot(shareAction)) {
                            Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("공유", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Column {
                        PageTopBar("러닝 기록", onBack, closeTag = "close_record", compact = true, trailingInset = gutter) {
                            if (onShare != null && !separateShare) shareButton()
                        }
                        if (onShare != null && separateShare) Row(Modifier.fillMaxWidth()
                            .padding(start = gutter, end = gutter, bottom = 12.dp),
                            horizontalArrangement = Arrangement.End) { shareButton() }
                    }
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).testTag("record_scroll_viewport")) {
                val gutter = pageGutter(maxWidth)
                Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                    .align(Alignment.TopCenter).verticalScroll(scroll).testTag("record_detail_scroll")
                    .padding(bottom = RunmoniLayout.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(RunmoniLayout.SectionGap)) {
                    RunRecordSummary(record, showNewlySavedMotion, onNewlySavedMotionShown,
                        Modifier.padding(horizontal = gutter).padding(top = RunmoniLayout.ContentGap))

                    val fastest = analysis.fastestSplits.firstOrNull()
                    val firstSplit = analysis.splits.firstOrNull()
                    if (analysis.elapsedMillis != null || record.goal != null || firstSplit != null || hasRoute || onPrepareAgain != null) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = gutter),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            record.goal?.let { goal ->
                                val outcome = goal.progress(record.distanceMeters, record.activeDurationMillis)
                                Text("목표 ${goal.label} · ${if (outcome.reached) "달성" else "미달성"}",
                                    style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Ink,
                                    modifier = Modifier.testTag("record_goal_outcome"))
                            }
                            analysis.elapsedMillis?.let {
                                Text("전체 경과 ${duration(it)}", style = MaterialTheme.typography.bodyMedium,
                                    color = RunmoniColors.Muted, modifier = Modifier.testTag("record_elapsed"))
                            }
                            fastest?.let {
                                val quickest = formatResultPace(it.paceSeconds)
                                val label = if (analysis.fastestSplits.size > 1) "공동 ${analysis.fastestSplits.size}개"
                                else "${it.kilometer - 1}–${it.kilometer} km"
                                val title = if (analysis.hasMissingSplits) "저장된 구간 중 가장 빠른 1km" else "가장 빠른 1km"
                                Text("$title · $label · $quickest /km",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    modifier = Modifier.testTag("record_fastest_split"))
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (firstSplit != null) FilledTonalButton(onClick = {
                                    selectedKilometer = (fastest ?: firstSplit).kilometer
                                    analysisRequest++
                                }, shape = RunmoniShapes.Action,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("record_view_splits")) { Text("구간 보기") }
                                if (hasRoute) OutlinedButton(onClick = { openMap(0) }, shape = RunmoniShapes.Action,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("record_view_route")) {
                                    Icon(Icons.Outlined.Route, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("경로 보기")
                                }
                                if (onPrepareAgain != null) OutlinedButton(onClick = prepareAgainAction,
                                    shape = RunmoniShapes.Action, enabled = !operationBusy,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag("record_prepare_again")
                                        .recordActionSnapshot(prepareAgainAction)) { Text("이 훈련 다시 준비") }
                            }
                        }
                    }

                    RunIntervalResult(record.interval, record.activeDurationMillis, Modifier.padding(horizontal = gutter))

                    RunRecordPaceAnalysis(analysis, selectedKilometer, { selectedKilometer = it },
                        Modifier.padding(horizontal = gutter).bringIntoViewRequester(analysisAnchor),
                        onOpenSelectedRoute = if (selectedRoute != null) ({ openMap(selectedKilometer) }) else null,
                        selectedLapModifier = Modifier.bringIntoViewRequester(lapAnchor))

                    RunRecordComparison(record, comparisonRecords, Modifier.padding(horizontal = gutter))

                    Column(Modifier.fillMaxWidth().padding(horizontal = gutter).bringIntoViewRequester(mapAnchor),
                        verticalArrangement = Arrangement.spacedBy(RunmoniLayout.ContentGap)) {
                        RecordSectionTitle("러닝 경로", Icons.Outlined.Route)
                        if (hasRoute) {
                            OutlinedButton(onClick = { if (mapOpen) mapOpen = false else openMap(0) }, shape = RunmoniShapes.Action,
                                modifier = Modifier.heightIn(min = 48.dp).testTag("record_toggle_map")) {
                                Icon(if (mapOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (mapOpen) "지도 접기" else "지도 펼치기")
                            }
                            if (mapOpen) {
                                if (highlightedRoute != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${mapKilometer - 1}–$mapKilometer km 강조 중",
                                        style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.RouteBlue,
                                        modifier = Modifier.align(Alignment.CenterVertically).testTag("record_map_selected_lap")
                                            .background(RunmoniColors.BlueWash, RunmoniShapes.Action)
                                            .padding(horizontal = 10.dp, vertical = 6.dp))
                                    TextButton(onClick = {
                                        selectedKilometer = mapKilometer
                                        val destination = lapMapOrigin
                                        if (destination >= 0) scope.launch { scroll.scrollTo(destination) }
                                        else lapRequest++
                                    }, shape = RunmoniShapes.Action,
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("record_map_return_to_lap")) {
                                        Text("구간 기록 보기")
                                    }
                                    TextButton(onClick = { mapKilometer = 0 },
                                        modifier = Modifier.heightIn(min = 48.dp).testTag("record_map_clear_lap")) {
                                        Text("전체 경로 보기")
                                    }
                                }
                                Box(Modifier.fillMaxWidth().height(if (LocalDensity.current.fontScale >= 1.3f) 360.dp else 240.dp)
                                    .clip(RunmoniShapes.Surface).testTag("record_map")) {
                                    MapPane(record.points, modifier = Modifier.fillMaxSize(), fitRoute = true,
                                        highlightedRoute = highlightedRoute)
                                }
                            }
                        } else {
                            Text(if (record.points.isEmpty()) "저장된 GPS 위치가 없어 경로를 표시할 수 없어요."
                                else "연결할 수 있는 GPS 위치가 부족해 경로를 표시할 수 없어요.",
                                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                                modifier = Modifier.testTag("record_no_route"))
                        }
                        if (record.interrupted) Text("중단된 구간이 있어요. 확인되지 않은 거리는 더하지 않았어요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                            modifier = Modifier.testTag("record_interrupted"))
                    }

                    RunReflectionSection(record, actions, Modifier.padding(horizontal = gutter),
                        enabled = !operationBusy && !persistence.historyUnavailable)

                    Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper)
                        .padding(horizontal = gutter, vertical = RunmoniLayout.SectionGap),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecordSectionTitle("기록 관리", Icons.Outlined.FolderOpen)
                        HorizontalDivider(color = RunmoniColors.Line)
                        RecordStatusLine(if (record.synced) Icons.Outlined.CloudDone else Icons.Outlined.PhoneAndroid,
                            if (record.synced) "서버 동기화됨" else "기기에 저장됨",
                            Modifier.testTag("record_storage_status"), RunmoniColors.Primary)
                        if (record.id in persistence.pendingCreditRunIds)
                            RunPersistenceNotice(RunPersistenceState(issue = RunPersistenceIssue.CREDIT,
                                initialized = true, saving = persistence.saving, creditPending = true,
                                pendingCreditRunIds = setOf(record.id)), actions::retryRunPersistence)
                        Text(AUTOMATIC_BACKUP_PRIVACY,
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        if (cloud.uid.isNotBlank()) {
                            RecordStatusLine(when (backupState) {
                                AutomaticBackupRecordState.SAVED -> Icons.Outlined.CloudDone
                                AutomaticBackupRecordState.EXCLUDED -> Icons.Outlined.CloudOff
                                AutomaticBackupRecordState.CONFLICT -> Icons.Outlined.WarningAmber
                                else -> Icons.Outlined.CloudQueue
                            }, cloud.automaticBackupMessage(record.id), Modifier.testTag("automatic_cloud_backup_status"))
                            if (backupState != AutomaticBackupRecordState.SAVED && backupState != AutomaticBackupRecordState.EXCLUDED) {
                                TextButton(shape = RunmoniShapes.Action, onClick = {
                                    if (pageAlive && latestContext === confirmationContext && latestCanBackUp) { backupRequest = Any(); backupFor = record.id }
                                }, enabled = canBackUp,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup_cloud_run")) {
                                    Text("자동 백업 다시 시도")
                                }
                            }
                            Text("백업 계정: ${cloud.accountLabel}\n${cloud.status}",
                                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        } else Text("설정에서 Google 계정을 연결하면 완료 기록 요약을 자동 백업해요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        HorizontalDivider(color = RunmoniColors.Line)
                        OutlinedButton(shape = RunmoniShapes.Action, onClick = exportAction, enabled = hasRecordId && !operationBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_run").recordActionSnapshot(exportAction)) {
                            Icon(Icons.Outlined.FileDownload, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("GPX 경로 내보내기")
                        }
                        Text(if (record.points.isEmpty()) "저장된 GPS 지점이 없어 경로는 포함되지 않아요."
                            else "GPX 파일에는 실제 경로가 포함돼요.", style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted)
                        TextButton(shape = RunmoniShapes.Action, onClick = {
                            if (pageAlive && hasRecordId && latestContext === confirmationContext && !latestBusy) { deleteRequest = Any(); deleteFor = record.id }
                        }, enabled = hasRecordId && !operationBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_run")) {
                            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("기록 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    val backupId = backupFor
    if (backupId != null && backupId == record.id && cloud.uid.isNotBlank()) {
        val expectedUid = cloud.uid
        val expectedRequest = backupRequest
        val confirmBackup: () -> Unit = {
            if (pageAlive && expectedRequest != null && backupRequest === expectedRequest && backupFor == backupId && latestContext === confirmationContext &&
                latestRecordId == backupId && latestUid == expectedUid && latestCanBackUp) {
                backupFor = null
                backupRequest = null
                latestActions.backupCloudRun(backupId, expectedUid)
            }
        }
        DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { if (backupRequest === expectedRequest) { backupFor = null; backupRequest = null } },
            title = { Text("자동 백업을 다시 시도할까요?") },
            text = { DialogMessage("계정: ${cloud.accountLabel}\n\n완료 기록은 이미 자동 백업 대상이에요. 대기 중인 이 기록의 전송을 다시 요청해요.\n\n$AUTOMATIC_BACKUP_PRIVACY") },
            confirmButton = {
                TextButton(shape = RunmoniShapes.Action, onClick = confirmBackup, enabled = canBackUp,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_cloud_backup").recordActionSnapshot(confirmBackup)) {
                    Text("다시 시도")
                }
            }, dismissButton = {
                TextButton(shape = RunmoniShapes.Action, onClick = { if (backupRequest === expectedRequest) { backupFor = null; backupRequest = null } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_cloud_backup")) { Text("취소") }
            })
    }
    val deleteId = deleteFor
    if (deleteId != null && deleteId == record.id) {
        val expectedUid = cloud.uid
        val expectedRequest = deleteRequest
        val confirmDelete: () -> Unit = {
            if (pageAlive && expectedRequest != null && deleteRequest === expectedRequest && deleteFor == deleteId && deleteId.isNotBlank() && latestContext === confirmationContext &&
                latestRecordId == deleteId && latestUid == expectedUid && !latestBusy) {
                deleteFor = null
                deleteRequest = null
                // The root waits for canonical removal before leaving.
                latestActions.deleteRun(deleteId)
            }
        }
        DismissibleAlertDialog(shape = RunmoniShapes.Dialog, onDismissRequest = { if (deleteRequest === expectedRequest) { deleteFor = null; deleteRequest = null } },
            title = { Text("이 기록을 삭제할까요?") },
            text = { DialogMessage("삭제한 기기 기록은 복구할 수 없어요. 클라우드에 백업한 요약은 계정 설정에서 별도로 삭제해 주세요.") },
            confirmButton = {
                TextButton(shape = RunmoniShapes.Action, onClick = confirmDelete, enabled = !operationBusy,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_delete_run").recordActionSnapshot(confirmDelete)) { Text("삭제") }
            }, dismissButton = {
                TextButton(shape = RunmoniShapes.Action, onClick = { if (deleteRequest === expectedRequest) { deleteFor = null; deleteRequest = null } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("cancel_delete_run")) { Text("취소") }
            })
    }
}

@Composable
internal fun RecordSectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = RunmoniColors.Primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun RunRecordSummary(record: RunRecord, showNewlySavedMotion: Boolean, onNewlySavedMotionShown: () -> Unit,
    modifier: Modifier = Modifier) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    RunningMetricPanel(contentPadding = PaddingValues(if (largeText) 16.dp else 20.dp),
        spacing = if (largeText) 8.dp else 12.dp, modifier = modifier, accent = {
            NewlySavedTrackCue(record.id, showNewlySavedMotion, onNewlySavedMotionShown,
                Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 5.dp).size(width = 44.dp, height = 10.dp))
        }) {
        Text(dateTime(record.startedAtMillis),
            style = if (largeText) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            color = RunmoniTrack.Detail, modifier = Modifier.testTag("record_date"))
        RecordDistance(distance(record.distanceMeters), maxSize = if (largeText) 44f else 56f)
        HorizontalDivider(color = RunmoniTrack.Detail.copy(alpha = .4f))
        val time = duration(record.activeDurationMillis)
        val pace = averagePace(record.distanceMeters, record.activeDurationMillis)
        Box(Modifier.fillMaxWidth().testTag("record_duration_pace")
            .clearAndSetSemantics { text = AnnotatedString("러닝 시간 $time · 평균 $pace /km") }) {
            MetricGroup(listOf(UiMetric(if (largeText) "활동 시간" else "러닝 시간", time, tag = "record_active_time"),
                UiMetric(if (largeText) "평균" else "평균 페이스", pace, "/km", "record_average_pace")),
                onDark = true, compact = largeText)
        }
    }
}

/** One short decorative mark, outside the panel's metric text area. */
@Composable
private fun NewlySavedTrackCue(recordId: String, request: Boolean, onShown: () -> Unit, modifier: Modifier) {
    val motionAllowed = rememberPageAnimationsEnabled()
    val progress = remember(recordId) { Animatable(0f) }
    var consumed by remember(recordId) { mutableStateOf(false) }
    var visible by remember(recordId) { mutableStateOf(false) }
    val latestOnShown by rememberUpdatedState(onShown)
    // Consume before the first frame. The route can change while the mark is drawing.
    LaunchedEffect(recordId, motionAllowed) {
        if (request && !consumed) {
            consumed = true
            latestOnShown()
            if (motionAllowed) {
                visible = true
                progress.animateTo(1f, tween(160))
            }
        }
        visible = false
    }
    if (visible) Canvas(modifier.testTag("newly_saved_track_cue").clearAndSetSemantics { }) {
        val start = Offset(1.dp.toPx(), size.height * .6f)
        val turn = Offset(size.width * .76f, size.height * .6f)
        val drop = Offset(size.width * .82f, size.height * .88f)
        val end = Offset(size.width - 2.dp.toPx(), size.height * .23f)
        val segments = listOf(start to turn, turn to drop, drop to end)
        val alpha = (1f - ((progress.value - .65f) / .35f).coerceIn(0f, 1f))
        for ((index, segment) in segments.withIndex()) {
            val fraction = (progress.value * segments.size - index).coerceIn(0f, 1f)
            if (fraction > 0f) drawLine(RunmoniTrack.Highlight.copy(alpha = alpha), segment.first,
                segment.first + (segment.second - segment.first) * fraction, 1.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun RecordStatusLine(icon: ImageVector, message: String, modifier: Modifier = Modifier,
    color: Color = RunmoniColors.Muted) {
    Row(modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(20.dp), tint = color)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Number and unit share a baseline and a text block, rather than opposite ends
 * of a weighted row. Respect font scaling; at the readable minimum a genuinely
 * long value wraps naturally instead of clipping the final digits or its unit.
 */
@Composable
private fun RecordDistance(value: String, maxSize: Float) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum")
    val unit = SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = RunmoniTrack.Detail)
    val label = buildAnnotatedString {
        append(value)
        withStyle(unit) { append(" km") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("record_distance")
        .clearAndSetSemantics { text = AnnotatedString("$value km") }) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val size = remember(value, widthPx, measurer, style, unit, maxSize) {
            fun fits(candidate: Float) = measurer.measure(label,
                style.copy(fontSize = candidate.sp, lineHeight = (candidate * 1.15f).sp),
                softWrap = false, maxLines = 1).size.width <= widthPx
            if (fits(maxSize)) maxSize else {
                var low = 20f
                var high = maxSize
                repeat(8) {
                    val middle = (low + high) / 2
                    if (fits(middle)) low = middle else high = middle
                }
                low
            }
        }
        Text(label, modifier = Modifier.fillMaxWidth(), color = RunmoniTrack.Highlight,
            style = style.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp), softWrap = true)
    }
}
