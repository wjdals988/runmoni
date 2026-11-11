package com.runmoni.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.data.LocalWallet
import com.runmoni.app.domain.CommunityBackend
import com.runmoni.app.domain.Profile
import com.runmoni.app.domain.RunPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual AppViewModel in the local Firebase-disabled TEST APK; no FakeActions.
 * Root's outer review harness owns whole-XML/DB restoration. Tests refuse an
 * unfinished run and retain the sole preference key they select themselves.
 * No run start, Firebase auth, profile server write or production request occurs.
 */
@RunWith(AndroidJUnit4::class)
class ProfileConnectionViewModelTest {
    private suspend fun withViewModel(block: suspend (AppViewModel, Profile) -> Unit) {
        assertTrue(BuildConfig.DEBUG)
        assertFalse("Only the local Firebase-disabled review APK is accepted", BuildConfig.FIREBASE_ENABLED)
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        assertFalse(app.community.configured)
        withTimeout(30_000) { app.runs.persistence.first { it.initialized && !it.saving } }
        assertEquals(RunPhase.IDLE, app.runs.active.value.phase)
        assertFalse(app.runs.persistence.value.creditPending)
        assertNull(withContext(Dispatchers.IO) { app.runs.dao.unfinished() })
        val preferences = app.getSharedPreferences("runmoni", Context.MODE_PRIVATE)
        val hadBackend = preferences.contains("communityBackend")
        val originalBackend = preferences.getString("communityBackend", null)
        val store = ViewModelStore()
        try {
            assertTrue(preferences.edit().putString("communityBackend", "firebase").commit())
            withContext(Dispatchers.Main) {
                val model = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[AppViewModel::class.java]
                val original = LocalWallet(app).read()
                block(model, original)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                store.clear()
                val editor = preferences.edit()
                if (hadBackend) editor.putString("communityBackend", originalBackend) else editor.remove("communityBackend")
                assertTrue(editor.commit())
            }
        }
    }

    @Test fun failedConnectionStaysInlineAndPreservesTheActualWalletAcrossSameNameRetry() = runBlocking {
        withViewModel { model, original ->
            val initialReceipt = model.state.value.unifiedProfile.connectedSerial
            repeat(2) {
                model.connectUnifiedProfile("연결검토 러너", "", CommunityBackend.FIREBASE)
                val failed = withTimeout(5_000) { model.state.first { state ->
                    state.unifiedProfile.error != null && !state.unifiedProfile.connecting
                } }
                assertNull("Profile errors must not create the generic modal", failed.error)
                assertFalse(failed.signedIn)
                assertEquals("", failed.communityUid)
                assertEquals(initialReceipt, failed.unifiedProfile.connectedSerial)
                assertEquals(0L, failed.unifiedProfile.savedSerial)
                assertFalse(failed.busy)
                assertEquals(original, failed.profile)
                assertEquals(original, LocalWallet(ApplicationProvider.getApplicationContext<Context>()).read())
                assertEquals("", ApplicationProvider.getApplicationContext<RunmoniApplication>().community.uid)
            }
        }
    }

    @Test fun staleUidAndBackendAreRejectedBeforeStartingAnotherProfileOperation() = runBlocking {
        withViewModel { model, original ->
            model.connectUnifiedProfile("경계검토 러너", "foreign-profile", CommunityBackend.FIREBASE)
            val stale = model.state.value.unifiedProfile.error
            assertNotNull(stale)
            assertFalse(model.state.value.unifiedProfile.connecting)
            // Reach the actual configured=false path, so the next stale-backend
            // result cannot be an unnoticed old error from the first rejection.
            model.connectUnifiedProfile("경계검토 러너", "", CommunityBackend.FIREBASE)
            val configuredFailure = withTimeout(5_000) { model.state.first { it.unifiedProfile.error != null && !it.unifiedProfile.connecting } }
            assertNotEquals(stale, configuredFailure.unifiedProfile.error)
            model.connectUnifiedProfile("경계검토 러너", "", CommunityBackend.HTTP)
            val rejected = model.state.value
            assertEquals(stale, rejected.unifiedProfile.error)
            // Both backends can have an empty UID. The wrong-backend save
            // must be rejected before a nickname write. This configured=false
            // fixture keeps the actual backend Firebase and expects HTTP.
            model.saveUnifiedProfile("저장경계 검토", "", CommunityBackend.HTTP)
            val rejectedSave = model.state.value
            assertEquals(stale, rejectedSave.unifiedProfile.error)
            assertFalse(rejectedSave.unifiedProfile.saving)
            assertFalse(rejectedSave.unifiedProfile.connecting)
            assertEquals(0L, rejectedSave.unifiedProfile.savedSerial)
            assertEquals(0L, rejectedSave.unifiedProfile.connectedSerial)
            assertEquals(original, rejectedSave.profile)
            assertEquals(original, LocalWallet(ApplicationProvider.getApplicationContext<Context>()).read())
            assertNull(rejectedSave.error)
            assertEquals(CommunityBackend.FIREBASE, rejected.communityBackend)
            assertFalse(rejected.unifiedProfile.connecting)
            assertFalse(rejected.busy)
            assertNull(rejected.error)
            assertEquals(0L, rejected.unifiedProfile.connectedSerial)
            assertEquals(0L, rejected.unifiedProfile.savedSerial)
            assertEquals("", rejected.communityUid)
            assertFalse(rejected.signedIn)
            assertEquals(original, rejected.profile)
            assertEquals(original, LocalWallet(ApplicationProvider.getApplicationContext<Context>()).read())
        }
    }
}
