package com.runmoni.app.domain

/** Linking protects the existing community UID; it is not a backup of local run or wallet data. */
data class CommunityGoogleLinkState(
    val linked: Boolean = false,
    val busy: Boolean = false,
    val status: String = "현재 크루 프로필에 Google 계정을 연결할 수 있어요",
    val accountLabel: String = "",
)

internal data class CommunityLinkIdentity(val uid: String, val googleLinked: Boolean)
internal enum class CommunityLinkResult { LINKED, ALREADY_LINKED, CREDENTIAL_CONFLICT, SESSION_CHANGED, NOT_VERIFIED }
internal class CommunityCredentialConflict : Exception()

/** Recognizes an existing auth candidate, not proof that its server profile exists. */
internal fun acceptedStoredCommunityUid(uid: String, anonymous: Boolean, googleLinked: Boolean, verifiedGoogleUid: String,
    pendingAnonymousConnect: Boolean = false): String =
    uid.takeIf { it.isNotBlank() && (anonymous && !googleLinked && !pendingAnonymousConnect || googleLinked && it == verifiedGoogleUid) }.orEmpty()

/** An interrupted reservation retains its own anonymous auth UID for an explicit retry. */
internal fun pendingStoredCommunityUid(uid: String, anonymous: Boolean, googleLinked: Boolean, pendingAnonymousConnect: Boolean): String =
    uid.takeIf { it.isNotBlank() && anonymous && !googleLinked && pendingAnonymousConnect }.orEmpty()

/** A retained auth candidate and a server ACK must describe the same still-current identity. */
internal fun verifiedCommunityUid(authCandidateUid: String, serverProfileUid: String, currentAuthUid: String): String =
    authCandidateUid.takeIf { it.isNotBlank() && it == serverProfileUid && it == currentAuthUid }.orEmpty()

internal enum class CommunityConnectMode { NEW_ANONYMOUS, REUSE_CURRENT, REJECT_UNVERIFIED }
internal fun communityConnectMode(acceptedUid: String, currentUid: String): CommunityConnectMode = when {
    acceptedUid.isBlank() && currentUid.isBlank() -> CommunityConnectMode.NEW_ANONYMOUS
    currentUid.isNotBlank() && currentUid == acceptedUid -> CommunityConnectMode.REUSE_CURRENT
    else -> CommunityConnectMode.REJECT_UNVERIFIED
}
internal fun validCommunityConnectIdentity(mode: CommunityConnectMode, initialUid: String, candidate: CommunityLinkIdentity?,
    anonymous: Boolean, currentUid: String): Boolean = candidate != null && candidate.uid.isNotBlank() && candidate.uid == currentUid && when (mode) {
        CommunityConnectMode.NEW_ANONYMOUS -> anonymous && !candidate.googleLinked
        CommunityConnectMode.REUSE_CURRENT -> candidate.uid == initialUid
        CommunityConnectMode.REJECT_UNVERIFIED -> false
    }

/** This port intentionally has no sign-in, sign-out, delete, or account-merge operation. */
internal interface CommunityLinkSession<in Credential> {
    fun identity(): CommunityLinkIdentity?
    suspend fun link(credential: Credential): CommunityLinkIdentity?
}

/** Refuse stale callers and provider collisions without replacing the current community identity. */
internal suspend fun <Credential> linkExistingCommunityIdentity(
    expectedUid: String,
    credential: Credential,
    session: CommunityLinkSession<Credential>,
): CommunityLinkResult {
    val before = session.identity()
    if (expectedUid.isBlank() || before?.uid != expectedUid) return CommunityLinkResult.SESSION_CHANGED
    if (before.googleLinked) return CommunityLinkResult.ALREADY_LINKED
    val linked = try {
        session.link(credential)
    } catch (_: CommunityCredentialConflict) {
        val current = session.identity()
        return when {
            current?.uid != expectedUid -> CommunityLinkResult.SESSION_CHANGED
            current.googleLinked -> CommunityLinkResult.ALREADY_LINKED
            else -> CommunityLinkResult.CREDENTIAL_CONFLICT
        }
    }
    val current = session.identity()
    return when {
        linked?.uid != expectedUid || current?.uid != expectedUid -> CommunityLinkResult.SESSION_CHANGED
        !linked.googleLinked || !current.googleLinked -> CommunityLinkResult.NOT_VERIFIED
        else -> CommunityLinkResult.LINKED
    }
}

internal enum class CommunityRestoreStatus { RESTORED, EXISTING_SESSION, NO_PROFILE, SESSION_CHANGED, NOT_VERIFIED }
internal data class CommunityRestoreResult(val status: CommunityRestoreStatus, val uid: String = "")
internal interface CommunityRestoreSession<in Credential> {
    fun identity(): CommunityLinkIdentity?
    suspend fun signIn(credential: Credential): CommunityLinkIdentity?
    suspend fun hasExistingProfile(uid: String): Boolean
    /** Must only clear this candidate's temporary session, never a newer user. */
    fun rejectTemporaryIdentity(uid: String)
}

/** Restore only an existing Google-linked community profile; never overwrite an active profile. */
internal suspend fun <Credential> restoreExistingCommunityIdentity(
    credential: Credential,
    session: CommunityRestoreSession<Credential>,
): CommunityRestoreResult {
    if (session.identity() != null) return CommunityRestoreResult(CommunityRestoreStatus.EXISTING_SESSION)
    var candidateUid = ""
    var verified = false
    try {
        val candidate = session.signIn(credential)
        candidateUid = candidate?.uid.orEmpty()
        if (candidateUid.isBlank() || candidate?.googleLinked != true) {
            return CommunityRestoreResult(CommunityRestoreStatus.NOT_VERIFIED)
        }
        if (session.identity()?.uid != candidateUid) return CommunityRestoreResult(CommunityRestoreStatus.SESSION_CHANGED)
        if (!session.hasExistingProfile(candidateUid)) return CommunityRestoreResult(CommunityRestoreStatus.NO_PROFILE)
        val current = session.identity()
        if (current?.uid != candidateUid || !current.googleLinked) return CommunityRestoreResult(CommunityRestoreStatus.SESSION_CHANGED)
        verified = true
        return CommunityRestoreResult(CommunityRestoreStatus.RESTORED, candidateUid)
    } finally {
        if (!verified && candidateUid.isNotBlank()) session.rejectTemporaryIdentity(candidateUid)
    }
}
