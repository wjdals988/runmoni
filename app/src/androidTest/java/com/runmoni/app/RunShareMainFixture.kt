package com.runmoni.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.system.Os
import android.util.AtomicFile
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.data.StoredRun
import com.runmoni.app.domain.RunPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Root-invoked support fixture, not a product-test pass or a real runner/photo.
 * Run prepare/cleanup separately with the SAME mandatory runmoniShareNonce.
 * The Root owns whole-XML/profile/credit-ledger/DB-fingerprint preservation.
 * Neither test directly calls credit, writes preferences/Auth, starts a run, or
 * sends a production request. Ordinary repository startup can replay this zero-point ID
 * into the local credit ledger; the Root must restore its original whole XML.
 */
@RunWith(AndroidJUnit4::class)
class RunShareMainFixture {
    private data class Owned(val nonce: String, val id: String, val displayName: String, val marker: File)

    private fun owned(app: RunmoniApplication): Owned {
        check(BuildConfig.DEBUG && !BuildConfig.FIREBASE_ENABLED) { "Only the offline TEST debug fixture is allowed" }
        val nonce = InstrumentationRegistry.getArguments().getString("runmoniShareNonce")
            ?: error("An explicit fixture nonce is required")
        check(Regex("[a-z0-9]{8,32}").matches(nonce)) { "Invalid explicit fixture nonce" }
        val folder = File(app.cacheDir, "run-share-review")
        check(folder.isDirectory || folder.mkdirs()) { "Cannot prepare the owned review cache" }
        return Owned(nonce, "runmoni-share-review-$nonce", "RunmoniReview-$nonce.png",
            File(folder, "main-fixture-$nonce.json"))
    }

    private suspend fun idle(app: RunmoniApplication) {
        withTimeout(30_000) { app.runs.persistence.first { it.initialized && !it.saving } }
        check(app.runs.active.value.phase == RunPhase.IDLE && !app.runs.persistence.value.creditPending &&
            !app.runs.persistence.value.finishing && !app.runs.persistence.value.historyUnavailable) {
            "An idle initialized device with no pending credit/recovery is required"
        }
        check(app.runs.dao.unfinished() == null) { "Never take over an unfinished run" }
    }

