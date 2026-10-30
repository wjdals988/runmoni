package com.runmoni.app.integration

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.AppViewModel
import com.runmoni.app.MainActivity
import com.runmoni.app.domain.NewCrew
import com.runmoni.app.domain.NewParty
import com.runmoni.app.domain.PartyKind
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Requires loopback uvicorn + adb reverse 8787. This uses the same model/API path as the UI. */
@RunWith(AndroidJUnit4::class)
class PilotApiFlowTest {
    @Test fun invitedAccountCrewPartyAndScopedMessagesRoundTripThroughRealApi() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        lateinit var model: AppViewModel
        var modelInitialized = false
        val suffix = UUID.randomUUID().toString().take(8)
        try {
            scenario.onActivity {
                model = ViewModelProvider(it)[AppViewModel::class.java]
                modelInitialized = true
                model.configureServer("http://127.0.0.1:8787")
                model.signIn("runmoni-local-test", "검증러너-$suffix")
            }
            until { model.state.value.signedIn && model.state.value.serverConnected && !model.state.value.busy }
            assertNull(model.state.value.error)
            scenario.onActivity { model.createCrew(NewCrew("검증크루-$suffix", "자동 API 검증용", "서울", "6:00/km")) }
            until { model.state.value.crews.any { it.name == "검증크루-$suffix" && it.joined && it.isOwner } && !model.state.value.busy }
            val crew = model.state.value.crews.first { it.name == "검증크루-$suffix" }
            assertNull(crew.invitationCode)
            scenario.onActivity { model.createParty(NewParty(crew.id, "검증파티-$suffix", PartyKind.REMOTE, System.currentTimeMillis() + 900000, "각자 출발", 3.0, "6:00/km", 2)) }
            until { model.state.value.parties.any { it.title == "검증파티-$suffix" } && !model.state.value.busy }
            val party = model.state.value.parties.first { it.title == "검증파티-$suffix" }
            assertTrue(party.joined)
            assertEquals(1, party.participantCount)
            assertEquals(2, party.capacity)
            scenario.onActivity { model.openChat("crew:${crew.id}", crew.name) }
            until { !model.state.value.busy }
            scenario.onActivity { model.sendMessage("크루 실제 HTTP 공지 $suffix", true) }
            until { model.state.value.messages.any { it.body == "크루 실제 HTTP 공지 $suffix" && it.notice } && !model.state.value.busy }
            scenario.onActivity { model.openChat("party:${party.id}", party.title) }
            until { !model.state.value.busy }
            scenario.onActivity { model.sendMessage("파티 실제 HTTP 채팅 $suffix", false) }
            until { model.state.value.messages.any { it.body == "파티 실제 HTTP 채팅 $suffix" && !it.notice } && !model.state.value.busy }
            scenario.onActivity { model.updateProfile("변경러너-$suffix") }
            until { model.state.value.profile.name == "변경러너-$suffix" && !model.state.value.busy }
            assertFalse("Unconfigured voice must not fake a connection", model.state.value.audio.connected)
            scenario.onActivity { model.cancelParty(party.id) }
            until { model.state.value.parties.none { it.id == party.id } && !model.state.value.busy }
        } finally {
            if (modelInitialized) {
                scenario.onActivity { model.closeChat(); model.signOut() }
                until { !model.state.value.signedIn && !model.state.value.busy }
            }
            scenario.close()
        }
    }
    private fun until(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("API round trip completed before timeout", condition())
    }
}
