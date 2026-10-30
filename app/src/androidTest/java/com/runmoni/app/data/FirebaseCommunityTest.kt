package com.runmoni.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.runmoni.app.domain.NewCrew
import com.runmoni.app.domain.NewParty
import com.runmoni.app.domain.PartyKind
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SDK against task-owned demo Auth/Firestore emulators, never real Google/phone/prod. */
@RunWith(AndroidJUnit4::class)
class FirebaseCommunityTest {
    @Test fun anonymousClientsShareRoomsAndLiveMessagesWithRulesEnforced() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.currentTimeMillis().toString()
        val apps = mutableListOf<FirebaseApp>()
        val repositories = mutableListOf<FirebaseCommunityRepository>()
        fun client(name: String): FirebaseCommunityRepository {
            val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-runmoni")
                .setApplicationId("1:123:android:abc123").setApiKey("fake-emulator-api-key").build(), "room-$name-$suffix")
            apps += app
            FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
            FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
            return FirebaseCommunityRepository(context, app).also { repositories += it }
        }
        try {
            val a = client("a"); val b = client("b"); val c = client("c")
            a.connect("검토러너A"); b.connect("검토러너B"); c.connect("검토러너C")
            assertTrue(a.uid.isNotBlank()); assertNotEquals(a.uid, b.uid)
            val crew = a.createCrew(NewCrew("Firebase 검토 방", "에뮬레이터에서 확인", "서울", "자유 페이스"))
            b.join(crew.id); c.join(crew.id)
            val discovered = b.snapshot().crews.first { it.id == crew.id }
            assertTrue(discovered.joined); assertFalse(discovered.isOwner); assertEquals(3, discovered.memberCount)
            assertEquals(3, a.members(crew.id).size)
            val party = b.createParty(NewParty(crew.id, "Firebase 검토 파티", PartyKind.REMOTE,
                System.currentTimeMillis() + 3_600_000, "각자 출발", 3.0, "자유 페이스", 2))
            a.join(party.id)
            assertTrue(runCatching { c.join(party.id) }.isFailure)
            val events = Channel<FirebaseRoomMessages>(Channel.UNLIMITED)
            val listener = launch { b.messages(crew.id).collect { events.send(it) } }
            try {
                withTimeout(30_000) { while (events.receive().fromCache) { /* wait for actual server */ } }
                a.sendMessage(crew.id, "실시간 확인 $suffix", true)
                val received = withTimeout(30_000) {
                    var value: FirebaseRoomMessages
                    do { value = events.receive() } while (value.messages.none { it.body == "실시간 확인 $suffix" })
                    value
                }
                assertTrue(received.messages.any { it.notice && it.author == "검토러너A" })
                assertFalse(b.canAnnounce(crew.id))
                assertTrue(runCatching { b.sendMessage(crew.id, "권한 없는 공지", true) }.isFailure)
                a.setDj(crew.id, b.uid, true)
                assertTrue(b.canAnnounce(crew.id))
                b.sendMessage(crew.id, "DJ 공지", true)
            } finally { listener.cancelAndJoin(); events.close() }
            b.leave(crew.id)
            assertFalse(b.snapshot().crews.first { it.id == crew.id }.joined)
            a.cancelParty(party.id) // Parent crew owner can cancel a linked party.
            assertTrue(b.snapshot().parties.first { it.id == party.id }.cancelled)
            assertTrue(runCatching { b.leave(party.id) }.isFailure) // Organizer stays owner.
            a.signOut()
            assertTrue(a.uid.isBlank()); assertFalse(a.snapshot().signedIn)
            assertTrue(runCatching { a.createCrew(NewCrew("불가", "", "", "자유")) }.isFailure)
        } finally {
            repositories.forEach { it.signOut(); it.close() }
            apps.forEach { FirebaseFirestore.getInstance(it).terminate().await(); it.delete() }
        }
    }
}
