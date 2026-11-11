package com.runmoni.app.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.runmoni.app.domain.NicknameAvailability
import com.runmoni.app.domain.NewCrew
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** Task-owned demo emulators only; no real Google sign-in or production writes. */
@RunWith(AndroidJUnit4::class)
class FirebaseNicknameTest {
    @Test fun checkingNeverCreatesIdentityAndAtomicSaveRejectsAnAvailabilityRace() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime().toString(36).takeLast(6)
        val apps = mutableListOf<FirebaseApp>()
        val repositories = mutableListOf<FirebaseCommunityRepository>()
        fun client(label: String): FirebaseCommunityRepository {
            val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-runmoni")
                .setApplicationId("1:123:android:abc123").setApiKey("fake-emulator-api-key").build(), "nickname-$label-$suffix")
            apps += app
            FirebaseAuth.getInstance(app).useEmulator("10.0.2.2", 9099)
            FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
            return FirebaseCommunityRepository(context, app).also { repositories += it }
        }
        try {
            val a = client("a"); val b = client("b"); val c = client("c")
            assertEquals(NicknameAvailability.UNVERIFIED, a.checkNickname("Runner$suffix").availability)
            assertNull(FirebaseAuth.getInstance(apps[0]).currentUser)
            assertNull(a.presenceFirestore())
            a.connect("검토A$suffix"); b.connect("검토B$suffix")
            assertTrue(runCatching { c.connect("검토A$suffix") }.isFailure)
            val pendingUid = FirebaseAuth.getInstance(apps[2]).currentUser?.uid.orEmpty()
            assertTrue(pendingUid.isNotBlank())
            assertEquals("", c.uid)
            assertNull(c.presenceFirestore())
            assertFalse(c.snapshot().signedIn)
            assertEquals(pendingUid, FirebaseAuth.getInstance(apps[2]).currentUser?.uid)
            c.connect("검토C$suffix")
            assertEquals(pendingUid, c.uid)
            val originalA = a.uid; val originalB = b.uid
            assertNotEquals(originalA, originalB)
            assertNotNull(a.presenceFirestore())
            val ownedCrew = b.createCrew(NewCrew("권한검토$suffix", "닉네임 저장 대상 확인", "서울", "자유 페이스"))
            assertTrue(runCatching { b.updateNickname("변경거절$suffix", expectedUid = originalA) }.isFailure)
            assertEquals(originalB, b.uid)
            assertEquals("검토B$suffix", b.currentNickname())
            assertTrue(b.snapshot().crews.first { it.id == ownedCrew.id }.isOwner)
            val target = "Team$suffix"
            assertEquals(NicknameAvailability.AVAILABLE, a.checkNickname(target).availability)
            assertEquals(NicknameAvailability.AVAILABLE, b.checkNickname(target).availability)
            val attempts = listOf(async { runCatching { a.updateNickname(target) } },
                async { runCatching { b.updateNickname(target.uppercase(Locale.ROOT)) } }).awaitAll()
            assertEquals(1, attempts.count { it.isSuccess })
            val winner = if (attempts[0].isSuccess) a else b
            val loser = if (attempts[0].isSuccess) b else a
            assertEquals(originalA, a.uid); assertEquals(originalB, b.uid)
            assertEquals(NicknameAvailability.OWNED, winner.checkNickname(target).availability)
            assertEquals(NicknameAvailability.TAKEN, loser.checkNickname(target).availability)
            val stableUid = winner.uid
            winner.updateNickname("새러너$suffix")
            assertEquals(stableUid, winner.uid)
            assertEquals("새러너$suffix", winner.currentNickname())
            assertEquals(NicknameAvailability.AVAILABLE, loser.checkNickname(target).availability)
            loser.updateNickname(target)
            assertEquals(target, loser.snapshot().nickname)
            winner.signOut()
            assertNull(winner.presenceFirestore())
            assertEquals(NicknameAvailability.UNVERIFIED, winner.checkNickname(target).availability)
        } finally {
            repositories.forEach { it.signOut(); it.close() }
            apps.forEach { FirebaseFirestore.getInstance(it).terminate().await(); it.delete() }
        }
    }
    /** Repository recreation exercises persisted candidate markers; it is not a phone/Google test. */
    @Test fun pendingAndLegacyAnonymousAuthNeedServerProfileAcknowledgementAfterRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val suffix = System.nanoTime().toString(36).takeLast(6)
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-runmoni")
            .setApplicationId("1:123:android:abc123").setApiKey("fake-emulator-api-key").build(), "profile-retry-$suffix")
        val appName = app.name
        val auth = FirebaseAuth.getInstance(app)
        val database = FirebaseFirestore.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        database.useEmulator("10.0.2.2", 8080)
        val preferences = context.getSharedPreferences("runmoni-community-account", android.content.Context.MODE_PRIVATE)
        val repositories = mutableListOf<FirebaseCommunityRepository>()
        fun reopened() = FirebaseCommunityRepository(context, app).also { repositories += it }
        try {
            // Models a v0.4.1 retained anonymous Auth with no marker/profile.
            val originalUid = auth.signInAnonymously().await().user!!.uid
            assertFalse(preferences.contains("pendingAnonymousConnectV1:$appName"))
            val legacy = reopened()
            assertEquals("", legacy.uid)
            assertNull(legacy.presenceFirestore())
            assertFalse(legacy.snapshot().signedIn)
            assertEquals(originalUid, auth.currentUser?.uid)
            assertTrue(preferences.getBoolean("pendingAnonymousConnectV1:$appName", false))
            legacy.close()
            val pending = reopened()
            assertEquals("", pending.uid)
            assertNull(pending.presenceFirestore())
            assertEquals(originalUid, auth.currentUser?.uid)
            pending.connect("재시도$suffix")
            assertEquals(originalUid, pending.uid)
            assertNotNull(pending.presenceFirestore())
            assertFalse(preferences.contains("pendingAnonymousConnectV1:$appName"))
            pending.close()
            val confirmed = reopened()
            // Even retained Auth with a real profile is gated until this process
            // obtains its own server profile observation.
            assertEquals("", confirmed.uid)
            assertNull(confirmed.presenceFirestore())
            val snapshot = confirmed.snapshot()
            assertTrue(snapshot.signedIn)
            assertEquals("재시도$suffix", snapshot.nickname)
            assertEquals(originalUid, confirmed.uid)
            assertEquals(originalUid, auth.currentUser?.uid)
        } finally {
            repositories.lastOrNull()?.signOut()
            repositories.forEach { it.close() }
            database.terminate().await(); app.delete()
            preferences.edit().remove("pendingAnonymousConnectV1:$appName").remove("verifiedGoogleUid:$appName").commit()
        }
    }

}
