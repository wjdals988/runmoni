package com.runmoni.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.data.MapTileResult
import com.runmoni.app.data.MapTileSource
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private fun recordPageFixture(id: String = "record-page-a") = RunRecord(
    id = id, startedAtMillis = 1_730_000_000_000, finishedAtMillis = 1_730_001_300_000,
    distanceMeters = 3210.0, activeDurationMillis = 1_200_000, elapsedDurationMillis = 1_300_000,
    points = listOf(
        TrackPoint(0, 37.5665, 126.9780, 5f, 1_730_000_000_000),
        TrackPoint(1, 37.5666, 126.9781, 5f, 1_730_000_002_000),
        TrackPoint(2, 37.5670, 126.9785, 5f, 1_730_000_100_000, segment = 1),
    ),
    splits = (1..3).map { KmSplit(it, 360_000 + it * 1_000L) }, interrupted = true,
)

/** An unavailable tile source, never a fabricated map or an online-map proof. */
@Composable
private fun OfflineRecordPageFixture(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMapTileSource provides MapTileSource { MapTileResult() }) {
        RunmoniTheme(content)
    }
}

class RunRecordScreenUiTest : RunmoniDesignTestBase() {
    @Test fun resultPaceChartAndRowsUseTheSameSelectionAndRequestTheMapOnlyOnTap() {
        val record = recordPageFixture().copy(interrupted = false, elapsedDurationMillis = 1_200_000,
            splits = listOf(KmSplit(1, 361_000), KmSplit(2, 301_000), KmSplit(3, 363_000)))
        val actions = RunmoniUiTest.FakeActions(mutableStateOf(AppState(records = listOf(record))))
        val tileOperations = AtomicInteger()
        val tiles = MapTileSource { tileOperations.incrementAndGet(); MapTileResult() }
        compose.setContent {
            CompositionLocalProvider(LocalMapTileSource provides tiles) {
                RunmoniTheme(darkTheme = false) {
                    Box(Modifier.width(411.dp).height(760.dp).testTag("audit_viewport")) {
                        RunRecordScreen(record, actions, onBack = {}, onShare = {})
                    }
                }
            }
        }
        compose.onNodeWithTag("record_map").assertDoesNotExist()
        compose.onNodeWithTag("record_fastest_split").assertTextContains("1–2 km · 5:01 /km", substring = true)
        capture("run-result-overview-411-fixture")

        compose.onNodeWithTag("record_view_splits").performScrollTo().performClick()
        compose.onNodeWithTag("record_split_2").assertIsSelected()
        val chart = compose.onNodeWithTag("record_pace_plot")
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2번 구간 선택"))
        chart.performScrollTo().performTouchInput { click(Offset(2f, center.y)) }
        compose.onNodeWithTag("record_split_1").assertIsSelected()
        compose.onNodeWithTag("record_selected_split").assertTextContains("0–1 km · 6:01 /km")
        compose.onNodeWithTag("record_split_3").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        chart.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "3번 구간 선택"))
        compose.onNodeWithTag("record_selected_split").assertTextContains("직전 구간보다 1분 2초 느려요.")
        chart.performScrollTo()
        capture("run-result-pace-and-laps-411-fixture")

        assertEquals(0, tileOperations.get())
        compose.onNodeWithTag("record_view_route").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("record_map").assertExists()
        compose.waitUntil(10_000) { tileOperations.get() > 0 }
        compose.onNodeWithTag("record_toggle_map").performScrollTo().performClick()
        compose.onNodeWithTag("record_map").assertDoesNotExist()
    }

    @Test fun compactResultKeepsSummaryAndShareFullyVisibleBeforeScroll() {
        val record = recordPageFixture().copy(interrupted = false, goal = RunGoal(RunGoalKind.DISTANCE, 3))
        val actions = RunmoniUiTest.FakeActions(mutableStateOf(AppState(records = listOf(record))))
        compose.setContent {
            OfflineRecordPageFixture { ViewportBox(Viewport(320, 2f)) {
                Box(Modifier.height(520.dp).testTag("compact_record_fixture")) {
                    RunRecordScreen(record, actions, onBack = {}, onShare = {})
                }
            } }
        }
        val body = compose.onNodeWithTag("record_scroll_viewport").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("record_distance", "record_duration_pace")) {
            val metric = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("Full $tag is visible before scrolling", metric.positionInRoot.y >= body.top - 1f &&
                metric.positionInRoot.y + metric.size.height <= body.bottom + 1f)
        }
        val share = compose.onNodeWithTag("share_run_record").assertIsDisplayed().assertIsEnabled()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).fetchSemanticsNode()
        assertTrue(share.positionInRoot.y + share.size.height <= body.top + 1f)
        capture("run-result-320-font2-fixture")
        compose.onNodeWithTag("record_goal_outcome").performScrollTo().assertTextEquals("목표 3 km · 달성")
        compose.onNodeWithTag("record_elapsed").performScrollTo().assertTextEquals("전체 경과 21:40")
    }

    @Test fun recordsWithoutGpsPositionsShowAnExplanationAndKeepSharingAvailable() {
        val record = recordPageFixture().copy(points = emptyList())
        val actions = RunmoniUiTest.FakeActions(mutableStateOf(AppState(records = listOf(record))))
        compose.setContent {
            OfflineRecordPageFixture { RunRecordScreen(record, actions, onBack = {}, onShare = {}) }
        }
        compose.onNodeWithTag("record_map").assertDoesNotExist()
        compose.onNodeWithTag("record_no_route").performScrollTo().assertTextContains("저장된 GPS 위치가 없어", substring = true)
        compose.onNodeWithTag("share_run_record").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun automaticBackupShowsItsQueueAndAcknowledgementWithoutARequiredPerRecordAction() {
        val record = recordPageFixture()
        val cloud = mutableStateOf(CloudAccountState(configured = true, uid = "account-a", accountLabel = "계정 A",
            automaticBackup = AutomaticCloudBackupState(mapOf(record.id to AutomaticBackupRecordState.WAITING), initialized = true)))
        val state = mutableStateOf(AppState(records = listOf(record)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { OfflineRecordPageFixture { RunRecordScreen(record, actions, cloud.value, onBack = {}) } }
        compose.onNodeWithTag("automatic_cloud_backup_status").performScrollTo().assertTextContains("자동 백업 대기", substring = true)
        compose.runOnIdle {
            assertTrue(actions.cloudBackups.isEmpty())
            cloud.value = cloud.value.copy(automaticBackup = AutomaticCloudBackupState(
                mapOf(record.id to AutomaticBackupRecordState.SAVED), initialized = true))
        }
        compose.onNodeWithTag("automatic_cloud_backup_status").assertTextContains("자동 백업했어요", substring = true)
        compose.onNodeWithTag("backup_cloud_run").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(actions.cloudBackups.isEmpty())
            cloud.value = cloud.value.copy(uid = "account-b", accountLabel = "계정 B",
                automaticBackup = AutomaticCloudBackupState(mapOf(record.id to AutomaticBackupRecordState.EXCLUDED), initialized = true))
        }
        compose.onNodeWithTag("automatic_cloud_backup_status").performScrollTo().assertTextContains("대상에서 제외", substring = true)
        compose.onNodeWithTag("backup_cloud_run").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.cloudBackups.isEmpty()) }
    }

    @Test fun actionsKeepTheirRecordIdentityAndDeleteCancellationDoesNotLeaveThePage() {
        val first = recordPageFixture()
        val second = recordPageFixture("record-page-b")
        val record = mutableStateOf(first)
        val busy = mutableStateOf(false)
        val state = mutableStateOf(AppState(records = listOf(first, second)))
        val actions = RunmoniUiTest.FakeActions(state)
        var backs = 0
        compose.setContent { OfflineRecordPageFixture {
            RunRecordScreen(record.value, actions, operationBusy = busy.value, onBack = { backs++ })
        } }
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.onNodeWithTag("record_distance").assertTextEquals("3.21 km")
        compose.onNodeWithTag("record_duration_pace").assertTextEquals("러닝 시간 20:00 · 평균 6′13″ /km")
        compose.onNodeWithTag("export_run").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first.id), actions.exports) }
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.onNodeWithTag("cancel_delete_run").performClick()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.deletions.isEmpty()); assertEquals(0, backs) }

        // A list refresh selecting a different record must revoke the old dialog.
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.runOnIdle { record.value = second }
        compose.onNodeWithTag("confirm_delete_run").assertDoesNotExist()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithTag("export_run").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("delete_run").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { busy.value = false }
        compose.onNodeWithTag("export_run").performScrollTo().performClick()
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_delete_run").performClick()
        compose.runOnIdle {
            assertEquals(listOf(first.id, second.id), actions.exports)
            assertEquals(listOf(second.id), actions.deletions)
            assertEquals(0, backs) // The root observes actual deletion before closing.
        }
        compose.onNodeWithTag("close_record").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun backupRequiresANewConfirmationAfterEitherAccountOrRecordChanges() {
        val first = recordPageFixture()
        val second = recordPageFixture("record-page-b")
        val record = mutableStateOf(first)
        val cloud = mutableStateOf(CloudAccountState(configured = true, uid = "account-a", accountLabel = "계정 A"))
        val state = mutableStateOf(AppState(records = listOf(first, second)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { OfflineRecordPageFixture {
            RunRecordScreen(record.value, actions, cloud = cloud.value, onBack = {})
        } }
        val diagnostic = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "design-review/record-backup-window-focus.txt").apply { parentFile!!.mkdirs() }
        diagnostic.writeText("Synthetic record/account callback fixture; actual user lambda snapshots, not live ClickableNode actions.\n")
        fun trace(label: String) {
            val values = compose.runOnIdle { "record=${record.value.id} uid=${cloud.value.uid} tuples=${actions.cloudBackups.toList()} focus=${compose.activity.hasWindowFocus()}" }
            val nodes = compose.onAllNodesWithTag("confirm_cloud_backup").fetchSemanticsNodes().map { it.id }
            diagnostic.appendText("$label $values confirmNodeIds=$nodes\n")
        }
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_cloud_backup").assertIsDisplayed()
        trace("first-confirm-visible")
        val oldAccountConfirm = compose.onNodeWithTag("confirm_cloud_backup").fetchSemanticsNode()
            .config[RecordActionSnapshot]
        compose.runOnIdle { cloud.value = cloud.value.copy(uid = "account-b", accountLabel = "계정 B") }
        compose.onNodeWithTag("confirm_cloud_backup").assertDoesNotExist()
        compose.runOnIdle { oldAccountConfirm.invoke(); assertTrue("Old account callback: ${actions.cloudBackups}", actions.cloudBackups.isEmpty()) }
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_cloud_backup").assertIsDisplayed()
        trace("second-confirm-visible-before-record-change")
        compose.runOnIdle { assertTrue("Before record change: ${actions.cloudBackups}", actions.cloudBackups.isEmpty()) }
        val oldRecordConfirm = compose.onNodeWithTag("confirm_cloud_backup").fetchSemanticsNode()
            .config[RecordActionSnapshot]
        compose.runOnIdle { record.value = second }
        compose.onNodeWithTag("confirm_cloud_backup").assertDoesNotExist()
        trace("record-changed-before-old-callback")
        compose.runOnIdle { assertTrue("Before old record callback: ${actions.cloudBackups}", actions.cloudBackups.isEmpty()) }
        compose.runOnIdle { oldRecordConfirm.invoke(); assertTrue("After old record callback: ${actions.cloudBackups}", actions.cloudBackups.isEmpty()) }
        trace("after-old-record-callback")
        // Removing Compose dialog semantics precedes WindowManager returning
        // input focus. Exercise a real touch only once the page owns that focus.
        diagnostic.appendText("after-record-change activityFocus=${compose.activity.hasWindowFocus()}\n")
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        val backup = compose.onNodeWithTag("backup_cloud_run").performScrollTo().assertIsDisplayed().assertIsEnabled()
        diagnostic.appendText("before-fresh-touch activityFocus=${compose.activity.hasWindowFocus()} bounds=${backup.fetchSemanticsNode().boundsInRoot}\n")
        captureDevice("record-backup-before-fresh-touch-fixture")
        backup.performClick()
        trace("fresh-touch-completed")
        compose.onNodeWithTag("confirm_cloud_backup").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(second.id to "account-b"), actions.cloudBackups) }
        compose.runOnIdle { cloud.value = cloud.value.copy(uid = "") }
        compose.onNodeWithTag("backup_cloud_run").assertDoesNotExist()
        compose.runOnIdle { assertFalse(state.value.records.first().synced); assertEquals(0, state.value.profile.mileage) }
    }

    @Test fun oldConfirmationCannotApproveAReopenedDialogAndDisposedPageCannotAct() {
        val record = recordPageFixture()
        val visible = mutableStateOf(true)
        val busy = mutableStateOf(false)
        val state = mutableStateOf(AppState(records = listOf(record)))
        val actions = RunmoniUiTest.FakeActions(state)
        compose.setContent { OfflineRecordPageFixture {
            if (visible.value) RunRecordScreen(record, actions,
                cloud = CloudAccountState(configured = true, uid = "account-a", accountLabel = "계정 A"),
                operationBusy = busy.value, onBack = {})
        } }
        fun clickCallback(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[RecordActionSnapshot]
        compose.onNodeWithTag("export_run").performScrollTo()
        val oldExport = clickCallback("export_run")
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        val oldBackup = clickCallback("confirm_cloud_backup")
        compose.onNodeWithTag("cancel_cloud_backup").performClick()
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("backup_cloud_run").performScrollTo().performClick()
        compose.runOnIdle { oldBackup.invoke(); assertTrue(actions.cloudBackups.isEmpty()) }
        compose.onNodeWithTag("confirm_cloud_backup").assertIsDisplayed().performClick()
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        val oldDelete = clickCallback("confirm_delete_run")
        compose.onNodeWithTag("cancel_delete_run").performClick()
        compose.waitUntil(30_000) { compose.activity.hasWindowFocus() }
        compose.onNodeWithTag("delete_run").performScrollTo().performClick()
        compose.runOnIdle { oldDelete.invoke(); assertTrue(actions.deletions.isEmpty()) }
        val currentDelete = clickCallback("confirm_delete_run")
        compose.runOnIdle { busy.value = true }
        compose.runOnIdle { currentDelete.invoke(); oldExport.invoke(); assertTrue(actions.deletions.isEmpty()); assertTrue(actions.exports.isEmpty()) }
        compose.runOnIdle { busy.value = false; visible.value = false }
        compose.onNodeWithTag("record_detail_page").assertDoesNotExist()
        compose.runOnIdle {
            currentDelete.invoke(); oldBackup.invoke(); oldExport.invoke()
            assertTrue(actions.deletions.isEmpty()); assertTrue(actions.exports.isEmpty())
            assertEquals(listOf(record.id to "account-a"), actions.cloudBackups)
        }
    }

    @Test fun currentStorageAndPendingCreditStaySeparateFromGoogleBackup() {
        val record = mutableStateOf(recordPageFixture())
        val state = mutableStateOf(AppState(records = listOf(record.value)))
        val pending = mutableStateOf(RunPersistenceState(initialized = true,
            issue = RunPersistenceIssue.CREDIT, creditPending = true, pendingCreditRunIds = setOf(record.value.id)))
        var retries = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun retryRunPersistence() { retries++ }
        }
        compose.setContent { OfflineRecordPageFixture {
            RunRecordScreen(record.value, actions, persistence = pending.value, onBack = {})
        } }
        compose.onNodeWithTag("record_interrupted").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("record_storage_status").performScrollTo().assertTextEquals("기기에 저장됨")
        compose.onNodeWithTag("retry_run_save").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.runOnIdle {
            record.value = record.value.copy(synced = true)
            pending.value = RunPersistenceState(initialized = true)
        }
        compose.onNodeWithTag("record_storage_status").performScrollTo().assertTextEquals("서버 동기화됨")
        compose.onNodeWithTag("run_persistence_notice").assertDoesNotExist()
        compose.onNodeWithTag("backup_cloud_run").assertDoesNotExist()
        noTextOverflow("record detail page with unavailable map tiles")
        captureDevice("record-detail-unavailable-map-fixture")
    }
}

