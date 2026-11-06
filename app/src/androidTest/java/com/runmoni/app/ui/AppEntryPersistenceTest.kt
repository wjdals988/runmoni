package com.runmoni.app.ui

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.BuildConfig
import com.runmoni.app.data.AppEntryStore
import com.runmoni.app.data.FirebaseCloudAccount
import com.runmoni.app.domain.AppEntryChoice
import com.runmoni.app.domain.GoogleSignInOutcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Isolated preference files only. Never clear actual runmoni/Room/auth state. */
class AppEntryPersistenceTest {
    private fun withPreferences(block: (android.content.SharedPreferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "entry-fixture-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try { block(prefs) } finally { context.deleteSharedPreferences(name) }
    }

    @Test fun startupPreferenceDoesNotMistakeANewInstallForAReturningUser() = withPreferences { prefs ->
        assertTrue(prefs.edit().putBoolean("publicLocation", false).commit())
        val first = AppEntryStore(prefs, existingInstallation = false)
        assertEquals(AppEntryChoice.CHOOSE, first.initialChoice())
        // Recreating storage after Room/application startup still requires an explicit choice.
        assertEquals(AppEntryChoice.CHOOSE, AppEntryStore(prefs, true).initialChoice())
    }

    @Test fun guestChoicePersistsWithoutChangingExistingWalletOrCommunitySettings() = withPreferences { prefs ->
        assertTrue(prefs.edit().putString("localProfile", "private fixture; not parsed")
            .putString("communityBackend", "firebase").putBoolean("publicLocation", false).commit())
        val before = prefs.all.toMap()
        val store = AppEntryStore(prefs, false)
        assertEquals(AppEntryChoice.EXISTING, store.initialChoice())
        assertTrue(store.save(AppEntryChoice.GUEST))
        assertEquals(AppEntryChoice.GUEST, AppEntryStore(prefs, false).initialChoice())
        assertEquals(before, prefs.all.filterKeys { it != AppEntryStore.KEY })
    }

    @Test fun upgradeKeepsExistingUseButNeverOverridesAnExplicitPendingChoice() = withPreferences { prefs ->
        val store = AppEntryStore(prefs, true)
        assertEquals(AppEntryChoice.EXISTING, store.initialChoice())
        assertTrue(prefs.edit().putString(AppEntryStore.KEY, AppEntryChoice.CHOOSE.wireValue).commit())
        assertEquals(AppEntryChoice.CHOOSE, AppEntryStore(prefs, true).initialChoice())
    }

    @Test fun unavailableGoogleReturnsFailureWithoutGrantingEntryOrCreatingACloudSession() = runBlocking {
        check(!BuildConfig.FIREBASE_ENABLED) { "Only the local unconfigured debug build" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val account = FirebaseCloudAccount(context)
        try {
            assertTrue(account.signIn(context) is GoogleSignInOutcome.Failure)
            assertTrue(account.state.value.uid.isBlank())
            assertFalse(account.state.value.busy)
        } finally { account.close() }
    }
}
