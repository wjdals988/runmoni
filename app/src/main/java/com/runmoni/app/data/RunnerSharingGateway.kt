package com.runmoni.app.data

import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunnerShareSession
import com.runmoni.app.domain.TrackPoint

/** Session operations only: this boundary never signs in or starts a recorder. */
interface RunnerSharingGateway {
    fun activeSession(): RunnerShareSession?
    fun prepareEnable(runId: String, adultConfirmed: Boolean, expectedUid: String): RunnerShareSession
    suspend fun activate(session: RunnerShareSession): RunnerShareSession
    suspend fun publish(session: RunnerShareSession, point: TrackPoint, nickname: String, avatar: AvatarStyle)
    fun invalidate(session: RunnerShareSession? = activeSession()): RunnerShareSession?
    suspend fun revoke(session: RunnerShareSession? = null)
    /** Complete the server fence for this UID's retired sessions before allocating another. */
    suspend fun cleanupInvalidated(expectedUid: String)
}
