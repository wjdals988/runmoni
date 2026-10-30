package com.runmoni.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.*
import com.runmoni.app.BuildConfig
import com.runmoni.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

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
    private var acceptedUid = auth?.currentUser?.uid.orEmpty()
    private val authListener = FirebaseAuth.AuthStateListener { current ->
        synchronized(lock) {
            if (!closed && !activeLogin && current.currentUser?.uid.orEmpty() != acceptedUid) {
                // Reject a late sign-in Task after cancellation/timeout.
                epoch++; acceptedUid = ""
                if (current.currentUser != null) current.signOut()
            }
        }
    }
    init { auth?.addAuthStateListener(authListener) }
    val configured get() = database != null && auth != null
    val uid get() = synchronized(lock) { acceptedUid.takeIf { auth?.currentUser?.uid == it }.orEmpty() }
    private fun db() = database ?: error("Firebase 방 설정이 없는 빌드예요. 설정을 포함한 새 APK가 필요해요.")
    private fun identity(): String = uid.takeIf { it.isNotBlank() } ?: error("닉네임으로 방 프로필을 먼저 연결해 주세요.")
    private fun profile(user: String) = db().collection("communityProfiles").document(user)
    private fun room(id: String) = db().collection("communityRooms").document(id.also { require(it.matches(Regex("[A-Za-z0-9]{20}"))) { "방 주소를 확인해 주세요." } })
    private fun member(id: String, user: String) = room(id).collection("members").document(user)

    private suspend fun <T> network(action: suspend () -> T): T {
        val ticket = synchronized(lock) { check(!closed); epoch }
        try {
            val value = withTimeout(15_000) { action() }
            check(synchronized(lock) { !closed && epoch == ticket }) { "방 연결이 바뀌었어요. 다시 확인해 주세요." }
            return value
        } catch (_: TimeoutCancellationException) { error("Firebase 방 응답 시간이 초과됐어요. 저장 여부는 새로고침해 주세요.") }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: FirebaseFirestoreException) {
            error(if (failure.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "Firebase 방 규칙 또는 참여 권한을 확인해 주세요." else "Firebase 방에 연결하지 못했어요. 저장 여부는 새로고침해 주세요.")
        } catch (_: FirebaseAuthException) { error("Firebase Authentication의 익명 로그인을 사용 설정해 주세요.") }
        catch (_: FirebaseException) { error("Firebase 연결을 확인해 주세요. 계정 정보는 공유 결과에 포함되지 않아요.") }
    }

    suspend fun connect(nickname: String) {
        require(nickname.matches(Regex("[\\w가-힣 -]{2,24}"))) { "닉네임은 2~24자로 입력해 주세요." }
        val currentAuth = auth ?: error("Firebase 설정을 포함한 새 APK가 필요해요.")
        val ticket = synchronized(lock) { epoch++; activeLogin = true; epoch }
        try {
            val user = withTimeout(15_000) { currentAuth.currentUser ?: currentAuth.signInAnonymously().await().user }
                ?: error("방 프로필을 연결하지 못했어요.")
            synchronized(lock) {
                check(!closed && epoch == ticket) { "방 연결이 바뀌었어요." }
                acceptedUid = user.uid; activeLogin = false
            }
            network {
                val ref = profile(user.uid)
                val existing = ref.get(Source.SERVER).await()
                if (existing.exists()) ref.update(mapOf("nickname" to nickname, "updatedAt" to FieldValue.serverTimestamp())).await()
                else ref.set(mapOf("nickname" to nickname, "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(), "joinedRoomIds" to emptyList<String>(),
                    "lastRoomId" to "", "createdRoomCount" to 0, "lastRoomCreatedAt" to Timestamp(0, 0))).await()
            }
        } catch (_: TimeoutCancellationException) { reconcileLogin(ticket); error("방 프로필 연결 시간이 초과됐어요. 다시 시도해 주세요.") }
        catch (cancelled: CancellationException) { reconcileLogin(ticket); throw cancelled }
        catch (_: FirebaseAuthException) { reconcileLogin(ticket); error("Firebase Authentication에서 익명 로그인을 켜 주세요.") }
        catch (_: FirebaseException) { reconcileLogin(ticket); error("Firebase 방 프로필 연결에 실패했어요. 네트워크와 익명 로그인 설정을 확인해 주세요.") }
        catch (failure: Exception) { reconcileLogin(ticket); throw failure }
    }
    private fun reconcileLogin(ticket: Long) = synchronized(lock) {
        if (epoch == ticket) {
            activeLogin = false
            if (auth?.currentUser?.uid.orEmpty() != acceptedUid) auth?.signOut()
        }
    }
    fun signOut() = synchronized(lock) { epoch++; activeLogin = false; acceptedUid = ""; auth?.signOut(); Unit }
    suspend fun updateNickname(nickname: String) = network {
        profile(identity()).update(mapOf("nickname" to nickname, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun snapshot(): FirebaseRoomSnapshot = network {
        val current = uid
        val own = if (current.isBlank()) null else profile(current).get(Source.SERVER).await().takeIf { it.exists() }
        val joined = (own?.get("joinedRoomIds") as? List<*>)?.filterIsInstance<String>().orEmpty().toSet()
        val rooms = mutableMapOf<String, DocumentSnapshot>()
        for (kind in listOf("crew", "party")) {
            db().collection("communityRooms").whereEqualTo("visibility", "open").whereEqualTo("kind", kind)
                .orderBy("createdAt", Query.Direction.DESCENDING).limit(50).get(Source.SERVER).await()
                .documents.forEach { rooms[it.id] = it }
        }
        // Keep joined rooms available after they leave the latest public page.
        for (id in joined - rooms.keys) room(id).get(Source.SERVER).await().takeIf { it.exists() }?.let { rooms[id] = it }
        FirebaseRoomSnapshot(rooms.values.filter { it.getString("kind") == "crew" }.map { crew(it, current, it.id in joined) },
            rooms.values.filter { it.getString("kind") == "party" }.map { party(it, current, it.id in joined) }, own != null)
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
            require((p.getLong("createdRoomCount") ?: 0) < 20) { "검토 기간에는 프로필당 최대20개 방을 만들 수 있어요." }
            require(Timestamp.now().seconds - (p.getTimestamp("lastRoomCreatedAt")?.seconds ?: 0) >= 10) { "방은 10초 간격으로 만들어 주세요." }
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
            if (!mine.exists()) {
                require(r.getString("status") == "active") { "종료된 방이에요." }
                require((r.getLong("memberCount") ?: 0) < (r.getLong("capacity") ?: 0)) { "정원이 가득 찼어요." }
                require(r.getString("kind") != "party" || (r.getLong("startsAtMillis") ?: 0) > System.currentTimeMillis()) { "이미 시작한 파티예요." }
                tx.set(member(id, user), memberFields(user, p.getString("nickname").orEmpty(), "member"))
                tx.update(room(id), mapOf("memberCount" to FieldValue.increment(1), "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayUnion(id), "lastRoomId" to id, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    suspend fun leave(id: String) = network {
        val user = identity()
        db().runTransaction { tx ->
            val r = tx.get(room(id)); val mine = tx.get(member(id, user)); tx.get(profile(user))
            require(r.getString("ownerUid") != user) { "방장·주최자는 방을 나갈 수 없어요." }
            if (mine.exists()) {
                tx.delete(member(id, user))
                tx.update(room(id), mapOf("memberCount" to FieldValue.increment(-1), "updatedAt" to FieldValue.serverTimestamp()))
                tx.update(profile(user), mapOf("joinedRoomIds" to FieldValue.arrayRemove(id), "lastRoomId" to id, "updatedAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }
    suspend fun setDj(id: String, memberId: String, enabled: Boolean) = network {
        identity(); member(id, memberId).update("role", if (enabled) "dj" else "member").await()
    }
    suspend fun cancelParty(id: String) = network {
        identity(); room(id).update(mapOf("status" to "cancelled", "updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun canAnnounce(id: String): Boolean = network {
        val user = identity()
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
        network { check(member(id, user).get(Source.SERVER).await().exists()) { "이 방에 먼저 입장해 주세요." } }
        val listener = room(id).collection("messages").orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                if (failure != null) { close(IllegalStateException("방 채팅 권한과 연결을 확인해 주세요.")); return@addSnapshotListener }
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
            require(mine.exists()) { "이 방에 먼저 입장해 주세요." }
            require(Timestamp.now().seconds - (mine.getTimestamp("lastMessageAt")?.seconds ?: 0) >= 2) { "메시지는 2초 간격으로 보내 주세요." }
            val nickname = p.getString("nickname").orEmpty()
            tx.set(message, mapOf("authorUid" to user, "nickname" to nickname, "body" to body.trim(),
                "kind" to if (notice) "announcement" else "chat", "createdAt" to FieldValue.serverTimestamp()))
            tx.update(member(id, user), mapOf("nickname" to nickname, "lastMessageAt" to FieldValue.serverTimestamp(), "lastMessageId" to message.id))
        }.await()
    }
    fun close() = synchronized(lock) { closed = true; epoch++; auth?.removeAuthStateListener(authListener); Unit }

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
    companion object { private const val APP_NAME = "runmoni-community" }
}
