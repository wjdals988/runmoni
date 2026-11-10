package com.runmoni.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.runmoni.app.domain.NewCrew
import com.runmoni.app.domain.NewParty
import com.runmoni.app.domain.PartyKind
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
            val nickA = "검토A${suffix.takeLast(6)}"
            a.connect(nickA); b.connect("검토B${suffix.takeLast(6)}"); c.connect("검토C${suffix.takeLast(6)}")
            assertTrue(a.uid.isNotBlank()); assertNotEquals(a.uid, b.uid)
            val crew = a.createCrew(NewCrew("Firebase 검토 크루", "에뮬레이터에서 확인", "서울", "자유 페이스"))
            b.join(crew.id); c.join(crew.id)
            val discovered = b.snapshot().crews.first { it.id == crew.id }
            assertTrue(discovered.joined); assertFalse(discovered.isOwner); assertEquals(3, discovered.memberCount)
            assertEquals(3, a.members(crew.id).size)
            val party = b.createParty(NewParty(crew.id, "Firebase 검토 파티", PartyKind.REMOTE,
                System.currentTimeMillis() + 3_600_000, "각자 출발", 3.0, "자유 페이스", 2))
            assertTrue(c.partiesForCrew(crew.id).any { it.id == party.id && !it.joined })
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
                assertTrue(received.messages.any { it.notice && it.author == nickA })
                assertFalse(b.canAnnounce(crew.id))
                assertTrue(runCatching { b.sendMessage(crew.id, "권한 없는 공지", true) }.isFailure)
                a.setDj(crew.id, b.uid, true)
                assertTrue(b.canAnnounce(crew.id))
                b.sendMessage(crew.id, "DJ 공지", true)
            } finally { listener.cancelAndJoin(); events.close() }
            b.sendMessage(party.id, "삭제 전 파티 기록", false)
            b.leave(crew.id)
            assertFalse(b.snapshot().crews.first { it.id == crew.id }.joined)
            // Public metadata remains visible, but a party membership/organizer
            // role cannot preserve private access after leaving its parent crew.
            assertTrue(b.partiesForCrew(crew.id).any { it.id == party.id })
            assertTrue(runCatching { withTimeout(5_000) { b.messages(party.id).first() } }.isFailure)
            assertTrue(runCatching { b.canAnnounce(party.id) }.isFailure)
            delay(2_100) // Ensure rejection below is not merely the message throttle.
            assertTrue(runCatching { b.sendMessage(party.id, "부모 크루 탈퇴 뒤 공지", true) }.isFailure)
            a.cancelParty(party.id) // Parent crew owner can cancel a linked party.
            assertTrue(b.snapshot().parties.first { it.id == party.id }.cancelled)
            assertTrue(runCatching { b.leave(party.id) }.isFailure) // Organizer stays owner.
            assertTrue(runCatching { c.deleteParty(party.id) }.isFailure)
            a.deleteParty(party.id) // Parent owner can remove a different organizer's party.
            assertFalse(a.snapshot().parties.any { it.id == party.id })
            assertFalse(b.snapshot().parties.any { it.id == party.id })
            assertFalse(c.partiesForCrew(crew.id).any { it.id == party.id })
            assertTrue(runCatching { b.messages(party.id).first() }.isFailure)
            assertTrue(runCatching { c.join(party.id) }.isFailure)
            val storeA = FirebaseFirestore.getInstance(apps[0])
            val storeB = FirebaseFirestore.getInstance(apps[1])
            // Maintenance has a separate one-second budget. Verify eventual
            // cleanup without turning an optional timeout into a false failure.
            withTimeout(30_000) {
                while (true) {
                    a.snapshot(); b.snapshot()
                    val ownA = storeA.collection("communityProfiles").document(a.uid).get(Source.SERVER).await()
                    val ownB = storeB.collection("communityProfiles").document(b.uid).get(Source.SERVER).await()
                    if ((ownA.get("joinedRoomIds") as? List<*>)?.contains(party.id) != true &&
                        (ownB.get("joinedRoomIds") as? List<*>)?.contains(party.id) != true) break
                    delay(100)
                }
            }
            val tombstone = storeA.collection("communityRooms").document(party.id).get(Source.SERVER).await()
            assertEquals("deleted", tombstone.getString("status"))
            assertEquals(0L, tombstone.getLong("memberCount") ?: -1L)
            assertFalse(storeB.collection("communityRooms").document(party.id).collection("members").document(b.uid).get(Source.SERVER).await().exists())
            assertTrue(a.snapshot().crews.any { it.id == crew.id && it.isOwner })
            a.signOut()
            assertTrue(a.uid.isBlank()); assertFalse(a.snapshot().signedIn)
            assertTrue(runCatching { a.createCrew(NewCrew("불가", "", "", "자유")) }.isFailure)
        } finally {
            repositories.forEach { it.signOut(); it.close() }
            apps.forEach { FirebaseFirestore.getInstance(it).terminate().await(); it.delete() }
        }
    }
}
