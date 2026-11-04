package com.runmoni.app.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunityGoogleLinkTest {
    @Test fun existingAnonymousSessionSurvivesUpgradeWithoutGoogleMarker() {
        assertEquals("anonymous-owner", acceptedStoredCommunityUid("anonymous-owner", true, false, ""))
    }

    @Test fun interruptedGoogleRestoreCannotBeAcceptedOnNextProcessStart() {
        assertEquals("", acceptedStoredCommunityUid("temporary-google", false, true, ""))
        assertEquals("", acceptedStoredCommunityUid("temporary-google", false, true, "another-verified-user"))
        assertEquals("", acceptedStoredCommunityUid("temporary-google", true, true, ""))
    }

    @Test fun onlyVerifiedGoogleCommunityIdentityIsAcceptedFromPersistentAuth() {
        assertEquals("verified-owner", acceptedStoredCommunityUid("verified-owner", false, true, "verified-owner"))
        assertEquals("", acceptedStoredCommunityUid("other-provider", false, false, "other-provider"))
    }

    @Test fun nicknameConnectRefusesAnUnacceptedTemporaryGoogleSession() {
        assertEquals(CommunityConnectMode.REJECT_UNVERIFIED, communityConnectMode("", "temporary-google"))
        assertEquals(CommunityConnectMode.REJECT_UNVERIFIED, communityConnectMode("known-owner", "temporary-google"))
    }

    @Test fun nicknameConnectReusesOnlyTheAlreadyAcceptedIdentity() {
        assertEquals(CommunityConnectMode.REUSE_CURRENT, communityConnectMode("known-owner", "known-owner"))
        assertTrue(validCommunityConnectIdentity(CommunityConnectMode.REUSE_CURRENT, "known-owner", CommunityLinkIdentity("known-owner", true), false, "known-owner"))
        assertFalse(validCommunityConnectIdentity(CommunityConnectMode.REUSE_CURRENT, "known-owner", CommunityLinkIdentity("other", true), false, "other"))
    }

    @Test fun newNicknameConnectCanAcceptOnlyANewAnonymousResult() {
        assertEquals(CommunityConnectMode.NEW_ANONYMOUS, communityConnectMode("", ""))
        assertTrue(validCommunityConnectIdentity(CommunityConnectMode.NEW_ANONYMOUS, "", CommunityLinkIdentity("new-anonymous", false), true, "new-anonymous"))
        assertFalse(validCommunityConnectIdentity(CommunityConnectMode.NEW_ANONYMOUS, "", CommunityLinkIdentity("late-google", true), false, "late-google"))
    }

    @Test fun nicknameConnectCannotAcceptAResultAfterAuthHasChanged() {
        assertFalse(validCommunityConnectIdentity(CommunityConnectMode.NEW_ANONYMOUS, "", CommunityLinkIdentity("new-anonymous", false), true, "late-google"))
        assertEquals(CommunityConnectMode.REJECT_UNVERIFIED, communityConnectMode("known-owner", ""))
    }

    private class LinkFake(var current: CommunityLinkIdentity?) : CommunityLinkSession<String> {
        var calls = 0
        var attempt: suspend LinkFake.() -> CommunityLinkIdentity? = {
            current = current?.copy(googleLinked = true)
            current
        }
        override fun identity() = current
        override suspend fun link(credential: String): CommunityLinkIdentity? { calls++; return attempt() }
    }

    @Test fun linkingKeepsCurrentUidAndExistingOwnershipKey() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", false))
        assertEquals(CommunityLinkResult.LINKED, linkExistingCommunityIdentity("crew-owner", "credential", session))
        assertEquals("crew-owner", session.current?.uid)
        assertTrue(session.current?.googleLinked == true)
        assertEquals(1, session.calls)
    }

    @Test fun credentialCollisionPreservesAnonymousIdentityWithoutRetryingAsAnotherUser() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", false))
        session.attempt = { throw CommunityCredentialConflict() }
        assertEquals(CommunityLinkResult.CREDENTIAL_CONFLICT, linkExistingCommunityIdentity("crew-owner", "used-google", session))
        assertEquals(CommunityLinkIdentity("crew-owner", false), session.current)
        assertEquals(1, session.calls)
    }

    @Test fun staleUidAndMissingSessionNeverSendCredentialToLink() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("new-session", false))
        assertEquals(CommunityLinkResult.SESSION_CHANGED, linkExistingCommunityIdentity("old-session", "credential", session))
        session.current = null
        assertEquals(CommunityLinkResult.SESSION_CHANGED, linkExistingCommunityIdentity("old-session", "credential", session))
        assertEquals(0, session.calls)
    }

    @Test fun alreadyLinkedSessionDoesNotStartAnotherProviderOperation() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", true))
        assertEquals(CommunityLinkResult.ALREADY_LINKED, linkExistingCommunityIdentity("crew-owner", "credential", session))
        assertEquals(0, session.calls)
    }

    @Test fun changedIdentityDuringLinkIsNotAcceptedAsSuccessfulRecovery() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", false))
        session.attempt = { current = CommunityLinkIdentity("unexpected", true); current }
        assertEquals(CommunityLinkResult.SESSION_CHANGED, linkExistingCommunityIdentity("crew-owner", "credential", session))
    }

    @Test fun credentialTaskCompletionWithoutProviderLinkIsNotSuccess() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", false))
        session.attempt = { current }
        assertEquals(CommunityLinkResult.NOT_VERIFIED, linkExistingCommunityIdentity("crew-owner", "credential", session))
    }

    @Test fun cancellationIsPropagatedWithoutChangingCurrentIdentity() = runBlocking {
        val session = LinkFake(CommunityLinkIdentity("crew-owner", false))
        session.attempt = { throw CancellationException("cancelled") }
        try {
            linkExistingCommunityIdentity("crew-owner", "credential", session)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(CommunityLinkIdentity("crew-owner", false), session.current)
    }

    private class RestoreFake(var current: CommunityLinkIdentity? = null) : CommunityRestoreSession<String> {
        var signIns = 0
        var profileReads = 0
        var rejected = emptyList<String>()
        var profileExists = true
        var readFailure: Exception? = null
        var afterRead: (() -> Unit)? = null
        override fun identity() = current
        override suspend fun signIn(credential: String): CommunityLinkIdentity? {
            signIns++
            current = CommunityLinkIdentity("old-google-crew", true)
            return current
        }
        override suspend fun hasExistingProfile(uid: String): Boolean {
            profileReads++
            readFailure?.let { throw it }
            afterRead?.invoke()
            return profileExists
        }
        override fun rejectTemporaryIdentity(uid: String) {
            rejected = rejected + uid
            if (current?.uid == uid) current = null
        }
    }

    @Test fun restorationRequiresAnExistingServerProfileBeforeAcceptingUid() = runBlocking {
        val session = RestoreFake()
        val result = restoreExistingCommunityIdentity("google", session)
        assertEquals(CommunityRestoreResult(CommunityRestoreStatus.RESTORED, "old-google-crew"), result)
        assertEquals(1, session.signIns)
        assertEquals(1, session.profileReads)
        assertTrue(session.rejected.isEmpty())
    }

    @Test fun restorationCannotReplaceAnExistingAnonymousOrLinkedSession() = runBlocking {
        for (linked in listOf(false, true)) {
            val session = RestoreFake(CommunityLinkIdentity("keep-current", linked))
            assertEquals(CommunityRestoreStatus.EXISTING_SESSION, restoreExistingCommunityIdentity("other-google", session).status)
            assertEquals("keep-current", session.current?.uid)
            assertEquals(0, session.signIns)
            assertEquals(0, session.profileReads)
        }
    }

    @Test fun summaryOnlyGoogleAccountDoesNotCreateACommunityProfile() = runBlocking {
        val session = RestoreFake().apply { profileExists = false }
        assertEquals(CommunityRestoreStatus.NO_PROFILE, restoreExistingCommunityIdentity("google", session).status)
        assertNull(session.current)
        assertEquals(listOf("old-google-crew"), session.rejected)
    }

    @Test fun offlineServerReadAndCancellationRejectOnlyTheTemporarySession() = runBlocking {
        for (failure in listOf(IllegalStateException("offline"), CancellationException("cancelled"))) {
            val session = RestoreFake().apply { readFailure = failure }
            try {
                restoreExistingCommunityIdentity("google", session)
                fail("Failed verification must propagate")
            } catch (actual: Exception) { assertSame(failure, actual) }
            assertNull(session.current)
            assertEquals(listOf("old-google-crew"), session.rejected)
        }
    }

    @Test fun restorationRacingWithAnotherSessionDoesNotClearThatNewSession() = runBlocking {
        val session = RestoreFake().apply { afterRead = { current = CommunityLinkIdentity("newer-session", true) } }
        assertEquals(CommunityRestoreStatus.SESSION_CHANGED, restoreExistingCommunityIdentity("google", session).status)
        assertEquals("newer-session", session.current?.uid)
    }
}
