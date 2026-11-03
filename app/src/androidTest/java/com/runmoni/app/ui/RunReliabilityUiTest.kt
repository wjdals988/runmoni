package com.runmoni.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.runmoni.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic durability states on real Compose. Not storage, GPS, Google or phone evidence. */
class RunReliabilityUiTest : RunmoniDesignTestBase() {
    @Test fun recoveryReadFailureBlocksNewRunUntilTheRetrySucceeds() {
        val state = mutableStateOf(AppState(runPersistence = RunPersistenceState()))
        var retries = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun retryRunPersistence() {
                retries++
                state.value = state.value.copy(runPersistence = RunPersistenceState(initialized = true))
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithTag("start_run").assertIsDisplayed().assertIsNotEnabled()

        compose.runOnIdle {
            state.value = state.value.copy(runPersistence = RunPersistenceState(issue = RunPersistenceIssue.RECOVERY))
        }
        compose.onNodeWithTag("run_persistence_notice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(state.value.runPersistence.message!!).assertExists()
        compose.onNodeWithTag("retry_run_save").performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        assertControlAboveNavigation("start_run", 64)
        noTextOverflow("recovery read failure 320 / 2")
        captureReliability("run-recovery-read-failed-320-200")

        compose.onNodeWithTag("retry_run_save").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.onNodeWithTag("start_run").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("retry_run_save").assertDoesNotExist()
    }

    @Test fun checkpointFailureExplainsStoragePauseAndRetriesOnlyStorage() {
        val state = mutableStateOf(AppState(
            activeRun = ActiveRun(id = "checkpoint-ui", phase = RunPhase.PAUSED,
                distanceMeters = 1250.0, activeDurationMillis = 450_000),
            runPersistence = RunPersistenceState(issue = RunPersistenceIssue.CHECKPOINT,
                initialized = true, unsavedPointCount = 1),
        ))
        val delegate = RunmoniUiTest.FakeActions(state)
        var retries = 0
        val actions = object : AppActions by delegate {
            override fun retryRunPersistence() {
                retries++
                state.value = state.value.copy(runPersistence = state.value.runPersistence.copy(saving = true))
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNode(hasText("기록 저장 확인 필요") and hasAnyAncestor(hasTestTag("run_persistence_notice"))).assertExists()
        compose.onNodeWithText("직접 일시정지했어요. 다시 달릴 때 ‘계속 달리기’를 누르세요.").assertDoesNotExist()
        compose.onNodeWithText(state.value.runPersistence.message!!).assertExists()
        compose.onNodeWithTag("retry_run_save").performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        noTextOverflow("checkpoint failure 320 / 2")
        captureReliability("run-checkpoint-failed-320-200")

        compose.onNodeWithTag("retry_run_save").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, delegate.resumes)
            assertEquals(0, delegate.finishes)
            assertEquals(1250.0, state.value.activeRun.distanceMeters, 0.0)
        }
        compose.onNodeWithTag("resume_run").assertIsNotEnabled()
        assertAbsentOrDisabled("retry_run_save")

        compose.runOnIdle {
            state.value = state.value.copy(runPersistence = RunPersistenceState(initialized = true,
                lastSavedAtMillis = System.currentTimeMillis()))
        }
        compose.onNodeWithTag("retry_run_save").assertDoesNotExist()
        compose.onNodeWithTag("resume_run").assertIsEnabled()
        assertControlAboveNavigation("resume_run", 56)
    }

    @Test fun unfinishedFinalSaveCannotResumeOrSubmitAnotherFinish() {
        val state = mutableStateOf(AppState(
            activeRun = ActiveRun(id = "finish-ui", phase = RunPhase.PAUSED,
                distanceMeters = 3210.0, activeDurationMillis = 1_200_000),
            runPersistence = RunPersistenceState(issue = RunPersistenceIssue.FINISH,
                initialized = true, finishing = true),
        ))
        val delegate = RunmoniUiTest.FakeActions(state)
        var retries = 0
        val actions = object : AppActions by delegate {
            override fun retryRunPersistence() {
                retries++
                state.value = state.value.copy(runPersistence = state.value.runPersistence.copy(saving = true))
            }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.onNodeWithText("러닝 마무리 중").assertExists()
        compose.onNodeWithText(state.value.runPersistence.message!!).assertExists()
        assertAbsentOrDisabled("resume_run")
        assertAbsentOrDisabled("finish_run")
        compose.onNodeWithTag("retry_run_save").performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        noTextOverflow("finish save failure 320 / 2")
        captureReliability("run-finish-failed-320-200")

        compose.onNodeWithTag("retry_run_save").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(0, delegate.resumes)
            assertEquals(0, delegate.finishes)
        }
        assertAbsentOrDisabled("retry_run_save")
        assertAbsentOrDisabled("resume_run")
        assertAbsentOrDisabled("finish_run")
        compose.onNodeWithText("나의 러닝 기록").assertDoesNotExist()
    }

    @Test fun savedRecordKeepsCreditRetrySeparateFromCloudBackup() {
        val stamp = System.currentTimeMillis()
        val id = "saved-credit-ui"
        val record = RunRecord(id, stamp - 450_000, stamp, distanceMeters = 1250.0,
            activeDurationMillis = 450_000, mileage = 125)
        val state = mutableStateOf(AppState(activeRun = ActiveRun(id = id, phase = RunPhase.RUNNING,
            startedAtMillis = record.startedAtMillis, distanceMeters = record.distanceMeters,
            activeDurationMillis = record.activeDurationMillis)))
        var retries = 0
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun retryRunPersistence() { retries++ }
        }
        compose.setContent { ViewportBox(Viewport(320, 2f)) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("nav_run").performClick()
        compose.runOnIdle {
            state.value = state.value.copy(activeRun = ActiveRun(), records = listOf(record),
                runPersistence = RunPersistenceState(issue = RunPersistenceIssue.CREDIT,
                    initialized = true, creditPending = true, pendingCreditRunIds = setOf(id)))
        }
        compose.onNodeWithText("나의 러닝 기록").assertIsDisplayed()
        compose.onNode(hasText(state.value.runPersistence.message!!) and hasAnyAncestor(hasTestTag("form_dialog"))).assertExists()
        compose.onNode(hasTestTag("retry_run_save") and hasAnyAncestor(hasTestTag("form_dialog")))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("기기에 저장됨").assertExists()
        compose.onNodeWithText("백업 완료", substring = true).assertDoesNotExist()
        noTextOverflow("saved record credit pending 320 / 2")
        captureReliability("saved-record-credit-pending-320-200")
        compose.onNode(hasTestTag("retry_run_save") and hasAnyAncestor(hasTestTag("form_dialog"))).performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(listOf(record), state.value.records)
        }
    }

    @Test fun currentStorageFailureTakesPriorityOverOlderPendingCredit() {
        val persistence = RunPersistenceState(issue = RunPersistenceIssue.CHECKPOINT,
            initialized = true, creditPending = true, pendingCreditRunIds = setOf("older-credit-run"))
        compose.setContent {
            ViewportBox(Viewport(320, 2f)) {
                RunmoniTheme { ScreenContent { RunPersistenceNotice(persistence) {} } }
            }
        }
        compose.onNodeWithText("기록 저장 확인 필요").assertExists()
        compose.onNodeWithText(persistence.message!!).assertExists()
        compose.onNodeWithText("기록은 저장됐어요 · 포인트 처리 필요").assertDoesNotExist()
        compose.onNodeWithTag("retry_run_save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        noTextOverflow("current storage issue with older pending credit 320 / 2")
    }

    @Test fun linkingCommunityGoogleRequiresConfirmationAndKeepsExistingDataOnConflict() {
        val viewport = mutableStateOf(Viewport())
        val profile = Profile(name = "기존 러너", mileage = 321,
            avatar = AvatarStyle(gender = AvatarGender.FEMALE, top = 1),
            ownedItems = AvatarCatalogue.defaultOwned + "top:1")
        val crews = listOf(Crew("owned-crew-ui", "기존 크루", "연결 전부터 참여한 크루",
            joined = true, isOwner = true))
        val state = mutableStateOf(AppState(profile = profile, crews = crews,
            communityBackend = CommunityBackend.FIREBASE, communityGuest = true,
            signedIn = true, serverConnected = true, cloud = CloudAccountState(configured = true)))
        var links = 0
        var restores = 0
        var summarySignIns = 0
        val conflict = "이미 다른 크루 프로필에 연결한 계정이에요. 현재 프로필은 그대로 유지돼요."
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun linkCommunityGoogle() {
                links++
                state.value = state.value.copy(communityGoogleLink = CommunityGoogleLinkState(status = conflict))
            }
            override fun restoreCommunityGoogle() { restores++ }
            override fun signInGoogle() { summarySignIns++ }
        }
        compose.setContent { ViewportBox(viewport.value) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("restore_community_google").assertDoesNotExist()
        compose.onNodeWithTag("link_community_google").performScrollTo().assertIsEnabled()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("confirm_link_community_google").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, links); assertEquals(0, restores); assertEquals(0, summarySignIns) }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle {
            assertEquals(0, links)
            assertEquals(profile, state.value.profile)
            assertEquals(crews, state.value.crews)
        }

        compose.onNodeWithTag("link_community_google").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_link_community_google").performClick()
        compose.onNodeWithTag("community_google_status").performScrollTo().assertTextEquals(conflict)
        compose.runOnIdle {
            assertEquals(1, links)
            assertEquals(0, restores)
            assertEquals(0, summarySignIns)
            assertEquals(profile, state.value.profile)
            assertEquals(crews, state.value.crews)
            assertTrue(state.value.signedIn)
        }
        captureReliability("community-google-link-conflict-fixture-360-100")

        compose.runOnIdle {
            viewport.value = Viewport(320, 2f)
            state.value = state.value.copy(communityGoogleLink = state.value.communityGoogleLink.copy(busy = true))
        }
        compose.onNodeWithTag("link_community_google").performScrollTo().assertIsNotEnabled()
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("sign_out").assertIsNotEnabled()
        noTextOverflow("community Google link pending 320 / 2")
    }

