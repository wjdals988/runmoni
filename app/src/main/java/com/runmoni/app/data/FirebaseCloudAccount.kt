package com.runmoni.app.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.CloudAccountState
import com.runmoni.app.domain.CloudRunSummary
import com.runmoni.app.domain.RunRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Private manual backups only. This service never reads or mutates the pilot session. */
class FirebaseCloudAccount(context: Context) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(CloudAccountState())
    val state: StateFlow<CloudAccountState> = mutableState.asStateFlow()
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var sessionEpoch = 0L
    private var operationSequence = 0L
    private var acceptedUid = ""
    private var firstAuthObservation = true
    private var activeLogin: Ticket? = null
    private var closed = false

    private data class Ticket(val epoch: Long, val operation: Long, val uid: String)
    private class MalformedSummary : Exception()

    init {
        val hasConfiguration = BuildConfig.FIREBASE_ENABLED && listOf(
            BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_WEB_CLIENT_ID,
        ).all { it.isNotBlank() }
        if (!hasConfiguration) {
            mutableState.value = CloudAccountState(status = "Firebase 설정 없음 · 기기 기록은 계속 사용할 수 있습니다")
        } else {
            try {
                val options = FirebaseOptions.Builder()
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .build()
                val firebaseApp = FirebaseApp.getApps(context.applicationContext).firstOrNull { it.name == APP_NAME }
                    ?: FirebaseApp.initializeApp(context.applicationContext, options, APP_NAME)
                val database = FirebaseFirestore.getInstance(firebaseApp)
                // Room is the offline source of truth. Do not persist cloud account data to disk.
                if (database.firestoreSettings.cacheSettings !is MemoryCacheSettings) {
                    database.firestoreSettings = FirebaseFirestoreSettings.Builder(database.firestoreSettings)
                        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
                }
                firestore = database
                auth = FirebaseAuth.getInstance(firebaseApp)
                mutableState.value = CloudAccountState(configured = true, busy = true, status = "저장된 Google 계정 확인 중")
                authListener = FirebaseAuth.AuthStateListener { observeAuth(it) }
                auth?.addAuthStateListener(authListener!!)
            } catch (_: Exception) {
                auth = null
                firestore = null
                mutableState.value = CloudAccountState(status = "Firebase 설정을 확인해 주세요 · 기기 기록은 계속 사용할 수 있습니다")
            }
        }
    }

    private fun accountLabel(user: FirebaseUser?): String = user?.email?.takeIf { it.isNotBlank() }
        ?: user?.displayName?.takeIf { it.isNotBlank() }
        ?: if (user == null) "" else "연결된 Google 계정"

    private fun observeAuth(firebaseAuth: FirebaseAuth) {
        var rejectUnexpectedSession = false
        var rejectedEpoch = -1L
        var rejectedUid = ""
        synchronized(lock) {
            if (closed || activeLogin != null) return
            val user = firebaseAuth.currentUser
            val actualUid = user?.uid.orEmpty()
            if (firstAuthObservation) {
                firstAuthObservation = false
                acceptedUid = actualUid
                sessionEpoch++
                operationSequence++
                mutableState.value = mutableState.value.copy(
                    uid = actualUid, accountLabel = accountLabel(user), busy = false, summaries = emptyList(),
                    status = if (actualUid.isBlank()) "Google 계정 연결 후 요약을 수동 백업할 수 있습니다"
                    else "저장된 Google 계정 연결됨 · 백업과 조회는 수동으로 실행합니다",
                )
            } else if (actualUid != acceptedUid) {
                // A timed-out Firebase Task can still finish. Never adopt its late UID.
                sessionEpoch++
                operationSequence++
                acceptedUid = ""
                rejectUnexpectedSession = actualUid.isNotBlank()
                rejectedEpoch = sessionEpoch
                rejectedUid = actualUid
                mutableState.value = mutableState.value.copy(
                    uid = "", accountLabel = "", busy = false, summaries = emptyList(),
                    status = if (actualUid.isBlank()) "Google 계정 로그아웃됨 · 기기 기록은 유지됩니다"
                    else "Google 계정 세션이 변경되었습니다 · 다시 연결해 주세요",
                )
            } else {
                mutableState.value = mutableState.value.copy(accountLabel = accountLabel(user))
            }
        }
        if (rejectUnexpectedSession) synchronized(lock) {
            // Do not let a delayed rejection sign out a newer account-picker operation.
            if (!closed && sessionEpoch == rejectedEpoch && activeLogin == null && firebaseAuth.currentUser?.uid == rejectedUid) {
                firebaseAuth.signOut()
            }
        }
    }

    suspend fun signIn(context: Context) {
        val firebaseAuth = auth ?: return
        val ticket = synchronized(lock) {
            if (closed || !mutableState.value.configured) return
            firstAuthObservation = false
            sessionEpoch++
            operationSequence++
            Ticket(sessionEpoch, operationSequence, mutableState.value.uid).also {
                activeLogin = it
                mutableState.value = mutableState.value.copy(busy = true, summaries = emptyList(), status = "백업할 Google 계정을 선택해 주세요")
            }
        }
        try {
            val googleOption = GetGoogleIdOption.Builder()
                .setServerClientId(BuildConfig.FIREBASE_WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
            if (!isCurrentLogin(ticket)) return
            // The account picker is interactive; only the network Task has a 15-second timeout.
            val result = CredentialManager.create(context).getCredential(context, request)
            if (!isCurrentLogin(ticket)) return
            val credential = result.credential
            require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            if (!isCurrentLogin(ticket)) return
            val user = withTimeout(NETWORK_TIMEOUT_MILLIS) {
                firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(googleCredential.idToken, null)).await().user
            } ?: error("Missing authenticated account")
            synchronized(lock) {
                if (!isCurrentLoginLocked(ticket)) return
                require(firebaseAuth.currentUser?.uid == user.uid)
                activeLogin = null
                acceptedUid = user.uid
                sessionEpoch++
                mutableState.value = mutableState.value.copy(
                    uid = user.uid, accountLabel = accountLabel(user), busy = false, summaries = emptyList(),
                    status = "Google 계정 연결됨 · 요약 백업과 조회는 수동으로 실행합니다",
                )
            }
        } catch (_: GetCredentialCancellationException) {
            failLogin(ticket, "Google 계정 선택을 취소했습니다")
        } catch (_: TimeoutCancellationException) {
            failLogin(ticket, "Google 계정 연결 응답 시간이 초과되었습니다 · 다시 시도해 주세요")
        } catch (cancelled: CancellationException) {
            failLogin(ticket, "Google 계정 연결을 취소했습니다")
            throw cancelled
        } catch (_: Exception) {
            failLogin(ticket, "Google 계정에 연결하지 못했습니다 · Firebase 설정과 네트워크를 확인해 주세요")
        }
    }

    private fun isCurrentLoginLocked(ticket: Ticket) = !closed && activeLogin == ticket && sessionEpoch == ticket.epoch
    private fun isCurrentLogin(ticket: Ticket) = synchronized(lock) { isCurrentLoginLocked(ticket) }

    private fun failLogin(ticket: Ticket, status: String) {
        val reconcile = synchronized(lock) {
            if (!isCurrentLoginLocked(ticket)) return
            activeLogin = null
            mutableState.value = mutableState.value.copy(busy = false, status = status)
            true
        }
        if (reconcile) auth?.let { observeAuth(it) }
    }

    suspend fun signOut(context: Context) {
        val firebaseAuth = auth ?: return
        val ticket = synchronized(lock) {
            if (closed) return
            firstAuthObservation = false
            activeLogin = null
            acceptedUid = ""
            sessionEpoch++
            operationSequence++
            mutableState.value = mutableState.value.copy(uid = "", accountLabel = "", busy = true, summaries = emptyList(), status = "Google 계정 로그아웃 중")
            Ticket(sessionEpoch, operationSequence, "")
        }
        try {
            synchronized(lock) {
                if (!currentSignOutLocked(ticket)) return
                firebaseAuth.signOut()
            }
            if (!synchronized(lock) { currentSignOutLocked(ticket) }) return
            withTimeout(NETWORK_TIMEOUT_MILLIS) { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
            finishSignedOut(ticket, "Google 계정 로그아웃됨 · 기기 기록은 유지됩니다")
        } catch (_: TimeoutCancellationException) {
            finishSignedOut(ticket, "클라우드에서 로그아웃했습니다 · 계정 선택 상태 정리는 다시 확인해 주세요")
        } catch (cancelled: CancellationException) {
            finishSignedOut(ticket, "클라우드에서 로그아웃했습니다 · 기기 기록은 유지됩니다")
            throw cancelled
        } catch (_: Exception) {
            finishSignedOut(ticket, "클라우드에서 로그아웃했습니다 · 계정 선택 상태 정리는 다시 확인해 주세요")
        }
    }

    private fun currentSignOutLocked(ticket: Ticket) = !closed && activeLogin == null && sessionEpoch == ticket.epoch &&
        operationSequence == ticket.operation && mutableState.value.uid.isBlank()

    private fun finishSignedOut(ticket: Ticket, status: String) = synchronized(lock) {
        if (currentSignOutLocked(ticket)) {
            mutableState.value = mutableState.value.copy(busy = false, status = status)
        }
    }

    suspend fun backupRun(record: RunRecord, expectedUid: String) {
        accountOperation(expectedUid, "선택한 계정에 요약 백업 중", "요약 백업을 완료하지 못했습니다 · 설정과 네트워크를 확인해 주세요",
            action = { database, uid ->
                val summary = CloudRunSummary.fromRunRecord(record)
                val fields = summary.toFields() + (BACKUP_TIMESTAMP_FIELD to FieldValue.serverTimestamp())
                database.collection("users").document(uid).collection("runSummaries").document(summary.runId).set(fields).await()
                summary
            },
            completed = { current, summary -> current.copy(
                status = "요약 백업 완료 · GPS 경로와 마일리지는 업로드하지 않았습니다",
                summaries = (current.summaries.filterNot { it.runId == summary.runId } + summary).sortedByDescending { it.startedAtMillis }.take(MAX_SUMMARIES),
            ) },
        )
    }

    suspend fun refreshSummaries(expectedUid: String) {
        accountOperation(expectedUid, "이 계정의 최근 요약 50개 조회 중", "요약을 조회하지 못했습니다 · 설정과 네트워크를 확인해 주세요",
            action = { database, uid ->
                val result = database.collection("users").document(uid).collection("runSummaries")
                    .orderBy("startedAtMillis", Query.Direction.DESCENDING).limit(MAX_SUMMARIES.toLong()).get(Source.SERVER).await()
                try { result.documents.map { parseSummary(it) } } catch (_: Exception) { throw MalformedSummary() }
            },
            completed = { current, summaries -> current.copy(summaries = summaries, status = "서버에서 최근 요약 ${summaries.size}개를 확인했습니다 · GPS 원본은 포함되지 않습니다") },
        )
    }

    suspend fun deleteSummary(runId: String, expectedUid: String) {
        accountOperation(expectedUid, "선택한 클라우드 요약 삭제 중", "클라우드 요약을 삭제하지 못했습니다 · 설정과 네트워크를 확인해 주세요",
            action = { database, uid ->
                CloudRunSummary.validateRunId(runId)
                database.collection("users").document(uid).collection("runSummaries").document(runId).delete().await()
                runId
            },
            completed = { current, removedId -> current.copy(summaries = current.summaries.filterNot { it.runId == removedId }, status = "클라우드 요약 삭제 완료 · 기기 기록은 유지됩니다") },
        )
    }

    private fun currentTicketLocked(ticket: Ticket) = !closed && activeLogin == null &&
        sessionEpoch == ticket.epoch && operationSequence == ticket.operation &&
        mutableState.value.uid == ticket.uid && acceptedUid == ticket.uid && auth?.currentUser?.uid == ticket.uid

    private suspend fun <T> accountOperation(
        expectedUid: String,
        waiting: String,
        failure: String,
        action: suspend (FirebaseFirestore, String) -> T,
        completed: (CloudAccountState, T) -> CloudAccountState,
    ) {
        val database = firestore ?: return
        val ticket = synchronized(lock) {
            if (closed || activeLogin != null || expectedUid.isBlank() || mutableState.value.uid != expectedUid ||
                acceptedUid != expectedUid || auth?.currentUser?.uid != expectedUid) return
            operationSequence++
            mutableState.value = mutableState.value.copy(busy = true, status = waiting)
            Ticket(sessionEpoch, operationSequence, expectedUid)
        }
        try {
            if (!synchronized(lock) { currentTicketLocked(ticket) }) return
            val result = withTimeout(NETWORK_TIMEOUT_MILLIS) { action(database, ticket.uid) }
            synchronized(lock) {
                if (currentTicketLocked(ticket)) mutableState.value = completed(mutableState.value, result).copy(busy = false)
            }
        } catch (_: MalformedSummary) {
            operationFailure(ticket, "서버 요약 형식이 올바르지 않아 표시를 중단했습니다 · 설정을 확인해 주세요", clearSummaries = true)
        } catch (_: TimeoutCancellationException) {
            operationFailure(ticket, "클라우드 응답 시간이 초과되었습니다 · 저장 여부는 최근 요약 조회로 확인해 주세요")
        } catch (cancelled: CancellationException) {
            operationFailure(ticket, "클라우드 작업을 취소했습니다 · 기기 기록은 유지됩니다")
            throw cancelled
        } catch (_: Exception) {
            operationFailure(ticket, failure)
        }
    }

    private fun operationFailure(ticket: Ticket, status: String, clearSummaries: Boolean = false) = synchronized(lock) {
        if (currentTicketLocked(ticket)) mutableState.value = mutableState.value.copy(
            busy = false, status = status, summaries = if (clearSummaries) emptyList() else mutableState.value.summaries,
        )
    }

    private fun parseSummary(document: DocumentSnapshot): CloudRunSummary {
        val fields = document.data ?: throw MalformedSummary()
        require(fields.keys == SUMMARY_FIELDS + BACKUP_TIMESTAMP_FIELD)
        require(fields[BACKUP_TIMESTAMP_FIELD] is Timestamp)
        fun integer(name: String): Long = when (val value = fields[name]) {
            is Long -> value
            is Int -> value.toLong()
            else -> throw MalformedSummary()
        }
        val version = integer("schemaVersion")
        val count = integer("pointCount")
        require(version == 1L && count in 0..CloudRunSummary.MAX_POINT_COUNT.toLong())
        val summary = CloudRunSummary(
            schemaVersion = version.toInt(), runId = fields["runId"] as? String ?: throw MalformedSummary(),
            startedAtMillis = integer("startedAtMillis"), finishedAtMillis = integer("finishedAtMillis"),
            activeDurationMillis = integer("activeDurationMillis"),
            distanceMeters = (fields["distanceMeters"] as? Number)?.toDouble() ?: throw MalformedSummary(),
            pointCount = count.toInt(),
        )
        require(summary.runId == document.id)
        return summary
    }

    /** Release an Application/test owner's listener; preserve Firebase's account session. */
    fun close() {
        synchronized(lock) {
            closed = true
            sessionEpoch++
            operationSequence++
            activeLogin = null
        }
        authListener?.let { auth?.removeAuthStateListener(it) }
        authListener = null
    }

    companion object {
        private const val APP_NAME = "runmoni-cloud"
        private const val NETWORK_TIMEOUT_MILLIS = 15_000L
        private const val MAX_SUMMARIES = 50
        private const val BACKUP_TIMESTAMP_FIELD = "backupAt"
        private val SUMMARY_FIELDS = setOf("schemaVersion", "runId", "startedAtMillis", "finishedAtMillis", "activeDurationMillis", "distanceMeters", "pointCount")
    }
}
