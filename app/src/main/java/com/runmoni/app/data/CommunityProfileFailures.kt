package com.runmoni.app.data

import com.google.firebase.firestore.FirebaseFirestoreException
import com.runmoni.app.domain.CommunityProfileException
import com.runmoni.app.domain.CommunityProfileFailure
import com.runmoni.app.domain.CommunityProfileFailureReason
import com.runmoni.app.domain.CommunityProfileOperation

internal fun profileAccessFailure(code: FirebaseFirestoreException.Code, operation: CommunityProfileOperation): CommunityProfileFailure =
    CommunityProfileFailure(operation, when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> CommunityProfileFailureReason.PERMISSION_DENIED
        FirebaseFirestoreException.Code.UNAUTHENTICATED -> CommunityProfileFailureReason.AUTHENTICATION
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CommunityProfileFailureReason.TIMEOUT
        else -> CommunityProfileFailureReason.NETWORK
    })

/** Preserve the SDK exception for transient reads so Firestore can still retry
 * its transaction. Only final access denials need an exact diagnostic stage. */
internal fun profileTransactionReadFailure(failure: FirebaseFirestoreException, operation: CommunityProfileOperation): Exception =
    if (failure.code in setOf(FirebaseFirestoreException.Code.PERMISSION_DENIED, FirebaseFirestoreException.Code.UNAUTHENTICATED))
        CommunityProfileException(profileAccessFailure(failure.code, operation)) else failure
