package com.runmoni.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.runmoni.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** SDK exception mapping only; never initializes Firebase or uses the network. */
@RunWith(AndroidJUnit4::class)
class CommunityProfileFailureTest {
    @Test fun nicknameReadPermissionDenialKeepsItsStageWithoutSdkPrivateDetails() {
        val raw = FirebaseFirestoreException("PRIVATE SDK DOC PATH", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val mapped = profileTransactionReadFailure(raw, CommunityProfileOperation.NICKNAME_READ) as CommunityProfileException
        assertEquals(CommunityProfileOperation.NICKNAME_READ, mapped.failure.operation)
        assertEquals(CommunityProfileFailureReason.PERMISSION_DENIED, mapped.failure.reason)
        assertFalse(mapped.message.orEmpty().contains("PRIVATE SDK"))
        assertNull(mapped.cause)
    }

    @Test fun transientReadExceptionsStayIntactSoTheSdkCanRetryTheTransaction() {
        for (code in listOf(FirebaseFirestoreException.Code.ABORTED, FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED)) {
            val original = FirebaseFirestoreException("Transient test", code)
            assertSame(original, profileTransactionReadFailure(original, CommunityProfileOperation.NICKNAME_READ))
        }
    }

    @Test fun authenticationAndTimeoutDoNotBecomePermissionFailures() {
        assertEquals(CommunityProfileFailureReason.AUTHENTICATION,
            profileAccessFailure(FirebaseFirestoreException.Code.UNAUTHENTICATED, CommunityProfileOperation.PROFILE_READ).reason)
        assertEquals(CommunityProfileFailureReason.TIMEOUT,
            profileAccessFailure(FirebaseFirestoreException.Code.DEADLINE_EXCEEDED, CommunityProfileOperation.NICKNAME_SAVE).reason)
        assertEquals(CommunityProfileFailureReason.NETWORK,
            profileAccessFailure(FirebaseFirestoreException.Code.UNAVAILABLE, CommunityProfileOperation.PROFILE_READ).reason)
    }
}
