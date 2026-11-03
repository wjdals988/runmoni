package com.runmoni.app.data

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.*
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

data class FirebaseRoomSnapshot(val crews: List<Crew>, val parties: List<Party>, val signedIn: Boolean)
data class FirebaseRoomMessages(val messages: List<ChatMessage>, val fromCache: Boolean)

/** Spark-compatible rooms. Identity is separate from Google summary backup; no reward/GPS writes. */
class FirebaseCommunityRepository internal constructor(context: Context, testApp: FirebaseApp?) {
    constructor(context: Context) : this(context, null)
    private val lock = Any()
    private var epoch = 0L
    private var activeLogin = false
    private var closed = false
    private val firebaseApp = testApp?.also { require(BuildConfig.DEBUG && it.options.projectId.orEmpty().startsWith("demo-")) }
        ?: if (!BuildConfig.FIREBASE_ENABLED) null else runCatching {
        FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME } ?: FirebaseApp.initializeApp(context,
            FirebaseOptions.Builder().setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID).setApiKey(BuildConfig.FIREBASE_API_KEY).build(), APP_NAME)
    }.getOrNull()
    private val auth = firebaseApp?.let { FirebaseAuth.getInstance(it) }
    private val database = firebaseApp?.let { FirebaseFirestore.getInstance(it).apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings).setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    } }
    private val accountPreferences = context.applicationContext.getSharedPreferences("runmoni-community-account", Context.MODE_PRIVATE)
    private val verifiedGoogleKey = "verifiedGoogleUid:${firebaseApp?.name.orEmpty()}"
    private var acceptedUid = auth?.currentUser?.let { user ->
        acceptedStoredCommunityUid(user.uid, user.isAnonymous,
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID },
            accountPreferences.getString(verifiedGoogleKey, "").orEmpty())
    }.orEmpty()
    private data class GoogleTicket(val epoch: Long, val operation: Long, val uid: String, val restoring: Boolean = false)
    private var googleOperation = 0L
    private var activeGoogle: GoogleTicket? = null
    private val mutableGoogleLink = MutableStateFlow(googleState(auth?.currentUser?.takeIf { it.uid == acceptedUid }))
    val googleLinkState: StateFlow<CommunityGoogleLinkState> = mutableGoogleLink.asStateFlow()
    private val authListener = FirebaseAuth.AuthStateListener { current ->
        synchronized(lock) {
            if (!closed && !activeLogin && current.currentUser?.uid.orEmpty() != acceptedUid) {
                // Reject a late sign-in Task after cancellation/timeout.
                epoch++; acceptedUid = ""
                if (activeGoogle != null) {
                    activeGoogle = null
                    mutableGoogleLink.value = CommunityGoogleLinkState(status = "크루 프로필 연결 상태가 바뀌었어요 · 다시 확인해 주세요")
                }
                if (current.currentUser != null) current.signOut()
            }
            if (!closed) syncGoogleLinkLocked()
        }
    }
    init { auth?.addAuthStateListener(authListener) }
    val configured get() = database != null && auth != null
    val uid get() = synchronized(lock) { acceptedUid.takeIf { auth?.currentUser?.uid == it }.orEmpty() }
    private fun db() = database ?: error("크루·파티 연결 설정이 없는 빌드예요. 설정을 포함한 새 APK가 필요해요.")
    private fun identity(): String = uid.takeIf { it.isNotBlank() } ?: error("닉네임으로 크루 프로필을 먼저 연결해 주세요.")
    private fun profile(user: String) = db().collection("communityProfiles").document(user)
    private fun room(id: String) = db().collection("communityRooms").document(id.also { require(it.matches(Regex("[A-Za-z0-9]{20}"))) { "크루·파티 주소를 확인해 주세요." } })
    private fun member(id: String, user: String) = room(id).collection("members").document(user)

    private fun googleIdentity(user: FirebaseUser?): CommunityLinkIdentity? = user?.let {
        CommunityLinkIdentity(it.uid, it.providerData.any { provider -> provider.providerId == GoogleAuthProvider.PROVIDER_ID })
    }
    private fun googleState(user: FirebaseUser?): CommunityGoogleLinkState {
        val provider = user?.providerData?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        return if (provider == null) CommunityGoogleLinkState() else CommunityGoogleLinkState(
            linked = true,
            status = "현재 크루 프로필에 Google 계정이 연결돼 있어요 · 기록·포인트 백업은 별도예요",
            accountLabel = provider.email?.takeIf { it.isNotBlank() } ?: "연결된 Google 계정",
        )
    }
    private fun syncGoogleLinkLocked() {
        val user = auth?.currentUser?.takeIf { it.uid == acceptedUid && acceptedUid.isNotBlank() }
        val next = googleState(user)
        if (next.linked != mutableGoogleLink.value.linked && activeGoogle == null) mutableGoogleLink.value = next
    }
    private fun currentGoogleLocked(ticket: GoogleTicket) = !closed && epoch == ticket.epoch && activeGoogle == ticket &&
        if (ticket.restoring) acceptedUid.isBlank() else acceptedUid == ticket.uid && auth?.currentUser?.uid == ticket.uid
    private fun currentGoogle(ticket: GoogleTicket) = synchronized(lock) { currentGoogleLocked(ticket) }

    /** Explicitly link, never sign in as a different UID or merge another Google account. */
    suspend fun linkGoogleAccount(context: Context) {
        val currentAuth = auth ?: return
        val ticket = synchronized(lock) {
            if (closed || activeLogin || activeGoogle != null) return
            val user = currentAuth.currentUser
            if (user == null || user.uid != acceptedUid || acceptedUid.isBlank()) {
                mutableGoogleLink.value = CommunityGoogleLinkState(status = "닉네임으로 크루 프로필을 먼저 연결해 주세요")
                return
            }
            if (googleIdentity(user)?.googleLinked == true) { mutableGoogleLink.value = googleState(user); return }
            GoogleTicket(epoch, ++googleOperation, user.uid).also {
                activeGoogle = it
                mutableGoogleLink.value = CommunityGoogleLinkState(busy = true, status = "현재 크루 프로필에 연결할 Google 계정을 선택해 주세요")
            }
        }
        try {
            val credential = pickGoogleCredential(context)
            if (!currentGoogle(ticket)) return
            val result = withTimeout(15_000) {
                check(profile(ticket.uid).get(Source.SERVER).await().exists())
                linkExistingCommunityIdentity(ticket.uid, credential, object : CommunityLinkSession<AuthCredential> {
                    override fun identity() = synchronized(lock) {
                        if (currentGoogleLocked(ticket)) googleIdentity(currentAuth.currentUser) else null
                    }
                    override suspend fun link(credential: AuthCredential): CommunityLinkIdentity? {
                        check(currentGoogle(ticket))
                        val user = currentAuth.currentUser?.takeIf { it.uid == ticket.uid } ?: return null
                        try {
                            val task = user.linkWithCredential(credential)
                            task.addOnCompleteListener { if (it.isSuccessful) reconcileLateGoogleLink(ticket) }
                            return googleIdentity(task.await().user)
                        } catch (failure: FirebaseAuthException) {
                            if (failure.errorCode in setOf("ERROR_CREDENTIAL_ALREADY_IN_USE", "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "ERROR_PROVIDER_ALREADY_LINKED")) throw CommunityCredentialConflict()
                            throw failure
                        }
                    }
                })
            }
            val status = when (result) {
                CommunityLinkResult.LINKED, CommunityLinkResult.ALREADY_LINKED -> "Google 계정 연결됨 · 현재 크루 소속과 권한은 유지돼요. 기록·포인트 백업은 별도예요"
                CommunityLinkResult.CREDENTIAL_CONFLICT -> "이 Google 계정은 다른 프로필에 연결돼 있어요. 현재 크루 프로필과 소속은 유지돼요. 다른 Google 계정을 선택하거나 문의해 주세요"
                CommunityLinkResult.SESSION_CHANGED -> "크루 프로필 연결 상태가 바뀌었어요. 현재 프로필을 확인한 뒤 다시 시도해 주세요"
                CommunityLinkResult.NOT_VERIFIED -> "Google 연결 결과를 확인하지 못했어요. 현재 크루 프로필은 유지돼요. 다시 확인해 주세요"
            }
            finishGoogleLink(ticket, status)
        } catch (_: GetCredentialCancellationException) {
            finishGoogleLink(ticket, "Google 계정 선택을 취소했어요 · 현재 크루 프로필은 유지돼요")
        } catch (_: GoogleCredentialPickerBusyException) {
            finishGoogleLink(ticket, "다른 Google 계정 선택이 열려 있어요 · 먼저 연 선택을 마친 뒤 다시 시도해 주세요")
        } catch (_: TimeoutCancellationException) {
            finishGoogleLink(ticket, "Google 연결 응답을 확인하지 못했어요 · 현재 크루 프로필을 유지하고 결과를 다시 확인해 주세요")
        } catch (cancelled: CancellationException) {
            finishGoogleLink(ticket, "Google 연결을 취소했어요 · 현재 크루 프로필은 유지돼요")
            throw cancelled
        } catch (_: NoCredentialException) {
            finishGoogleLink(ticket, "기기의 Google 계정과 Google Play 서비스를 확인해 주세요 · 현재 크루 프로필은 유지돼요")
        } catch (_: Exception) {
            // Never expose a provider message, token, UID, or account address in errors/logs.
            finishGoogleLink(ticket, "Google 계정에 연결하지 못했어요 · 현재 크루 프로필은 유지돼요. 네트워크와 로그인 설정을 확인해 주세요")
        }
    }

    private fun finishGoogleLink(ticket: GoogleTicket, status: String) = synchronized(lock) {
        if (currentGoogleLocked(ticket)) {
            activeGoogle = null
            val state = googleState(auth?.currentUser)
            mutableGoogleLink.value = if (!state.linked) state.copy(status = status) else {
                val remembered = accountPreferences.edit().putString(verifiedGoogleKey, ticket.uid).commit()
                state.copy(status = if (remembered) "Google 계정 연결됨 · 현재 크루 소속과 권한은 유지돼요. 기록·포인트 백업은 별도예요"
                    else "Google 계정은 연결됐어요 · 기기 확인 정보를 저장하지 못해 다음 실행에서 다시 연결이 필요할 수 있어요")
            }
        }
    }
    private fun reconcileLateGoogleLink(ticket: GoogleTicket) = synchronized(lock) {
        // Firebase Tasks can complete after coroutine timeout. Accept metadata only for the same UID.
        if (!closed && epoch == ticket.epoch && activeGoogle == null && acceptedUid == ticket.uid && auth?.currentUser?.uid == ticket.uid &&
            googleIdentity(auth?.currentUser)?.googleLinked == true) {
            val remembered = accountPreferences.edit().putString(verifiedGoogleKey, ticket.uid).commit()
            mutableGoogleLink.value = googleState(auth?.currentUser).let { state ->
                if (remembered) state else state.copy(status = "Google 계정은 연결됐어요 · 기기 확인 정보를 저장하지 못해 다음 실행에서 다시 연결이 필요할 수 있어요")
            }
        }
    }

    /** Explicit recovery only from no current session, and only after the server confirms an existing profile. */
    suspend fun restoreGoogleAccount(context: Context) {
        val currentAuth = auth ?: return
        val ticket = synchronized(lock) {
            if (closed || activeLogin || activeGoogle != null) return
            if (currentAuth.currentUser != null || acceptedUid.isNotBlank()) {
                mutableGoogleLink.value = googleState(currentAuth.currentUser).copy(status = "현재 크루 프로필을 다른 계정으로 바꾸지 않았어요 · Google 연결은 현재 프로필에서 진행해 주세요")
                return
            }
            epoch++
            GoogleTicket(epoch, ++googleOperation, "", restoring = true).also {
                activeGoogle = it; activeLogin = true
                mutableGoogleLink.value = CommunityGoogleLinkState(busy = true, status = "이전에 크루 프로필에 연결한 Google 계정을 선택해 주세요")
            }
        }
        try {
            val credential = pickGoogleCredential(context)
            if (!currentGoogle(ticket)) return
            val result = withTimeout(15_000) {
                restoreExistingCommunityIdentity(credential, object : CommunityRestoreSession<AuthCredential> {
                    override fun identity() = synchronized(lock) {
                        if (currentGoogleLocked(ticket)) googleIdentity(currentAuth.currentUser) else null
                    }
                    override suspend fun signIn(credential: AuthCredential): CommunityLinkIdentity? {
                        check(currentGoogle(ticket) && currentAuth.currentUser == null)
                        return googleIdentity(currentAuth.signInWithCredential(credential).await().user)
                    }
                    override suspend fun hasExistingProfile(uid: String): Boolean {
                        check(currentGoogle(ticket) && currentAuth.currentUser?.uid == uid)
                        return profile(uid).get(Source.SERVER).await().exists()
                    }
                    override fun rejectTemporaryIdentity(uid: String) = synchronized(lock) {
                        if (currentGoogleLocked(ticket) && currentAuth.currentUser?.uid == uid) currentAuth.signOut()
                    }
                })
            }
            synchronized(lock) {
                if (!currentGoogleLocked(ticket)) return
                if (result.status == CommunityRestoreStatus.RESTORED && currentAuth.currentUser?.uid == result.uid) {
                    check(accountPreferences.edit().putString(verifiedGoogleKey, result.uid).commit())
                    acceptedUid = result.uid; activeLogin = false; activeGoogle = null
                    mutableGoogleLink.value = googleState(currentAuth.currentUser).copy(status = "기존 크루 프로필에 다시 연결했어요 · 소속은 목록을 새로고침해 확인해 주세요. 기기 기록·포인트·옷장은 별도예요")
                } else {
                    failGoogleRestoreLocked(ticket, if (result.status == CommunityRestoreStatus.NO_PROFILE)
                        "선택한 Google 계정에 연결된 크루 프로필이 없어요 · 요약 백업 로그인과 크루 연결은 별도예요"
                    else "기존 크루 프로필을 확인하지 못했어요 · 다른 프로필을 만들거나 합치지 않았어요")
                }
            }
        } catch (_: GetCredentialCancellationException) {
            failGoogleRestore(ticket, "Google 계정 선택을 취소했어요 · 크루 프로필을 바꾸지 않았어요")
        } catch (_: GoogleCredentialPickerBusyException) {
            failGoogleRestore(ticket, "다른 Google 계정 선택이 열려 있어요 · 먼저 연 선택을 마친 뒤 다시 시도해 주세요")
        } catch (_: TimeoutCancellationException) {
            failGoogleRestore(ticket, "서버에서 기존 크루 프로필을 확인하지 못했어요 · 네트워크를 확인하고 다시 시도해 주세요")
        } catch (cancelled: CancellationException) {
            failGoogleRestore(ticket, "크루 프로필 다시 연결을 취소했어요")
            throw cancelled
        } catch (_: Exception) {
            failGoogleRestore(ticket, "기존 크루 프로필에 연결하지 못했어요 · 네트워크와 Google 로그인 설정을 확인해 주세요")
        }
    }
    private fun failGoogleRestore(ticket: GoogleTicket, status: String) = synchronized(lock) { failGoogleRestoreLocked(ticket, status) }
    private fun failGoogleRestoreLocked(ticket: GoogleTicket, status: String) {
        if (currentGoogleLocked(ticket)) {
            activeGoogle = null; activeLogin = false
            if (auth?.currentUser?.uid.orEmpty() != acceptedUid) auth?.signOut()
            mutableGoogleLink.value = CommunityGoogleLinkState(status = status)
        }
    }

    private suspend fun <T> network(action: suspend () -> T): T {
        val ticket = synchronized(lock) { check(!closed); epoch }
        try {
            val value = withTimeout(15_000) { action() }
            check(synchronized(lock) { !closed && epoch == ticket }) { "크루 프로필 연결이 바뀌었어요. 다시 확인해 주세요." }
            return value
        } catch (_: TimeoutCancellationException) { error("크루·파티 응답 시간이 초과됐어요. 저장 여부는 새로고침해 주세요.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: FirebaseFirestoreException) {
            error(if (failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "Firebase 크루·파티 규칙 또는 참여 권한을 확인해 주세요." else "크루·파티에 연결하지 못했어요. 저장 여부는 새로고침해 주세요.")
        } catch (_: FirebaseAuthException) { error("Firebase 로그인 설정과 현재 크루 프로필의 연결 상태를 확인해 주세요.") }
        catch (_: FirebaseException) { error("Firebase 연결을 확인해 주세요. 계정 정보는 공유 결과에 포함되지 않아요.") }
    }

    suspend fun connect(nickname: String) {
        require(nickname.matches(Regex("[\\w가-힣 -]{2,24}"))) { "닉네임은 2~24자로 입력해 주세요." }
        val currentAuth = auth ?: error("Firebase 설정을 포함한 새 APK가 필요해요.")
        val ticket = synchronized(lock) {
            check(!closed && !activeLogin && activeGoogle == null) { "크루 프로필 연결이 끝난 뒤 다시 시도해 주세요." }
            val mode = communityConnectMode(acceptedUid, currentAuth.currentUser?.uid.orEmpty())
            check(mode != CommunityConnectMode.REJECT_UNVERIFIED) { "크루 프로필 연결 상태를 확인하고 다시 시도해 주세요." }
            epoch++; activeLogin = true
            ConnectTicket(epoch, acceptedUid, mode)
        }
        try {
            val user = withTimeout(15_000) {
                if (ticket.mode == CommunityConnectMode.REUSE_CURRENT) currentAuth.currentUser
                else {
                    check(currentAuth.currentUser == null) { "크루 프로필 연결 상태가 바뀌었어요." }
                    currentAuth.signInAnonymously().await().user
                }
            }
                ?: error("크루 프로필을 연결하지 못했어요.")
            synchronized(lock) { check(currentConnectLocked(ticket, user)) { "크루 프로필 연결이 바뀌었어요." } }
            network {
                val ref = profile(user.uid)
                val existing = ref.get(Source.SERVER).await()
                synchronized(lock) { check(currentConnectLocked(ticket, user)) { "크루 프로필 연결이 바뀌었어요." } }
                if (existing.exists()) ref.update(mapOf("nickname" to nickname, "updatedAt" to FieldValue.serverTimestamp())).await()
                else ref.set(mapOf("nickname" to nickname, "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(), "joinedRoomIds" to emptyList<String>(),
                    "lastRoomId" to "", "createdRoomCount" to 0, "lastRoomCreatedAt" to Timestamp(0, 0))).await()
            }
            synchronized(lock) {
                check(currentConnectLocked(ticket, user)) { "크루 프로필 연결이 바뀌었어요." }
                acceptedUid = user.uid; activeLogin = false
                syncGoogleLinkLocked()
            }
        } catch (_: TimeoutCancellationException) { reconcileLogin(ticket.epoch); error("크루 프로필 연결 시간이 초과됐어요. 다시 시도해 주세요.") }
        catch (cancelled: CancellationException) { reconcileLogin(ticket.epoch); throw cancelled }
        catch (_: FirebaseAuthException) { reconcileLogin(ticket.epoch); error("Firebase Authentication에서 익명 로그인을 켜 주세요.") }
        catch (_: FirebaseException) { reconcileLogin(ticket.epoch); error("크루 프로필 연결에 실패했어요. 네트워크와 로그인 설정을 확인해 주세요.") }
        catch (failure: Exception) { reconcileLogin(ticket.epoch); throw failure }
    }
    private data class ConnectTicket(val epoch: Long, val initialUid: String, val mode: CommunityConnectMode)
    private fun currentConnectLocked(ticket: ConnectTicket, user: FirebaseUser) = !closed && epoch == ticket.epoch && activeLogin &&
        validCommunityConnectIdentity(ticket.mode, ticket.initialUid, googleIdentity(user), user.isAnonymous, auth?.currentUser?.uid.orEmpty())
    private fun reconcileLogin(ticket: Long) = synchronized(lock) {
        if (epoch == ticket) {
            activeLogin = false
            if (auth?.currentUser?.uid.orEmpty() != acceptedUid) auth?.signOut()
        }
    }
    fun signOut() = synchronized(lock) {
        epoch++; activeLogin = false; activeGoogle = null; acceptedUid = ""
        accountPreferences.edit().remove(verifiedGoogleKey).commit()
        auth?.signOut()
        mutableGoogleLink.value = CommunityGoogleLinkState(status = "크루 프로필 연결을 해제했어요 · Google에 연결했던 프로필은 같은 Google 계정으로 다시 연결할 수 있어요")
    }
    suspend fun updateNickname(nickname: String) = network {
        profile(identity()).update(mapOf("nickname" to nickname, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun snapshot(): FirebaseRoomSnapshot {
        val deleted = mutableListOf<String>()
        var cleanupIdentity = ""
        val result = network {
        val current = uid
        cleanupIdentity = current
        val own = if (current.isBlank()) null else profile(current).get(Source.SERVER).await().takeIf { it.exists() }
        val joined = (own?.get("joinedRoomIds") as? List<*>)?.filterIsInstance<String>().orEmpty().toMutableSet()
        val rooms = mutableMapOf<String, DocumentSnapshot>()
        for (kind in listOf("crew", "party")) {
            // Reuse the existing visibility/kind/createdAt index. Tombstones do not
            // consume a whole first page: scan at most 3 x 50 documents per kind,
            // returning at most 50 visible documents plus explicitly joined ones.
            var cursor: DocumentSnapshot? = null
            var visible = 0
            for (page in 0 until PUBLIC_SCAN_PAGES) {
                var query = db().collection("communityRooms").whereEqualTo("visibility", "open").whereEqualTo("kind", kind)
                    .orderBy("createdAt", Query.Direction.DESCENDING).limit(PUBLIC_PAGE_SIZE.toLong())
                cursor?.let { query = query.startAfter(it) }
                val documents = query.get(Source.SERVER).await().documents
                for (document in documents) {
                    if (document.getString("status") != "deleted" && visible < PUBLIC_PAGE_SIZE) {
                        rooms[document.id] = document; visible++
                    } else if (document.id in joined) rooms[document.id] = document
                }
                if (visible >= PUBLIC_PAGE_SIZE || documents.size < PUBLIC_PAGE_SIZE) break
                cursor = documents.lastOrNull() ?: break
            }
        }
        // Keep joined groups available after they leave the latest public page.
        for (id in joined - rooms.keys) room(id).get(Source.SERVER).await().takeIf { it.exists() }?.let { rooms[id] = it }
        // A party's parent can be older than the crew discovery page.
        val parents = rooms.values.filter { it.getString("kind") == "party" && it.getString("status") != "deleted" }
            .mapNotNull { it.getString("crewId")?.takeIf { id -> id.matches(Regex("[A-Za-z0-9]{20}")) } }.toSet()
        for (id in parents - rooms.keys) room(id).get(Source.SERVER).await().takeIf { it.exists() && it.getString("kind") == "crew" }?.let { rooms[id] = it }
        deleted += joined.filter { rooms[it]?.getString("status") == "deleted" }.take(DELETED_CLEANUP_LIMIT)
        val visible = rooms.values.filter { it.getString("status") != "deleted" }
        FirebaseRoomSnapshot(visible.filter { it.getString("kind") == "crew" }.map { crew(it, current, it.id in joined) },
            visible.filter { it.getString("kind") == "party" }.map { party(it, current, it.id in joined) }, own != null)
        }
        // Keep optional maintenance outside the authoritative read's 15s budget.
        // Its own timeout cannot turn a successful snapshot into a failed read.
        cleanupDeletedMemberships(deleted, cleanupIdentity)
        return result
    }

    /** Public children of the selected crew, independently of global discovery. */
    suspend fun partiesForCrew(crewId: String): List<Party> = network {
        val current = uid
        val parent = room(crewId).get(Source.SERVER).await()
        require(parent.getString("kind") == "crew" && parent.getString("status") == "active") { "참여할 크루를 다시 확인해 주세요." }
        val own = if (current.isBlank()) null else profile(current).get(Source.SERVER).await()
        val joined = (own?.get("joinedRoomIds") as? List<*>)?.filterIsInstance<String>().orEmpty().toSet()
        // Equality-only filters use Firestore index merging. No orderBy is
        // introduced here; sorting the bounded result does not require a new
        // visibility/kind/crewId/status/createdAt composite index.
        db().collection("communityRooms").whereEqualTo("visibility", "open").whereEqualTo("kind", "party")
            .whereEqualTo("crewId", crewId).whereEqualTo("status", "active").limit(PUBLIC_PAGE_SIZE.toLong())
            .get(Source.SERVER).await().documents.map { party(it, current, it.id in joined) }
            .sortedBy { it.startsAtMillis }
    }

    suspend fun members(id: String): List<CrewMember> = network {
        identity()
        room(id).collection("members").limit(100).get(Source.SERVER).await().documents.map {
            CrewMember(it.id, it.getString("nickname").orEmpty(), it.getString("role") == "owner", it.getString("role") == "dj")
        }
    }

    suspend fun createCrew(input: NewCrew): Crew {
        val id = create(mapOf("kind" to "crew", "title" to input.name, "description" to input.description,
            "region" to input.region, "paceLabel" to input.paceLabel, "capacity" to 100, "crewId" to "",
            "partyKind" to "", "startsAtMillis" to 0L, "meetingLabel" to "", "distanceKm" to 0.0))
        return network { crew(room(id).get(Source.SERVER).await(), identity(), true) }
    }
    suspend fun createParty(input: NewParty): Party {
        require(!input.crewId.isNullOrBlank()) { "파티를 만들 크루를 먼저 선택해 주세요." }
        val id = create(mapOf("kind" to "party", "title" to input.title, "description" to "", "region" to "",
            "paceLabel" to input.paceLabel, "capacity" to input.capacity, "crewId" to input.crewId.orEmpty(),
            "partyKind" to input.kind.name.lowercase(), "startsAtMillis" to input.startsAtMillis,
            "meetingLabel" to input.meetingLabel, "distanceKm" to input.distanceKm))
        return network { party(room(id).get(Source.SERVER).await(), identity(), true) }
    }
    private suspend fun create(fields: Map<String, Any>): String = network {
        val user = identity()
        val ref = db().collection("communityRooms").document()
        db().runTransaction { tx ->
            val p = tx.get(profile(user))
            require(Timestamp.now().seconds - (p.getTimestamp("lastRoomCreatedAt")?.seconds ?: 0) >= 10) { "크루와 파티는 10초 간격으로 만들어 주세요." }
            if (fields["kind"] == "party") {
                val parent = fields["crewId"] as String
                val crew = tx.get(room(parent)); val membership = tx.get(member(parent, user))
                require(crew.getString("kind") == "crew" && crew.getString("status") == "active" && membership.exists()) {
                    "참여한 크루 안에서 파티를 만들어 주세요."
                }
            }
            tx.set(ref, fields + mapOf("ownerUid" to user, "visibility" to "open", "status" to "active", "memberCount" to 1,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(ref.collection("members").document(user), memberFields(user, p.getString("nickname").orEmpty(), "owner"))
            tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayUnion(ref.id), "lastRoomId" to ref.id,
                "createdRoomCount" to FieldValue.increment(1), "lastRoomCreatedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
        ref.id
    }

    suspend fun join(id: String) = network {
        val user = identity()
        db().runTransaction { tx ->
            val r = tx.get(room(id)); val p = tx.get(profile(user)); val mine = tx.get(member(id, user))
            if (r.getString("kind") == "party") {
                val parent = r.getString("crewId").orEmpty()
                require(parent.matches(Regex("[A-Za-z0-9]{20}"))) { "이전 독립 파티에는 새로 참여할 수 없어요. 크루에서 새 파티를 만들어 주세요." }
                val crew = tx.get(room(parent)); val parentMember = tx.get(member(parent, user))
                require(crew.getString("kind") == "crew" && crew.getString("status") == "active" && parentMember.exists()) { "먼저 이 파티의 크루에 참여해 주세요." }
            }
            if (!mine.exists()) {
                require(r.getString("status") == "active") { "종료되거나 삭제된 파티예요." }
                require((r.getLong("memberCount") ?: 0) < (r.getLong("capacity") ?: 0)) { "정원이 가득 찼어요." }
                require(r.getString("kind") != "party" || (r.getLong("startsAtMillis") ?: 0) > System.currentTimeMillis()) { "이미 시작한 파티예요." }
                tx.set(member(id, user), memberFields(user, p.getString("nickname").orEmpty(), "member"))
                tx.update(room(id), mapOf("memberCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayUnion(id), "lastRoomId" to id, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    suspend fun leave(id: String) = network { leaveMembership(id, identity()) }
    private suspend fun leaveMembership(id: String, user: String) {
        db().runTransaction { tx ->
            val r = tx.get(room(id)); val mine = tx.get(member(id, user)); val p = tx.get(profile(user))
            require(r.getString("ownerUid") != user || r.getString("status") == "deleted") { "크루장·주최자는 먼저 파티를 종료·삭제해 주세요." }
            if (mine.exists()) {
                tx.delete(member(id, user))
                tx.update(room(id), mapOf("memberCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayRemove(id), "lastRoomId" to id, "updatedAt" to FieldValue.serverTimestamp()))
            } else if (r.getString("status") == "deleted" && (p.get("joinedRoomIds") as? List<*>)?.contains(id) == true) {
                // An already-removed member may still have an old private link.
                tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayRemove(id), "lastRoomId" to id, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    private suspend fun cleanupDeletedMemberships(ids: List<String>, user: String) {
        if (user.isBlank() || uid != user) return
        // withTimeoutOrNull catches this local budget only. Parent cancellation
        // still propagates and is never reported as a successful maintenance run.
        withTimeoutOrNull(DELETED_CLEANUP_MILLIS) {
            for (id in ids.take(DELETED_CLEANUP_LIMIT)) {
                if (uid != user) break
                tryCleanupDeletedMembership(id, user)
            }
        }
    }
    private suspend fun tryCleanupDeletedMembership(id: String, user: String): Boolean = try {
        leaveMembership(id, user); true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: FirebaseException) {
        // A committed deletion/list read remains valid if this optional cleanup
        // races with another client or momentarily loses the network.
        false
    }

    suspend fun setDj(id: String, memberId: String, enabled: Boolean) = network {
        identity(); member(id, memberId).update("role", if (enabled) "dj" else "member").await()
    }
    suspend fun cancelParty(id: String) = network {
        identity(); room(id).update(mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    /** Delete from discovery without destroying messages or other participants' data. */
    suspend fun deleteParty(id: String) {
        val user = network {
            val user = identity()
            db().runTransaction { tx ->
                val party = tx.get(room(id))
                require(party.getString("kind") == "party") { "파티만 삭제할 수 있어요." }
                if (party.getString("status") != "deleted") tx.update(room(id), mapOf("status" to "deleted", "updatedAt" to FieldValue.serverTimestamp()))
            }.await()
            user
        }
        // The status mutation has completed before optional maintenance starts.
        // A local cleanup timeout cannot overturn this committed deletion.
        cleanupDeletedMemberships(listOf(id), user)
    }
    private suspend fun requireReadableMembership(id: String, user: String) {
        val group = room(id).get(Source.SERVER).await()
        check(group.exists() && group.getString("status") != "deleted" && member(id, user).get(Source.SERVER).await().exists()) { "참여 중인 크루·파티에서 채팅을 열어 주세요." }
        if (group.getString("kind") == "party") {
            val parent = group.getString("crewId").orEmpty()
            check(parent.matches(Regex("[A-Za-z0-9]{20}"))) { "크루에 속한 파티에서 채팅을 열어 주세요." }
            val crew = room(parent).get(Source.SERVER).await()
            check(crew.getString("kind") == "crew" && crew.getString("status") == "active" && member(parent, user).get(Source.SERVER).await().exists()) { "먼저 이 파티의 크루에 참여해 주세요." }
        }
    }
    suspend fun canAnnounce(id: String): Boolean = network {
        val user = identity()
        requireReadableMembership(id, user)
        val mine = member(id, user).get(Source.SERVER).await()
        if (!mine.exists()) false
        else if (mine.getString("role") in setOf("owner", "dj")) true
        else {
            val parent = room(id).get(Source.SERVER).await().getString("crewId").orEmpty()
            parent.isNotBlank() && room(parent).get(Source.SERVER).await().getString("ownerUid") == user
        }
    }

    fun messages(id: String) = callbackFlow {
        val user = identity()
        val ticket = synchronized(lock) { epoch }
        // Confirm current membership from the server before showing any cached content.
        network { requireReadableMembership(id, user) }
        val listener = room(id).collection("messages").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                if (failure != null) { close(IllegalStateException("채팅방 참여 권한과 연결을 확인해 주세요.")); return@addSnapshotListener }
                if (synchronized(lock) { closed || epoch != ticket } || uid != user) { close(); return@addSnapshotListener }
                if (snapshot != null) trySend(FirebaseRoomMessages(snapshot.documents.filterNot { it.metadata.hasPendingWrites() }.reversed().map {
                    ChatMessage(it.id, id, it.getString("nickname").orEmpty(), it.getString("body").orEmpty(),
                        it.getTimestamp("createdAt")?.toDate()?.time ?: 0, it.getString("kind") == "announcement", it.getString("authorUid") == user)
                }, snapshot.metadata.isFromCache))
            }
        awaitClose { listener.remove() }
    }
    suspend fun sendMessage(id: String, body: String, notice: Boolean) = network {
        val user = identity(); require(body.isNotBlank() && body.length <= 1000) { "메시지는 1~1000자로 입력해 주세요." }
        val message = room(id).collection("messages").document()
        db().runTransaction { tx ->
            val mine = tx.get(member(id, user)); val p = tx.get(profile(user))
            require(mine.exists()) { "이 크루나 파티에 먼저 참여해 주세요." }
            require(Timestamp.now().seconds - (mine.getTimestamp("lastMessageAt")?.seconds ?: 0) >= 2) { "메시지는 2초 간격으로 보내 주세요." }
            val nickname = p.getString("nickname").orEmpty()
            tx.set(message, mapOf("authorUid" to user, "nickname" to nickname, "body" to body.trim(),
                "kind" to if (notice) "announcement" else "chat", "createdAt" to FieldValue.serverTimestamp()))
            tx.update(member(id, user), mapOf("nickname" to nickname, "lastMessageAt" to FieldValue.serverTimestamp(), "lastMessageId" to message.id))
        }.await()
    }
    fun close() = synchronized(lock) {
        closed = true; epoch++; activeGoogle = null
        mutableGoogleLink.value = mutableGoogleLink.value.copy(busy = false)
        auth?.removeAuthStateListener(authListener)
    }

    private fun memberFields(user: String, nickname: String, role: String) = mapOf("uid" to user, "nickname" to nickname,
        "role" to role, "joinedAt" to FieldValue.serverTimestamp(), "lastMessageAt" to Timestamp(0, 0), "lastMessageId" to "")
    private fun crew(doc: DocumentSnapshot, user: String, joined: Boolean) = Crew(doc.id, doc.getString("title").orEmpty(),
        doc.getString("description").orEmpty(), doc.getString("region").orEmpty(), doc.getString("paceLabel").orEmpty(),
        doc.getLong("memberCount")?.toInt() ?: 0, joined, doc.getString("ownerUid") == user, isOpen = true)
    private fun party(doc: DocumentSnapshot, user: String, joined: Boolean) = Party(doc.id, doc.getString("crewId").orEmpty(),
        doc.getString("title").orEmpty(), if (doc.getString("partyKind") == "remote") PartyKind.REMOTE else PartyKind.LOCAL,
        doc.getLong("startsAtMillis") ?: 0, doc.getString("meetingLabel").orEmpty(), distanceKm = doc.getDouble("distanceKm") ?: 0.0,
        paceLabel = doc.getString("paceLabel").orEmpty(), capacity = doc.getLong("capacity")?.toInt() ?: 0,
        participantCount = doc.getLong("memberCount")?.toInt() ?: 0, joined = joined,
        cancelled = doc.getString("status") == "cancelled", isOrganizer = doc.getString("ownerUid") == user)
    companion object {
        private const val APP_NAME = "runmoni-community"
        private const val PUBLIC_PAGE_SIZE = 50
        private const val PUBLIC_SCAN_PAGES = 3
        private const val DELETED_CLEANUP_LIMIT = 5
        private const val DELETED_CLEANUP_MILLIS = 1_000L
    }
}