/** Full measured window geometry, including real OS navigation/IME insets. */
private fun assertRecordActionAboveSystemNavigation(compose: OfflineMapComposeRule, tag: String) {
    val action = compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(48.dp).fetchSemanticsNode()
    val safe = compose.onNodeWithTag("record_safe_content").fetchSemanticsNode()
    val scroll = compose.onNodeWithTag("record_scroll_viewport").fetchSemanticsNode()
    val top = action.positionInWindow.y
    val bottom = top + action.size.height
    val safeBottom = safe.positionInWindow.y + safe.size.height
    val scrollBottom = scroll.positionInWindow.y + scroll.size.height
    val decor = compose.activity.window.decorView
    val insets = ViewCompat.getRootWindowInsets(decor)
    assertNotNull("Actual Android insets required for record actions", insets)
    val bars = insets!!.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
    val osBottom = decor.height - bars.bottom
    assertTrue("Full $tag must follow the fixed toolbar", top + .5f >= scroll.positionInWindow.y)
    assertTrue("Full $tag must fit in the scroll body", bottom <= scrollBottom + .5f)
    assertTrue("Full $tag must fit in safe content", bottom <= safeBottom + .5f)
    assertTrue("Full $tag must precede OS navigation/IME", bottom <= osBottom + .5f)
    assertTrue("Full $tag must follow the physical left system inset", action.positionInWindow.x >= bars.left - .5f)
    assertTrue("Full $tag must precede the physical right system inset",
        action.positionInWindow.x + action.size.width <= decor.width - bars.right + .5f)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    File(context.cacheDir, "design-review").apply { mkdirs() }.resolve("record-detail-window-bounds.txt")
        .appendText("$tag topPx=$top bottomPx=$bottom safeBottomPx=$safeBottom scrollBottomPx=$scrollBottom osBottomPx=$osBottom\n")
}

