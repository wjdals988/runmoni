package com.runmoni.app.integration

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.runmoni.app.*
import com.runmoni.app.data.*
import com.runmoni.app.domain.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Requires an isolated task-owned DEBUG app with no HTTP session/active run or
 * production Firebase configuration; fresh current backend on host loopback8789
 * and adb reverse tcp:8789 tcp:8789. Accounts/messages are disposable local data.
 * This checks MainActivity/HTTP/LocalWallet, not production Firebase or Google.
 */
@RunWith(AndroidJUnit4::class)
class CommunityApiFlowTest {
    @Test fun guestCrewPartyAndFreeClosetUseRealAppWithoutUploadingLocalRuns() {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        assertTrue("Use only the task-owned debug installation", BuildConfig.DEBUG)
        assertTrue("Do not replace a pre-existing HTTP login", SecureSession(app).read().isBlank())
        assertEquals("Do not interrupt an existing run", RunPhase.IDLE, app.runs.active.value.phase)
        assertFalse("This loopback test must not connect production Firebase", app.community.configured)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val wallet = LocalWallet(app)
        val before = wallet.read()
        val preferences = app.getSharedPreferences("runmoni", android.content.Context.MODE_PRIVATE)
        val creditedLedgerExisted = preferences.contains("creditedLocalRuns")
        val suffix = UUID.randomUUID().toString().take(8)
        val server = "http://127.0.0.1:8789"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppViewModel
        var initialized = false
        var originalUrl = ""
        var originalBackend = CommunityBackend.HTTP
        var localRunId = ""
        var visitor: PilotClient? = null
        try {
            scenario.onActivity {
                model = ViewModelProvider(it)[AppViewModel::class.java]
                initialized = true
                originalUrl = model.state.value.apiUrl
                originalBackend = model.state.value.communityBackend
                model.configureServer(server)
            }
            until { model.state.value.communityBackend == CommunityBackend.HTTP && model.state.value.serverConnected && !model.state.value.busy }
            assertTrue("Start the current task-owned loopback API first", model.state.value.serverConnected)
            if (model.state.value.signedIn) { scenario.onActivity { model.signOut() }; until { !model.state.value.signedIn && !model.state.value.busy } }
            scenario.onActivity { model.dismissError(); model.connectCommunity("테스트크루장-$suffix") }
            until { model.state.value.signedIn && model.state.value.communityGuest && !model.state.value.busy }
            assertNull(model.state.value.error)
            assertEquals(before.mileage, model.state.value.profile.mileage)
            assertEquals(before.ownedItems, model.state.value.profile.ownedItems)
            assertEquals(before.avatar, model.state.value.profile.avatar)
            scenario.onActivity { model.createCrew(NewCrew("테스트크루-$suffix", "Android 실행 검증용 크루", "서울", "6:30/km")) }
            until { model.state.value.lastCreatedCrewId.isNotBlank() && !model.state.value.busy }
            val crew = model.state.value.crews.first { it.id == model.state.value.lastCreatedCrewId }
            assertTrue(crew.joined && crew.isOpen && crew.isOwner)
            assertNull(crew.invitationCode)
            scenario.onActivity { model.createParty(NewParty(crew.id, "테스트파티-$suffix", PartyKind.REMOTE, System.currentTimeMillis()+3_600_000, "각자 출발", 5.0, "자유 페이스", 2)) }
            until { model.state.value.lastCreatedPartyId.isNotBlank() && !model.state.value.busy }
            val party = model.state.value.parties.first { it.id == model.state.value.lastCreatedPartyId }
            assertTrue(party.joined && party.isOrganizer)
            assertEquals(crew.id, party.crewId)
            val public = PilotClient(server)
            runBlocking {
                assertTrue(public.crews().any { it.id == crew.id })
                assertTrue(public.parties().any { it.id == party.id })
            }
            val owner = PilotClient(server, SecureSession(app).read())
            val guest = PilotClient(server).also { visitor = it }
            val otherPartyId = runBlocking {
                expectHttpStatus(422) { owner.createParty(NewParty(null, "독립 생성 거부-$suffix", PartyKind.REMOTE,
                    System.currentTimeMillis()+3_600_000, "각자 출발", 5.0, "자유 페이스", 2)) }
                guest.token = guest.connectCommunity("테스트참가-$suffix").getString("access_token")
                expectHttpStatus(403) { guest.request("POST", "/v1/parties/${party.id}/join") }
                assertFalse(guest.crews().any { it.joined })
                guest.request("POST", "/v1/crews/${crew.id}/join")
                guest.request("POST", "/v1/parties/${party.id}/join")
                guest.request("POST", "/v1/parties/${party.id}/join")
                assertTrue(guest.crews().any { it.id == crew.id && it.joined })
                guest.request("POST", "/v1/parties/${party.id}/messages", JSONObject().put("body", "파티 참가 검증 메시지"))
                owner.createParty(NewParty(crew.id, "보존할파티-$suffix", PartyKind.REMOTE,
                    System.currentTimeMillis()+7_200_000, "각자 출발", 3.0, "자유 페이스", 2)).getString("id")
            }
            scenario.onActivity { model.refreshCommunity() }
            until { !model.state.value.busy && model.state.value.parties.any { it.id == party.id && it.participantCount == 2 } }
            assertNull(model.state.value.error)
            fun tap(text: String) {
                val nodes = device.wait(Until.findObjects(By.text(text)), 30000)
                if (nodes.isNullOrEmpty()) {
                    val dir = File(app.cacheDir, "room-review").apply { mkdirs() }
                    device.takeScreenshot(File(dir, "native-failure.png"))
                }
                assertTrue("Real app exposes $text", !nodes.isNullOrEmpty())
                nodes!!.maxBy { it.visibleBounds.centerY() }.click()
                device.waitForIdle(1000)
            }
            fun capture(name: String) {
                val dir = File(app.cacheDir, "room-review").apply { mkdirs() }
                assertTrue(device.takeScreenshot(File(dir, "$name.png")))
            }
            tap("홈")
            assertTrue(device.wait(Until.hasObject(By.text("오늘도, 나답게.")), 30000))
            capture("native-home")
            tap("크루")
            assertTrue(device.wait(Until.hasObject(By.text(crew.name)), 30000))
            until { !model.state.value.busy }
            assertTrue("Crew creation is enabled after refresh", device.wait(Until.hasObject(
                By.text("크루 만들기").enabled(true)), 30000))
            capture("native-crew-list")
            tap(crew.name)
            assertTrue(device.wait(Until.hasObject(By.text(party.title)), 30000))
            capture("native-crew-detail")
            tap(party.title)
            assertTrue(device.wait(Until.hasObject(By.text("파티 공지 · 채팅")), 30000))
            capture("native-party-detail")
            tap("파티 공지 · 채팅")
            assertTrue(device.wait(Until.hasObject(By.text("파티 참가 검증 메시지")), 30000))
            capture("native-party-chat")
            scenario.onActivity { model.closeChat() }
            until { model.state.value.openedRoomId.isBlank() }
            tap("나")
            tap("미니미 꾸미기")
            tap("여자")
            until { !model.state.value.busy && wallet.read().avatar.gender == AvatarGender.FEMALE }
            tap("상의")
            tap("민트 러닝 반팔")
            until { !model.state.value.busy && wallet.read().avatar.top == 1 }
            val selected = wallet.read()
            assertEquals(before.avatar.copy(gender = AvatarGender.FEMALE).withItem("top", 1), selected.avatar)
            assertEquals(selected.avatar, model.state.value.profile.avatar)
            assertEquals(before.mileage, selected.mileage)
            assertEquals(before.ownedItems + "top:1", selected.ownedItems)
            // Wallet/StateFlow updates can precede Compose's accessibility tree.
            // Capture only after the same item visibly reports its selected state.
            assertTrue("Closet renders the saved-selection preview notice", device.wait(Until.hasObject(
                By.text("선택은 저장됐어요 · 미리보기는 기본형을 표시해요")), 30000))
            val diagnosticDir = File(app.cacheDir, "room-review").apply { mkdirs() }
            device.dumpWindowHierarchy(File(diagnosticDir, "native-closet-selected-window.xml"))
            capture("native-closet-selected-before-check")
            // Compose maps non-tab Selected semantics to checked. A selected
            // RadioButton is intentionally not accessibility-clickable, and its
            // name/status may be merged into the node rather than descendants.
            fun UiObject2.exposes(value: String): Boolean = text.orEmpty().contains(value) ||
                contentDescription.orEmpty().contains(value) || hasObject(By.textContains(value)) ||
                hasObject(By.descContains(value))
            var selectedMint: UiObject2? = null
            try {
                until(timeoutMillis = 30000) {
                    // The actual Compose accessibility tree exposes this
                    // selectable card as android.view.View, with checked=true.
                    selectedMint = device.findObjects(By.checked(true))
                        .firstOrNull { it.exposes("민트 러닝 반팔") && it.exposes("선택 보관 · 미리보기 준비 중") }
                    selectedMint != null
                }
            } finally {
                device.dumpWindowHierarchy(File(diagnosticDir, "native-closet-selected-after-check-window.xml"))
                capture("native-closet-selected-after-check")
            }
            assertNotNull("Mint RadioButton is checked and exposes its name and saved-selection status", selectedMint)
            capture("native-closet-female-free-selection")
            tap("닫기")
            // Finishing a run opens its summary; keep this fixture after UI navigation.
            runBlocking { app.runs.begin(); localRunId = app.runs.active.value.id; app.runs.finish() }
            val work = OneTimeWorkRequestBuilder<RunSyncWorker>().build()
            val manager = WorkManager.getInstance(app)
            manager.enqueue(work)
            runBlocking { withTimeout(60000) { manager.getWorkInfoByIdFlow(work.id).first { it?.state == WorkInfo.State.SUCCEEDED } } }
            assertFalse(runBlocking { app.runs.record(localRunId)!! }.synced)
            assertEquals(0, runBlocking { owner.request("GET", "/v1/runs").getJSONArray("items").length() })
            scenario.onActivity { model.deleteParty(party.id) }
            until { !model.state.value.busy && model.state.value.parties.none { it.id == party.id } }
            assertNull(model.state.value.error)
            assertTrue(model.state.value.crews.any { it.id == crew.id && it.joined })
            assertTrue(model.state.value.parties.any { it.id == otherPartyId })
            assertNotNull(runBlocking { app.runs.record(localRunId) })
            assertEquals(selected, wallet.read())
            runBlocking {
                assertFalse(public.parties().any { it.id == party.id })
                assertTrue(owner.parties().any { it.id == otherPartyId })
                expectHttpStatus(404) { guest.request("POST", "/v1/parties/${party.id}/join") }
                expectHttpStatus(404) { guest.request("POST", "/v1/parties/${party.id}/messages", JSONObject().put("body", "삭제 후 거부")) }
            }
        } finally {
            try {
                if (localRunId.isNotBlank()) runBlocking {
                    if (app.runs.active.value.id == localRunId) app.runs.finish()
                    app.runs.delete(localRunId)
                }
                visitor?.let { runBlocking { runCatching { it.request("DELETE", "/v1/auth/session") } } }
                if (initialized) {
                    scenario.onActivity { model.closeChat(); model.signOut() }
                    until { !model.state.value.signedIn && !model.state.value.busy }
                    scenario.onActivity { model.configureServer(originalUrl) }
                    until { !model.state.value.busy }
                    if (originalBackend == CommunityBackend.FIREBASE) {
                        scenario.onActivity { model.useFirebaseCommunity() }
                        until { model.state.value.communityBackend == originalBackend && !model.state.value.busy }
                    }
                    assertEquals(originalUrl, model.state.value.apiUrl)
                    assertEquals(originalBackend, model.state.value.communityBackend)
                    scenario.onActivity { model.dismissError() }
                }
            } finally {
                try {
                    wallet.update { before }
                    if (localRunId.isNotBlank()) {
                        // Remove only this fixture's zero-distance reward marker;
                        // preserve every pre-existing ledger entry and preference.
                        val remaining = preferences.getStringSet("creditedLocalRuns", emptySet()).orEmpty().toSet() - localRunId
                        check(preferences.edit().apply {
                            if (!creditedLedgerExisted && remaining.isEmpty()) remove("creditedLocalRuns")
                            else putStringSet("creditedLocalRuns", remaining)
                        }.commit())
                    }
                } finally { scenario.close() }
            }
        }
    }
    private suspend fun expectHttpStatus(status: Int, action: suspend () -> Any) {
        try { action(); fail("Expected HTTP$status from the local policy boundary") }
        catch (failure: PilotException) { assertEquals(status, failure.status) }
    }
    private fun until(timeoutMillis: Long = 60000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime()+timeoutMillis
        while (!condition() && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
        assertTrue("State arrived before deadline", condition())
    }
}
