package com.runmoni.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.AppActions
import com.runmoni.app.domain.CloudAccountState
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunRecord

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
    val canBackUp = hasRecordId && record.finishedAtMillis > 0 && cloud.uid.isNotBlank() && !cloud.busy && !operationBusy
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
                Row(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                    .heightIn(min = 64.dp).padding(start = 8.dp, end = pageGutter(), top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("close_record")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로")
                    }
                    Text("러닝 기록", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    if (onShare != null) IconButton(onClick = shareAction,
                        enabled = !operationBusy && actions.canShareRun(record),
                        modifier = Modifier.size(48.dp).testTag("share_run_record").recordActionSnapshot(shareAction)) {
                        Icon(Icons.Outlined.Share, "공유 이미지 만들기")
                    }
                }
            }
            HorizontalDivider(color = RunmoniColors.Line)
            Box(Modifier.fillMaxWidth().weight(1f).testTag("record_scroll_viewport")) {
                Column(Modifier.widthIn(max = RunmoniLayout.ContentWidth).fillMaxWidth()
                    .align(Alignment.TopCenter).verticalScroll(scroll).testTag("record_detail_scroll")
                    .padding(horizontal = pageGutter(), vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateTime(record.startedAtMillis), style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted, modifier = Modifier.testTag("record_date"))
                        DistanceNumber("${distance(record.distanceMeters)} km", RunmoniColors.Primary,
                            modifier = Modifier.testTag("record_distance"), maxSize = 48f)
                        Text("러닝 시간 ${duration(record.activeDurationMillis)} · 평균 ${averagePace(record.distanceMeters, record.activeDurationMillis)} /km",
                            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("record_duration_pace"))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordSectionTitle("러닝 경로")
                        Box(Modifier.fillMaxWidth()
                            .height(if (LocalDensity.current.fontScale >= 1.3f) 300.dp else 260.dp)
                            .clip(RunmoniShapes.Surface).testTag("record_map")) {
                            MapPane(record.points, modifier = Modifier.fillMaxSize(), fitRoute = true)
                        }
                        if (record.interrupted) Text("중단된 구간이 있어요. 확인되지 않은 거리는 더하지 않았어요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted,
                            modifier = Modifier.testTag("record_interrupted"))
                    }

                    if (record.splits.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordSectionTitle("킬로미터별 기록")
                        record.splits.forEach { split ->
                            Row(Modifier.fillMaxWidth().testTag("record_split_${split.kilometer}"),
                                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                                Text("${split.kilometer} km", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                Text(duration(split.durationMillis), style = MaterialTheme.typography.bodyLarge)
                            }
                            HorizontalDivider(color = RunmoniColors.Line)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RecordSectionTitle("저장과 백업")
                        Text(if (record.synced) "서버 동기화됨" else "기기에 저장됨",
                            style = MaterialTheme.typography.titleSmall, color = RunmoniColors.Primary,
                            modifier = Modifier.testTag("record_storage_status"))
                        if (record.id in persistence.pendingCreditRunIds)
                            RunPersistenceNotice(RunPersistenceState(issue = RunPersistenceIssue.CREDIT,
                                initialized = true, saving = persistence.saving, creditPending = true,
                                pendingCreditRunIds = setOf(record.id)), actions::retryRunPersistence)
                        Text("GPS 경로·포인트는 이 기기에 보관돼요. Google에는 날짜·거리·시간·GPS 포인트 수 요약만 백업해요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        if (cloud.uid.isNotBlank()) {
                            OutlinedButton(shape = RunmoniShapes.Action, onClick = {
                                if (pageAlive && latestContext === confirmationContext && latestCanBackUp) { backupRequest = Any(); backupFor = record.id }
                            }, enabled = canBackUp,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("backup_cloud_run")) {
                                Text("이 기록 요약을 계정에 백업")
                            }
                            Text("백업 계정: ${cloud.accountLabel}\n${cloud.status}",
                                style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                        } else Text("요약 백업은 설정에서 Google 계정을 연결한 뒤 이용할 수 있어요.",
                            style = MaterialTheme.typography.bodyMedium, color = RunmoniColors.Muted)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider(color = RunmoniColors.Line)
                        OutlinedButton(shape = RunmoniShapes.Action, onClick = exportAction, enabled = hasRecordId && !operationBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_run").recordActionSnapshot(exportAction)) {
                            Text("GPX 경로 내보내기")
                        }
                        Text(if (record.points.isEmpty()) "저장된 GPS 지점이 없어 경로는 포함되지 않아요."
                            else "GPX 파일에는 실제 경로가 포함돼요.", style = MaterialTheme.typography.bodyMedium,
                            color = RunmoniColors.Muted)
                        TextButton(shape = RunmoniShapes.Action, onClick = {
                            if (pageAlive && hasRecordId && latestContext === confirmationContext && !latestBusy) { deleteRequest = Any(); deleteFor = record.id }
                        }, enabled = hasRecordId && !operationBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("delete_run")) {
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
            title = { Text("이 계정에 기록 요약을 백업할까요?") },
            text = { DialogMessage("계정: ${cloud.accountLabel}\n\n이 기기에서 선택한 본인의 기록인지 확인해 주세요. 날짜·거리·시간·GPS 포인트 수 요약만 저장해요. 경로 원본·마일리지는 포함하지 않으며, 보상을 검증한 기록으로 인정하지 않아요.") },
            confirmButton = {
                TextButton(shape = RunmoniShapes.Action, onClick = confirmBackup, enabled = canBackUp,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("confirm_cloud_backup").recordActionSnapshot(confirmBackup)) {
                    Text("이 계정에 백업")
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
private fun RecordSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}