    @Test fun prepareOwnedFixture() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        val owner = owned(app)
        idle(app)
        var reservedByThisCall = false
        try { withContext(Dispatchers.IO) {
            check(!owner.marker.exists() && !File(owner.marker.path + ".bak").exists() &&
                !File(owner.marker.path + ".new").exists()) { "A fixture marker already exists; inspect before creating another" }
            check(app.runs.dao.get(owner.id) == null && app.runs.dao.points(owner.id).isEmpty()) {
                "The owned fixture record name is already occupied"
            }
            check(photoNames(app, owner.displayName).isEmpty()) { "The owned fixture photo name is already occupied" }
            val now = System.currentTimeMillis()
            val finished = now - 60_000L
            val row = StoredRun(owner.id, startedAtMillis = finished - 1_900_000L,
                finishedAtMillis = finished, distanceMeters = 5240.0, activeDurationMillis = 1_800_000L,
                elapsedDurationMillis = 1_900_000L, synced = false, mileage = 0, splitsJson = SPLITS)
            val zone = ZoneId.systemDefault()
            check(Instant.ofEpochMilli(row.startedAtMillis).atZone(zone).toLocalDate() ==
                Instant.ofEpochMilli(now).atZone(zone).toLocalDate()) {
                "This current-day fixture requires thirty-three minutes after local midnight"
            }
            val marker = JSONObject().put("schemaVersion", 1).put("nonce", owner.nonce)
                .put("recordId", owner.id).put("createdAtMillis", now).put("run", encode(row))
                .put("photoUri", "").put("photoSha256", "").put("photoDisplayName", owner.displayName)
                .put("photoRelativePath", PHOTO_PATH).put("photoMime", "image/png").put("stage", "reserved")
                .put("sourceKind", SOURCE).put("rawGpsIncluded", false).put("fixtureDirectWalletWrites", false)
            check(owner.marker.createNewFile()) { "Cannot reserve the owned fixture marker" }
            reservedByThisCall = true
            var runWritten = false
            var photoWritten: Uri? = null
            try {
                write(owner.marker, marker)
                app.runs.database.withTransaction {
                    check(app.runs.dao.get(owner.id) == null) { "The owned record changed before insertion" }
                    app.runs.dao.save(row)
                    runWritten = true
                }
                marker.put("stage", "run-created"); write(owner.marker, marker)
                val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, owner.displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                    put(MediaStore.Images.Media.WIDTH, 1200)
                    put(MediaStore.Images.Media.HEIGHT, 1600)
                }) ?: error("Cannot create the owned synthetic photo")
                photoWritten = uri
                marker.put("photoUri", uri.toString()).put("stage", "photo-created"); write(owner.marker, marker)
                val bitmap = syntheticPhoto()
                try {
                    app.contentResolver.openOutputStream(uri, "w")?.use {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Cannot encode the owned synthetic photo" }
                    } ?: error("Cannot write the owned synthetic photo")
                } finally { bitmap.recycle() }
                val digest = digest(app, uri)
                marker.put("photoSha256", digest).put("stage", "photo-written"); write(owner.marker, marker)
                check(app.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }, null, null) == 1) { "Cannot publish the owned synthetic photo" }
                requireOwnPhoto(app, owner, uri, digest)
                check(app.runs.dao.get(owner.id) == row && app.runs.dao.points(owner.id).isEmpty()) {
                    "Prepared fixture differs from its complete expected row"
                }
                marker.put("stage", "ready"); write(owner.marker, marker)
            } catch (failure: Throwable) {
                // Only resources created in this invocation may be rolled back.
                // If another actor changes a row/URI, retain it and its marker for inspection.
                val cleaned = withContext(NonCancellable + Dispatchers.IO) {
                    val media = runCatching {
                        photoWritten?.let { uri ->
                            requireOwnPhoto(app, owner, uri, null)
                            check(app.contentResolver.delete(uri, null, null) == 1)
                        }
                    }.isSuccess
                    val run = runCatching {
                        if (runWritten) app.runs.database.withTransaction {
                            val current = app.runs.dao.get(owner.id)
                            check(current == null || current == row) { "Changed record preserved during rollback" }
                            if (current != null) {
                                check(app.runs.dao.points(owner.id).isEmpty()) { "Unexpected points preserved during rollback" }
                                app.runs.dao.delete(owner.id)
                            }
                        }
                    }.isSuccess
                    media && run
                }
                if (cleaned) AtomicFile(owner.marker).delete()
                else marker.put("stage", "rollback-incomplete").also { write(owner.marker, it) }
                throw failure
            }
        } } catch (failure: Throwable) {
            // Also cover cancellation while withContext returns a completed ready marker.
            // A pre-existing/unknown marker was never reserved here and is not touched.
            if (reservedByThisCall) runCatching {
                withContext(NonCancellable + Dispatchers.IO) { cleanup(app, owner) }
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    @Test fun cleanupOwnedFixture() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<RunmoniApplication>()
        val owner = owned(app)
        idle(app)
        withContext(NonCancellable + Dispatchers.IO) { cleanup(app, owner) }
    }

    private suspend fun cleanup(app: RunmoniApplication, owner: Owned) {
            // Unknown/partial marker state is deliberately left for Root inspection.
            check(!File(owner.marker.path + ".bak").exists() && !File(owner.marker.path + ".new").exists()) {
                "Incomplete atomic marker preserved for inspection"
            }
            if (!owner.marker.exists()) {
                check(app.runs.dao.get(owner.id) == null && photoNames(app, owner.displayName).isEmpty()) {
                    "Fixture resources exist without their ownership marker; preserved"
                }
                return
            }
            check(owner.marker.length() in 1..65_536) { "Unknown marker size; preserved" }
            val marker = JSONObject(owner.marker.readText(Charsets.UTF_8))
            check(marker.keys().asSequence().toSet() == MARKER_KEYS && marker.getInt("schemaVersion") == 1 &&
                marker.getString("nonce") == owner.nonce && marker.getString("recordId") == owner.id &&
                marker.getString("photoDisplayName") == owner.displayName && marker.getString("photoRelativePath") == PHOTO_PATH &&
                marker.getString("photoMime") == "image/png" && marker.getString("sourceKind") == SOURCE &&
                !marker.getBoolean("rawGpsIncluded") && !marker.getBoolean("fixtureDirectWalletWrites") &&
                marker.getString("stage") in STAGES) { "Unknown ownership marker; preserved" }
            val expected = decode(marker.getJSONObject("run"))
            check(expected.id == owner.id && expected.distanceMeters == 5240.0 && expected.activeDurationMillis == 1_800_000L &&
                expected.elapsedDurationMillis == 1_900_000L && expected.finishedAtMillis > 0 &&
                expected.startedAtMillis == expected.finishedAtMillis - 1_900_000L && !expected.synced &&
                !expected.interrupted && expected.segment == 0 && expected.mileage == 0 && expected.splitsJson == SPLITS) {
                "Unknown fixture row contract; preserved"
            }
            val rawUri = marker.getString("photoUri")
            val photoUri = rawUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            val hash = marker.getString("photoSha256").takeIf { it.isNotBlank() }
            check(hash == null || Regex("[0-9a-f]{64}").matches(hash)) { "Unknown fixture image hash; preserved" }
            val current = app.runs.dao.get(owner.id)
            check(current == null || current == expected) { "Changed fixture record preserved" }
            check(app.runs.dao.points(owner.id).isEmpty()) { "Unexpected track points preserved" }
            if (photoUri != null && photoExists(app, photoUri)) requireOwnPhoto(app, owner, photoUri, hash)
            // Validate both resources before mutating either. Re-check under the DB transaction.
            app.runs.database.withTransaction {
                val exact = app.runs.dao.get(owner.id)
                check(exact == null || exact == expected) { "Record changed during cleanup; preserved" }
                check(app.runs.dao.points(owner.id).isEmpty()) { "Points changed during cleanup; preserved" }
                if (exact != null) app.runs.dao.delete(owner.id)
            }
            if (photoUri != null && photoExists(app, photoUri)) {
                requireOwnPhoto(app, owner, photoUri, hash)
                check(app.contentResolver.delete(photoUri, null, null) == 1) { "Cannot remove owned review photo" }
            }
            check(app.runs.dao.get(owner.id) == null && app.runs.dao.points(owner.id).isEmpty()) { "Owned row cleanup incomplete" }
            check(photoNames(app, owner.displayName).isEmpty()) { "Owned media cleanup incomplete" }
            AtomicFile(owner.marker).delete()
            check(!owner.marker.exists()) { "Owned marker cleanup incomplete" }
    }

    private fun write(file: File, value: JSONObject) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write((value.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            atomic.finishWrite(stream)
            Os.chmod(file.absolutePath, 0x180) //0600, owned app cache only.
        } catch (failure: Throwable) { atomic.failWrite(stream); throw failure }
    }

    private fun photoNames(app: RunmoniApplication, name: String): List<Uri> = buildList {
        app.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { cursor ->
            while (cursor.moveToNext()) add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString()))
        } ?: error("Cannot inspect owned review media names")
    }

    private fun photoExists(app: RunmoniApplication, uri: Uri): Boolean {
        requireMediaUri(uri)
        return app.contentResolver.query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { it.moveToFirst() }
            ?: error("Cannot inspect owned review media URI")
    }

    private fun requireOwnPhoto(app: RunmoniApplication, owner: Owned, uri: Uri, hash: String?) {
        requireMediaUri(uri)
        app.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.OWNER_PACKAGE_NAME),
            null, null, null)?.use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == owner.displayName && cursor.getString(1) == PHOTO_PATH &&
                cursor.getString(2) == "image/png" && cursor.getString(3) == app.packageName) {
                "Media ownership/identity changed; preserved"
            }
        } ?: error("Cannot inspect owned review media")
        if (hash != null) check(digest(app, uri) == hash) { "Changed synthetic photo preserved" }
    }

    private fun requireMediaUri(uri: Uri) {
        check(uri.scheme == "content" && uri.authority == "media" && uri.query == null && uri.fragment == null &&
            uri.pathSegments.size == 4 && uri.pathSegments[0] in setOf("external", "external_primary") && uri.pathSegments[1] == "images" &&
            uri.pathSegments[2] == "media" && uri.pathSegments[3].all(Char::isDigit)) { "Unknown photo URI; preserved" }
    }

    private fun digest(app: RunmoniApplication, uri: Uri): String {
        val sha = MessageDigest.getInstance("SHA-256")
        app.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16_384)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count; check(total <= 16_777_216L) { "Unexpected review image size; preserved" }
                sha.update(buffer, 0, count)
            }
        } ?: error("Cannot read owned review image")
        return sha.digest().joinToString("") { "%02x".format(it) }
    }

    private fun syntheticPhoto(): Bitmap = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 1200f, 1600f, intArrayOf(Color.rgb(240, 197, 143),
            Color.rgb(126, 173, 163), Color.rgb(42, 91, 76)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1200f, 1600f, paint)
        paint.shader = null; paint.color = Color.rgb(248, 223, 175)
        canvas.drawCircle(900f, 330f, 150f, paint)
        paint.color = Color.rgb(55, 111, 88)
        canvas.drawPath(Path().apply { moveTo(0f, 970f); cubicTo(330f, 650f, 600f, 970f, 1200f, 710f)
            lineTo(1200f, 1600f); lineTo(0f, 1600f); close() }, paint)
        paint.color = Color.rgb(204, 196, 175)
        canvas.drawPath(Path().apply { moveTo(620f, 890f); cubicTo(520f, 1180f, 900f, 1240f, 720f, 1600f)
            lineTo(270f, 1600f); cubicTo(680f, 1260f, 350f, 1150f, 620f, 890f); close() }, paint)
        paint.color = Color.rgb(25, 45, 37); paint.textSize = 54f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText("RUNMONI REVIEW", 72f, 145f, paint)
        paint.textSize = 30f; paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("SYNTHETIC PHOTO", 74f, 198f, paint)
    }

    private fun encode(row: StoredRun) = JSONObject().put("id", row.id).put("startedAtMillis", row.startedAtMillis)
        .put("finishedAtMillis", row.finishedAtMillis).put("distanceMeters", row.distanceMeters)
        .put("activeDurationMillis", row.activeDurationMillis).put("elapsedDurationMillis", row.elapsedDurationMillis)
        .put("segment", row.segment).put("synced", row.synced).put("interrupted", row.interrupted)
        .put("mileage", row.mileage).put("splitsJson", row.splitsJson)

    private fun decode(value: JSONObject): StoredRun {
        check(value.keys().asSequence().toSet() == RUN_KEYS) { "Unknown row marker schema; preserved" }
        return StoredRun(value.getString("id"), value.getLong("startedAtMillis"), value.getLong("finishedAtMillis"),
            value.getDouble("distanceMeters"), value.getLong("activeDurationMillis"), value.getLong("elapsedDurationMillis"),
            value.getInt("segment"), value.getBoolean("synced"), value.getBoolean("interrupted"),
            value.getInt("mileage"), value.getString("splitsJson"))
    }

    companion object {
        private const val SOURCE = "synthetic completed run and Canvas photo for actual MainActivity review"
        private val PHOTO_PATH = "${Environment.DIRECTORY_PICTURES}/RunmoniReview/"
        private val SPLITS = JSONArray().apply {
            listOf(340_000L, 343_000L, 345_000L, 342_000L, 344_000L).forEachIndexed { index, duration ->
                put(JSONObject().put("km", index + 1).put("duration", duration))
            }
        }.toString()
        private val RUN_KEYS = setOf("id", "startedAtMillis", "finishedAtMillis", "distanceMeters", "activeDurationMillis",
            "elapsedDurationMillis", "segment", "synced", "interrupted", "mileage", "splitsJson")
        private val MARKER_KEYS = setOf("schemaVersion", "nonce", "recordId", "createdAtMillis", "run", "photoUri",
            "photoSha256", "photoDisplayName", "photoRelativePath", "photoMime", "stage", "sourceKind",
            "rawGpsIncluded", "fixtureDirectWalletWrites")
        private val STAGES = setOf("reserved", "run-created", "photo-created", "photo-written", "ready", "rollback-incomplete")
    }
}