    @Test fun restoringCommunityGoogleIsExplicitAndSeparateFromCreatingOrLinkingAProfile() {
        val profile = Profile(name = "기기 러너", mileage = 456,
            avatar = AvatarStyle(gender = AvatarGender.FEMALE))
        val state = mutableStateOf(AppState(profile = profile,
            communityBackend = CommunityBackend.FIREBASE, communityGuest = true,
            signedIn = false, cloud = CloudAccountState(configured = true)))
        var links = 0
        var restores = 0
        var summarySignIns = 0
        var newProfiles = 0
        val pendingStatus = "기존 크루 프로필을 확인하고 있어요"
        val actions = object : AppActions by RunmoniUiTest.FakeActions(state) {
            override fun linkCommunityGoogle() { links++ }
            override fun restoreCommunityGoogle() {
                restores++
                state.value = state.value.copy(communityGoogleLink = CommunityGoogleLinkState(
                    busy = true, status = pendingStatus))
            }
            override fun signInGoogle() { summarySignIns++ }
            override fun connectCommunity(name: String) { newProfiles++ }
        }
        compose.setContent { ViewportBox(Viewport()) { RunmoniApp(state.value, actions) } }
        compose.onNodeWithTag("settings_button").performClick()
        compose.onNodeWithTag("link_community_google").assertDoesNotExist()
        compose.onNodeWithTag("restore_community_google").performScrollTo().assertIsEnabled()
            .assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("confirm_restore_community_google").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, restores); assertEquals(0, links); assertEquals(0, summarySignIns); assertEquals(0, newProfiles) }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, restores); assertEquals(profile, state.value.profile) }

        compose.onNodeWithTag("restore_community_google").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_restore_community_google").performClick()
        compose.onNodeWithTag("community_google_status").performScrollTo().assertTextEquals(pendingStatus)
        compose.onNodeWithTag("restore_community_google").assertIsNotEnabled()
        compose.onNodeWithTag("open_sign_in").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(1, restores)
            assertEquals(0, links)
            assertEquals(0, summarySignIns)
            assertEquals(0, newProfiles)
            assertEquals(profile, state.value.profile)
            assertTrue(state.value.crews.isEmpty())
            assertTrue(!state.value.signedIn)
        }
        captureReliability("community-google-restore-pending-fixture-360-100")
    }

    private fun captureReliability(name: String) {
        capture(name)
        captureDevice("$name-device")
    }

    private fun assertAbsentOrDisabled(tag: String) {
        if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(tag).assertIsNotEnabled()
        }
    }

    private fun assertControlAboveNavigation(tag: String, minimumHeightDp: Int) {
        compose.onNodeWithTag(tag).assertIsDisplayed().assertHeightIsAtLeast(minimumHeightDp.dp)
        val button = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("nav_run").getUnclippedBoundsInRoot()
        assertTrue("Entire $tag must be above navigation: $button / $navigation", button.bottom <= navigation.top)
    }
}