/** ComponentActivity/fake actions + unavailable tiles; no GPS, auth or DB writes. */
class RunRecordScreenNativeUiTest : RunmoniDesignTestBase() {
    @Test fun nativeLargeTextKeepsLowerActionsReachableAndBackCancelsBeforeLeaving() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = context.resources.configuration
        assertTrue("Actual native320dp required", config.screenWidthDp in 315..325)
        assertTrue("Actual system fontScale2 required", config.fontScale >= 1.9f)
        val record = recordPageFixture()
        val state = mutableStateOf(AppState(records = listOf(record)))
        val actions = RunmoniUiTest.FakeActions(state)
        var backs = 0
        compose.setContent { OfflineRecordPageFixture {
            RunRecordScreen(record, actions,
                cloud = CloudAccountState(configured = true, uid = "native-account", accountLabel = "검토 계정"),
                onBack = { backs++ })
        } }
        compose.onNodeWithTag("nav_home").assertDoesNotExist()
        compose.onNodeWithTag("backup_cloud_run").performScrollTo()
        assertRecordActionAboveSystemNavigation(compose, "backup_cloud_run")
        compose.onNodeWithTag("backup_cloud_run").performClick()
        compose.onNodeWithTag("cancel_cloud_backup").performClick()
        compose.onNodeWithTag("export_run").performScrollTo()
        assertRecordActionAboveSystemNavigation(compose, "export_run")
        compose.onNodeWithTag("export_run").performClick()
        compose.onNodeWithTag("delete_run").performScrollTo()
        assertRecordActionAboveSystemNavigation(compose, "delete_run")
        noTextOverflow("native320/font2 record detail footer, unavailable map tiles")
        captureDevice("record-detail-footer-native320-font2-fixture")
        compose.onNodeWithTag("delete_run").performClick()
        compose.onNodeWithTag("confirm_delete_run").assertIsDisplayed()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Actual Back key dispatch", device.pressKeyCode(KeyEvent.KEYCODE_BACK))
        compose.waitForIdle()
        compose.onNodeWithTag("confirm_delete_run").assertDoesNotExist()
        compose.onNodeWithTag("record_detail_page").assertIsDisplayed()
        compose.runOnIdle { assertTrue(actions.deletions.isEmpty()); assertTrue(actions.cloudBackups.isEmpty()); assertEquals(0, backs) }
        compose.onNodeWithTag("close_record").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, backs); assertEquals(listOf(record.id), actions.exports) }
    }
}
