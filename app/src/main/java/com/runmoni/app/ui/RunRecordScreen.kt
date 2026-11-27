package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
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
import com.runmoni.app.domain.KmSplit

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
    onBack: () -> Unit,
) {
    val scroll = key(record.id) { rememberScrollState() }
    // Each entity/account owns its page and Dialog windows. Preserve the local
    // record scroll across account changes while disposing all old actions.
    key(record.id, cloud.uid) {
        RunRecordPageContent(record, actions, cloud, persistence, operationBusy, scroll, onShare, onBack)
    }
}

@Composable
private fun RunRecordPageContent(
    record: RunRecord,
    actions: AppActions,
    cloud: CloudAccountState,
    persistence: RunPersistenceState,
    operationBusy: Boolean,
    scroll: ScrollState,
    onShare: (() -> Unit)?,
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
    val shareAction: () -> Unit = {
        if (pageAlive && latestContext === confirmationContext && record.id == latestRecordId && !latestBusy &&
            latestActions.canShareRun(record)) latestShare?.invoke()
    }
    val exportAction: () -> Unit = {
        val expectedId = record.id
        if (pageAlive && expectedId.isNotBlank() && expectedId == latestRecordId && !latestBusy)
            latestActions.exportRun(expectedId)
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
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .padding(start = 8.dp, end = pageGutter(), top = 4.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("close_record")) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로")
                            }
                            Text("러닝 기록", style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f).semantics { heading() })
                            if (onShare != null && !separateShare) shareButton()
                        }
                        if (onShare != null && separateShare) Row(Modifier.fillMaxWidth()
                            .padding(start = pageGutter(), end = pageGutter(), bottom = 12.dp),
                            horizontalArrangement = Arrangement.End) { shareButton() }
                    }
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
            Box(Modifier.fillMaxWidth().weight(1f).testTag("record_scroll_viewport")) {
                Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                    .align(Alignment.TopCenter).verticalScroll(scroll).testTag("record_detail_scroll")
                    .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)) {
                    RunningMetricPanel(contentPadding = PaddingValues(20.dp), spacing = 12.dp,
                        modifier = Modifier.padding(horizontal = pageGutter()).padding(top = 16.dp)) {
                        Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniTrack.Detail, modifier = Modifier.testTag("record_date"))
                        val recordedDistance = distance(record.distanceMeters)
                        RecordDistance(recordedDistance)
                        HorizontalDivider(color = RunmoniTrack.Detail.copy(alpha = .4f))
                        val time = duration(record.activeDurationMillis)
                        val pace = averagePace(record.distanceMeters, record.activeDurationMillis)
                        BoxWithConstraints(Modifier.fillMaxWidth().testTag("record_duration_pace")
                            .clearAndSetSemantics { text = AnnotatedString("러닝 시간 $time · 평균 $pace /km") }) {
                            if (maxWidth < 272.dp || LocalDensity.current.fontScale >= 1.3f) {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    RecordResultMetric("러닝 시간", time, Icons.Outlined.Timer)
                                    RecordResultMetric("평균 페이스", "$pace /km", Icons.Outlined.Speed)
                                }
                            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Box(Modifier.weight(1f)) { RecordResultMetric("러닝 시간", time, Icons.Outlined.Timer) }
                                Box(Modifier.weight(1f)) { RecordResultMetric("평균 페이스", "$pace /km", Icons.Outlined.Speed) }
                            }
                        }
                    }

                    Column(Modifier.padding(horizontal = pageGutter()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        RecordSectionTitle("러닝 경로", Icons.Outlined.Route)
                        val hasRoute = remember(record.points) {
                            RouteGeometry.from(record.points).segments.any { it.size >= 2 }
                        }
                        if (hasRoute) {
                            Box(Modifier.fillMaxWidth().height(if (LocalDensity.current.fontScale >= 1.3f) 360.dp else 240.dp)
                                .clip(RunmoniShapes.Surface).testTag("record_map")) {
                                MapPane(record.points, modifier = Modifier.fillMaxSize(), fitRoute = true)
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

                    if (record.splits.isNotEmpty()) Column(Modifier.padding(horizontal = pageGutter()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordSectionTitle("킬로미터별 기록", Icons.Outlined.Timer)
                        Text("막대는 구간 시간의 비율이에요. 짧을수록 빨라요.",
                            style = MaterialTheme.typography.bodySmall, color = RunmoniColors.Muted)
                        val longestSplit = record.splits.maxOf { it.durationMillis }.coerceAtLeast(0)
                        record.splits.forEach { split -> RecordSplitRow(split, longestSplit) }
                    }

                    Column(Modifier.fillMaxWidth().background(RunmoniColors.Paper)
                        .padding(horizontal = pageGutter(), vertical = 24.dp),
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
private fun RecordSectionTitle(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = RunmoniColors.Primary)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

@Composable
private fun RecordResultMetric(label: String, value: String, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = RunmoniTrack.Detail)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = RunmoniTrack.Detail)
        }
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = RunmoniTrack.OnBoard)
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

/** Equal one-kilometre intervals compare elapsed split duration, never an invented pace target.
 * The full range starts at zero: no minimum bar length or truncated axis exaggerates a difference.
 */
@Composable
private fun RecordSplitRow(split: KmSplit, longestMillis: Long) {
    val known = split.durationMillis > 0 && longestMillis > 0
    val time = if (known) duration(split.durationMillis) else "시간 미확인"
    Column(Modifier.fillMaxWidth().testTag("record_split_${split.kilometer}")
        .clearAndSetSemantics { text = AnnotatedString("${split.kilometer} km, $time") }
        .padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Text("${split.kilometer} km", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
        }
        if (known) {
            val ratio = (split.durationMillis.toDouble() / longestMillis.toDouble()).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RunmoniShapes.Status).background(RunmoniColors.Mist)) {
                Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(RunmoniShapes.Status).background(RunmoniColors.Primary))
            }
        }
    }
}

/** Number and unit share a baseline and a text block, rather than opposite ends
 * of a weighted row. Respect font scaling; at the readable minimum a genuinely
 * long value wraps naturally instead of clipping the final digits or its unit.
 */
@Composable
private fun RecordDistance(value: String) {
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
        val size = remember(value, widthPx, measurer, style, unit) {
            fun fits(candidate: Float) = measurer.measure(label,
                style.copy(fontSize = candidate.sp, lineHeight = (candidate * 1.15f).sp),
                softWrap = false, maxLines = 1).size.width <= widthPx
            if (fits(56f)) 56f else {
                var low = 20f
                var high = 56f
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
