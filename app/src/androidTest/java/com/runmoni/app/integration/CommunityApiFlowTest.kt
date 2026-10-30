package com.runmoni.app.integration

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
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

/** Requires task-owned loopback server8789 and adb reverse. Accounts/messages are disposable test data. */
@RunWith(AndroidJUnit4::class)
class CommunityApiFlowTest {
    @Test fun guestCreatesDiscoversAndJoinsRealRoomsWithoutUploadingLocalRuns() {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val wallet = LocalWallet(app)
        val before = wallet.read()
        val suffix = UUID.randomUUID().toString().take(8)
        val server = "http://127.0.0.1:8789"
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppViewModel
        var initialized = false
        var originalUrl = ""
        var localRunId = ""
        try {
            scenario.onActivity { model = ViewModelProvider(it)[AppViewModel::class.java]; initialized = true; originalUrl = model.state.value.apiUrl; model.configureServer(server) }
            until { !model.state.value.busy }
            if (model.state.value.signedIn) { scenario.onActivity { model.signOut() }; until { !model.state.value.signedIn && !model.state.value.busy } }
            scenario.onActivity { model.dismissError(); model.connectCommunity("테스트방장-$suffix") }
            until { model.state.value.signedIn && model.state.value.communityGuest && !model.state.value.busy }
            assertNull(model.state.value.error)
            assertEquals(before.mileage, model.state.value.profile.mileage)
            assertEquals(before.ownedItems, model.state.value.profile.ownedItems)
            assertEquals(before.avatar, model.state.value.profile.avatar)
            scenario.onActivity { model.createCrew(NewCrew("테스트크루-$suffix", "Android 실행 검증용 방", "서울", "6:30/km")) }
            until { model.state.value.lastCreatedCrewId.isNotBlank() && !model.state.value.busy }
            val crew = model.state.value.crews.first { it.id == model.state.value.lastCreatedCrewId }
            assertTrue(crew.joined && crew.isOpen && crew.isOwner)
            assertNull(crew.invitationCode)
            scenario.onActivity { model.createParty(NewParty(null, "테스트파티-$suffix", PartyKind.REMOTE, System.currentTimeMillis()+3_600_000, "각자 출발", 5.0, "자유 페이스", 2)) }
            until { model.state.value.lastCreatedPartyId.isNotBlank() && !model.state.value.busy }
            val party = model.state.value.parties.first { it.id == model.state.value.lastCreatedPartyId }
            assertTrue(party.joined && party.isOrganizer)
            assertFalse(model.state.value.crews.any { it.id == party.crewId })
            val public = PilotClient(server)
            runBlocking {
                assertTrue(public.crews().any { it.id == crew.id })
                assertTrue(public.parties().any { it.id == party.id })
            }
            val visitor = PilotClient(server)
            runBlocking {
                visitor.token = visitor.connectCommunity("테스트참가-$suffix").getString("access_token")
                visitor.request("POST", "/v1/parties/${party.id}/join")
                visitor.request("POST", "/v1/parties/${party.id}/join")
                assertFalse(visitor.crews().any { it.joined })
                visitor.request("POST", "/v1/parties/${party.id}/messages", JSONObject().put("body", "방 입장 검증 메시지"))
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
            tap("크루")
            assertTrue(device.wait(Until.hasObject(By.text(crew.name)), 30000))
            capture("native-room-list")
            tap(crew.name)
            capture("native-crew-room")
            tap("방 목록")
            tap("파티 방")
            tap(party.title)
            capture("native-party-room")
            tap("파티 공지 · 채팅")
            assertTrue(device.wait(Until.hasObject(By.text("방 입장 검증 메시지")), 30000))
            capture("native-party-chat")
            // Finishing a run opens its summary; keep this fixture after room UI navigation.
            runBlocking { app.runs.begin(); localRunId = app.runs.active.value.id; app.runs.finish() }
            val work = OneTimeWorkRequestBuilder<RunSyncWorker>().build()
            val manager = WorkManager.getInstance(app)
            manager.enqueue(work)
            runBlocking { withTimeout(60000) { manager.getWorkInfoByIdFlow(work.id).first { it?.state == WorkInfo.State.SUCCEEDED } } }
            assertFalse(runBlocking { app.runs.record(localRunId)!! }.synced)
            val owner = PilotClient(server, SecureSession(app).read())
            assertEquals(0, runBlocking { owner.request("GET", "/v1/runs").getJSONArray("items").length() })
        } finally {
            if (localRunId.isNotBlank()) runBlocking { app.runs.delete(localRunId) }
            if (initialized) {
                scenario.onActivity { model.closeChat(); model.signOut() }
                until { !model.state.value.signedIn && !model.state.value.busy }
                scenario.onActivity { model.configureServer(originalUrl) }
            }
            wallet.update { before }
            scenario.close()
        }
    }
    private fun until(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime()+60000
        while (!condition() && SystemClock.elapsedRealtime()<end) Thread.sleep(100)
        assertTrue("State arrived before deadline", condition())
    }
}
