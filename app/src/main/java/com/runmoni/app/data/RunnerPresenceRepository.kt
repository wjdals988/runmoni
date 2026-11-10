package com.runmoni.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.runmoni.app.domain.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Date
import java.util.UUID

/** Explicit, approximate presence on Spark. No login, recorder startup or background subscription here. */
class RunnerPresenceRepository(
    private val firestore: () -> FirebaseFirestore?,
    private val acceptedUid: () -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class Context(val session: RunnerShareSession, val db: FirebaseFirestore)
    private val lock = Any()
    private var generation = 0L
    private var active: Context? = null
    private val contexts = linkedMapOf<String, Context>()

    fun activeSession(): RunnerShareSession? = synchronized(lock) { active?.session }

    /** Fence pending SDK callbacks synchronously, before launching network cleanup.
     * An older captured session never invalidates a newer explicit consent. */
    fun invalidate(session: RunnerShareSession? = activeSession()): RunnerShareSession? = synchronized(lock) {
        val expected = session ?: return@synchronized null
        val context = contexts[expected.token]?.takeIf { it.session === expected } ?: return@synchronized null
        if (active === context) { generation++; active = null }
        expected
    }

    private fun current(context: Context): Boolean = synchronized(lock) {
        active === context && generation == context.session.generation && acceptedUid() == context.session.uid &&
            firestore() === context.db
    }
    private fun requireCurrent(context: Context) { check(current(context)) { "위치 공유가 끝났어요. 다시 공유하려면 직접 켜 주세요." } }

    /** Create consent under the caller's controller lock; no Firebase request is made. */
    fun prepareEnable(runId: String, adultConfirmed: Boolean): RunnerShareSession {
        require(runId.isNotBlank() && adultConfirmed) { "진행 중인 러닝과 성인·위치 공개 동의를 확인해 주세요." }
        val db = firestore() ?: error("러너 지도에 사용할 프로필을 먼저 연결해 주세요.")
        val uid = acceptedUid().takeIf { it.isNotBlank() } ?: error("프로필을 먼저 연결해 주세요.")
        val context = synchronized(lock) {
            // Never evict the fence of an SDK transaction that may still finish
            // after coroutine cancellation. Successful revoke removes entries.
            check(contexts.size < 64) { "이전 위치 공유 연결을 정리한 뒤 다시 시도해 주세요." }
            val session = RunnerShareSession(uid, runId, UUID.randomUUID().toString(), ++generation)
            Context(session, db).also {
                active = it
                contexts[session.token] = it
            }
        }
        return context.session
    }

    suspend fun enable(runId: String, adultConfirmed: Boolean): RunnerShareSession =
        activate(prepareEnable(runId, adultConfirmed))

    /** Late activation can only use its already prepared context, never replace a newer one. */
    suspend fun activate(session: RunnerShareSession): RunnerShareSession {
        val context = synchronized(lock) { contexts[session.token]?.takeIf { it.session === session } }
            ?: error("위치 공개 동의가 바뀌었어요. 다시 확인해 주세요.")
        val db = context.db
        val uid = session.uid
        try {
            requireCurrent(context)
            withTimeout(10_000) {
                val ref = db.collection(CONSENTS).document(uid)
                db.runTransaction { tx ->
                    requireCurrent(context)
                    tx.get(ref) // This read fences a delayed enable against a newer revoke.
                    requireCurrent(context)
                    tx.set(ref, mapOf("runSessionToken" to context.session.token, "enabled" to true, "updatedAt" to FieldValue.serverTimestamp()))
                }.await()
            }
            requireCurrent(context)
            return context.session
        } catch (failure: Exception) {
            withContext(NonCancellable) { runCatching { withTimeout(10_000) { revoke(context.session) } } }
            throw failure
        }
    }

    suspend fun publish(session: RunnerShareSession, point: TrackPoint, nickname: String, avatar: AvatarStyle) {
        val context = synchronized(lock) { contexts[session.token] }
            ?.takeIf { it.session === session } ?: error("다시 위치 공유를 켜 주세요.")
        requireCurrent(context)
        check(RunnerPresencePolicy.shareable(point, nowMillis())) { "최근 정확한 위치를 받은 뒤 공유할 수 있어요." }
        val cell = RunnerPresenceCell.from(point.latitude, point.longitude)
        withTimeout(10_000) {
            val consent = context.db.collection(CONSENTS).document(session.uid)
            val marker = context.db.collection(PRESENCE).document(session.uid)
            context.db.runTransaction { tx ->
                requireCurrent(context)
                val authority = tx.get(consent)
                check(authority.getBoolean("enabled") == true && authority.getString("runSessionToken") == session.token) { "위치 공개 동의를 다시 확인해 주세요." }
                requireCurrent(context)
                check(RunnerPresencePolicy.shareable(point, nowMillis())) { "위치가 오래됐어요. 새 GPS 위치를 기다려 주세요." }
                val expiresAt = Timestamp(Date(nowMillis() + RunnerPresencePolicy.EXPIRY_MILLIS))
                tx.set(marker, mapOf("runSessionToken" to session.token, "latitudeE3" to cell.latitudeE3,
                    "longitudeE3" to cell.longitudeE3, "latBand" to cell.band.latitude, "lonBand" to cell.band.longitude,
                    "nickname" to nickname, "avatar" to publicAvatar(avatar), "visibility" to "approximate", "adultConfirmed" to true,
                    "updatedAt" to FieldValue.serverTimestamp(), "expiresAt" to expiresAt))
            }.await()
        }
        // If revocation happened during commit, its token-conditional transaction
        // removes this marker. Never write enabled=true from this path.
        requireCurrent(context)
    }

    /** Local revocation is immediate. Server removal needs connectivity; public reads expire within75s (normally60s). */
    suspend fun revoke(session: RunnerShareSession? = null) {
        val expected = invalidate(session ?: activeSession()) ?: return
        val context = synchronized(lock) { contexts[expected.token]?.takeIf { it.session === expected } } ?: return
        withTimeout(10_000) {
            val consent = context.db.collection(CONSENTS).document(context.session.uid)
            val marker = context.db.collection(PRESENCE).document(context.session.uid)
            context.db.runTransaction { tx ->
                val authority = tx.get(consent)
                val visible = tx.get(marker)
                // Old cleanup must not revoke or delete a new explicitly enabled run.
                if (!authority.exists() || authority.getString("runSessionToken") == context.session.token) {
                    tx.set(consent, mapOf("runSessionToken" to context.session.token, "enabled" to false,
                        "updatedAt" to FieldValue.serverTimestamp()))
                    if (visible.exists() && visible.getString("runSessionToken") == context.session.token) tx.delete(marker)
                } else {
                    // Even when this enable has not reached the server yet,
                    // touch the version its transaction read. Preserve a newer
                    // session's token/enabled state; its callback can retry,
                    // while the invalidated callback is fenced on retry.
                    tx.set(consent, mapOf("runSessionToken" to authority.getString("runSessionToken"),
                        "enabled" to authority.getBoolean("enabled"), "updatedAt" to FieldValue.serverTimestamp()))
                }
            }.await()
        }
        synchronized(lock) { if (active !== context) contexts.remove(context.session.token) }
    }

    /** Renew this finite SERVER query every15s, not a listener with a perpetually ageing expiry floor. */
    suspend fun discover(bounds: RouteMapBounds): RunnerDiscoveryResult {
        val bands = RunnerPresencePolicy.bands(bounds) ?: return RunnerDiscoveryResult(requiresZoom = true, checkedAtMillis = nowMillis())
        val db = firestore() ?: error("러너를 보려면 프로필을 먼저 연결해 주세요.")
        val uid = acceptedUid().takeIf { it.isNotBlank() } ?: error("프로필을 먼저 연결해 주세요.")
        val found = linkedMapOf<String, LiveRunner>()
        var partial = false
        withTimeout(20_000) {
            for (band in bands) {
                check(acceptedUid() == uid && firestore() === db) { "프로필 연결이 바뀌었어요. 지도를 다시 확인해 주세요." }
                val result = db.collection(PRESENCE).whereEqualTo("latBand", band.latitude)
                    .whereEqualTo("lonBand", band.longitude)
                    .whereGreaterThan("expiresAt", Timestamp(Date(nowMillis() + RunnerPresencePolicy.QUERY_FUTURE_MILLIS)))
                    .orderBy("expiresAt").limit(RunnerPresencePolicy.MAX_PER_BAND).get(Source.SERVER).await()
                partial = partial || result.size() >= RunnerPresencePolicy.MAX_PER_BAND
                for (document in result.documents) {
                    if (document.id == uid) continue
                    val runner = decode(document, nowMillis()) ?: continue
                    if (bounds.contains(runner.latitude, runner.longitude) ||
                        runner.longitude == -180.0 && bounds.contains(runner.latitude, 180.0)) found[runner.id] = runner
                }
            }
        }
        check(acceptedUid() == uid && firestore() === db) { "프로필 연결이 바뀌었어요." }
        return RunnerDiscoveryResult(found.values.sortedByDescending { it.updatedAtMillis }, partial, false, nowMillis())
    }

    private fun decode(document: DocumentSnapshot, now: Long): LiveRunner? {
        val data = document.data ?: return null
        if (data.keys != PRESENCE_KEYS || document.getString("visibility") != "approximate" || document.getBoolean("adultConfirmed") != true) return null
        val lat = document.getLong("latitudeE3")?.takeIf { it in -85_000..85_000 } ?: return null
        val lon = document.getLong("longitudeE3")?.takeIf { it in -180_000..179_999 } ?: return null
        val cell = RunnerPresenceCell(lat.toInt(), lon.toInt())
        if (document.getLong("latBand") != cell.band.latitude.toLong() || document.getLong("lonBand") != cell.band.longitude.toLong()) return null
        val updated = document.getTimestamp("updatedAt")?.toDate()?.time ?: return null
        val expires = document.getTimestamp("expiresAt")?.toDate()?.time ?: return null
        if (!RunnerPresencePolicy.fresh(updated, expires, now)) return null
        val name = document.getString("nickname")?.takeIf { it.length in 2..24 } ?: return null
        val avatar = decodeAvatar(data["avatar"] as? Map<*, *> ?: return null) ?: return null
        return LiveRunner(document.id, name, cell.latitude, cell.longitude, updated, avatar)
    }

    companion object {
        const val PRESENCE = "runnerPresence"
        const val CONSENTS = "runnerConsents"
        private val PRESENCE_KEYS = setOf("runSessionToken", "latitudeE3", "longitudeE3", "latBand", "lonBand", "nickname", "avatar", "visibility", "adultConfirmed", "updatedAt", "expiresAt")
        private val SLOT_KEYS = AvatarCatalogue.slots.map { it.key }.toSet()
        private fun publicAvatar(avatar: AvatarStyle): Map<String, Any> = buildMap {
            for (slot in AvatarCatalogue.slots) put(slot.key, avatar.variantFor(slot.key).also { require(AvatarCatalogue.valid(slot.key, it)) })
            put("gender", avatar.gender.wireValue)
            val colors = avatar.runningKitColors.normalized()
            put("colors", mapOf("top" to colors.top, "bottom" to colors.bottom, "shoes" to colors.shoes))
        }
        private fun decodeAvatar(data: Map<*, *>): AvatarStyle? {
            if (data.keys != SLOT_KEYS + setOf("gender", "colors")) return null
            val slots = SLOT_KEYS.associateWith { slot -> (data[slot] as? Long)?.takeIf { it in 0..5 && AvatarCatalogue.valid(slot, it.toInt()) }?.toInt() ?: return null }
            val gender = AvatarGender.entries.firstOrNull { it.wireValue == data["gender"] } ?: return null
            val colors = data["colors"] as? Map<*, *> ?: return null
            if (colors.keys != setOf("top", "bottom", "shoes")) return null
            fun color(key: String) = (colors[key] as? Long)?.takeIf { it in 0 until RunningKitColors.COLOR_COUNT }?.toInt()
            return AvatarStyle(hair = slots.getValue("hair"), top = slots.getValue("top"), bottom = slots.getValue("bottom"), shoes = slots.getValue("shoes"),
                face = slots.getValue("face"), socks = slots.getValue("socks"), headwear = slots.getValue("headwear"), ear = slots.getValue("ear"),
                eyewear = slots.getValue("eyewear"), wrist = slots.getValue("wrist"), waist = slots.getValue("waist"), hydration = slots.getValue("hydration"),
                costume = slots.getValue("costume"), gender = gender, runningKitColors = RunningKitColors(color("top") ?: return null, color("bottom") ?: return null, color("shoes") ?: return null))
        }
    }
}
