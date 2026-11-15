package com.runmoni.app.domain

import org.junit.Assert.*
import org.junit.Test

class CommunityProfileConnectionTest {
    private val receipt = CommunityProfileReceipt("same-owner", "기존 러너", "기존 러너")

    @Test fun authAloneAndPendingReservationNeverBecomeAConnectedProfile() {
        assertFalse(restoredCommunityProfile("same-owner", "same-owner", null).connected)
        assertFalse(restoredCommunityProfile("", "same-owner", receipt).connected)
        val pending = CommunityProfileConnectionState(phase = CommunityProfileConnectionPhase.PENDING)
        assertFalse(pending.connected)
        assertFalse(pending.serverVerified)
        assertEquals("", pending.uid)
    }

    @Test fun sameAcceptedAuthAndServerReceiptRestoreWithoutPretendingANewServerResponse() {
        val state = restoredCommunityProfile("same-owner", "same-owner", receipt)
        assertTrue(state.connected)
        assertEquals(CommunityProfileConnectionPhase.CACHED, state.phase)
        assertFalse(state.serverVerified)
        assertEquals("same-owner", state.uid)
        assertEquals("기존 러너", state.nickname)
    }

    @Test fun aReceiptCannotTransferToAnotherAuthCandidateOrCurrentUser() {
        for ((candidate, current) in listOf("foreign" to "foreign", "same-owner" to "foreign", "foreign" to "same-owner", "same-owner" to "")) {
            val state = restoredCommunityProfile(candidate, current, receipt)
            assertFalse(state.connected)
            assertEquals("", state.uid)
            assertNull(state.nickname)
        }
    }

    @Test fun pendingAnonymousAndUnverifiedGoogleMarkersStillFenceCachedReceipts() {
        val anonymous = acceptedStoredCommunityUid("same-owner", true, false, "", pendingAnonymousConnect = true)
        assertFalse(restoredCommunityProfile(anonymous, "same-owner", receipt).connected)
        val google = acceptedStoredCommunityUid("same-owner", false, true, "another-owner")
        assertFalse(restoredCommunityProfile(google, "same-owner", receipt).connected)
        val approved = acceptedStoredCommunityUid("same-owner", false, true, "same-owner")
        assertTrue(restoredCommunityProfile(approved, "same-owner", receipt).connected)
    }

    @Test fun aCompletedSameUidGoogleLinkCanReuseItsExistingServerProfileReceiptAfterRestart() {
        val candidate = acceptedStoredCommunityUid("same-owner", false, true, "", verifiedProfileUid = receipt.uid)
        assertTrue(restoredCommunityProfile(candidate, "same-owner", receipt).connected)
        assertEquals("", acceptedStoredCommunityUid("foreign-google", false, true, "", verifiedProfileUid = receipt.uid))
        assertEquals("", acceptedStoredCommunityUid("same-owner", false, true, ""))
    }

    @Test fun offlineFailureRetainsTheSameAccountAndNicknameInsteadOfLoggingOut() {
        val verified = CommunityProfileConnectionState(CommunityProfileConnectionPhase.VERIFIED, receipt.uid, receipt.nickname, receipt.nicknameKey)
        val failure = CommunityProfileFailure(CommunityProfileOperation.PROFILE_READ, CommunityProfileFailureReason.NETWORK)
        val offline = verified.offline(failure)
        assertTrue(offline.connected)
        assertFalse(offline.serverVerified)
        assertEquals(receipt.uid, offline.uid)
        assertEquals(receipt.nickname, offline.nickname)
        assertEquals(failure, offline.failure)
    }

    @Test fun failedFirstConnectionStaysPendingAndDoesNotManufactureAServerAck() {
        val pending = CommunityProfileConnectionState(phase = CommunityProfileConnectionPhase.PENDING)
        val failed = pending.offline(CommunityProfileFailure(CommunityProfileOperation.NICKNAME_READ, CommunityProfileFailureReason.PERMISSION_DENIED))
        assertFalse(failed.connected)
        assertEquals(CommunityProfileConnectionPhase.PENDING, failed.phase)
        assertEquals("", failed.uid)
        assertNull(failed.nickname)
    }

    @Test fun permissionAuthenticationAndNetworkFailuresRemainDifferentAndSafeToDisplay() {
        val denied = CommunityProfileFailure(CommunityProfileOperation.NICKNAME_READ, CommunityProfileFailureReason.PERMISSION_DENIED)
        val auth = CommunityProfileFailure(CommunityProfileOperation.AUTHENTICATION, CommunityProfileFailureReason.AUTHENTICATION)
        val network = CommunityProfileFailure(CommunityProfileOperation.PROFILE_READ, CommunityProfileFailureReason.NETWORK)
        assertNotEquals(denied.reason, network.reason)
        assertNotEquals(denied.message, network.message)
        assertNotEquals(auth.message, network.message)
        assertTrue(denied.message.contains("권한"))
        assertFalse(denied.message.contains("잠시 후 다시 시도"))
        assertEquals(denied.message, CommunityProfileException(denied).message)
    }

    @Test fun aRememberFailureCanKeepThisVerifiedSessionWithoutClaimingSuccessfulPersistence() {
        val failure = CommunityProfileFailure(CommunityProfileOperation.REMEMBER_CONNECTION, CommunityProfileFailureReason.LOCAL_STORAGE)
        val current = CommunityProfileConnectionState(CommunityProfileConnectionPhase.VERIFIED, receipt.uid, receipt.nickname, receipt.nicknameKey, failure)
        assertTrue(current.connected)
        assertTrue(current.serverVerified)
        assertEquals(CommunityProfileFailureReason.LOCAL_STORAGE, current.failure?.reason)
        assertFalse(restoredCommunityProfile(receipt.uid, receipt.uid, null).connected)
    }
}
