package com.runmoni.app.sharing

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Synthetic inputs only. Every file/MediaStore row this class creates is individually owned and removed. */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class RunShareImageStoreTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun folder(label: String) = File(app.cacheDir, "exports/run-shares/$label-${UUID.randomUUID()}").apply {
        check(mkdirs())
    }

    private fun bitmap() = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(28, 86, 65)) }

    private fun bytes(uri: Uri) = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private fun imageExists(uri: Uri): Boolean = app.contentResolver.query(uri,
        arrayOf(MediaStore.Images.Media._ID), null, null, null)?.use { it.moveToFirst() } == true

    // An API29 delete of an already removed image can throw SecurityException.
    // These are only exact URIs returned to the test's own insert hooks/save;
    // never query a gallery collection or infer ownership from a filename.
    private fun deleteOwnedRows(uris: Iterable<Uri>) {
        var firstFailure: Throwable? = null
        uris.distinct().forEach { uri ->
            try {
                if (imageExists(uri)) assertEquals("Remove the existing owned fixture row", 1,
                    app.contentResolver.delete(uri, null, null))
                assertFalse("The owned fixture row is absent after cleanup", imageExists(uri))
            } catch (failure: Throwable) {
                // One genuine cleanup failure must not prevent cleanup of the
                // other exact fixture URIs, and it must still fail the test.
                if (firstFailure == null) firstFailure = failure else firstFailure!!.addSuppressed(failure)
            }
        }
        firstFailure?.let { throw it }
    }

    private fun pending(uri: Uri): Int = app.contentResolver.query(uri,
        arrayOf(MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
        check(it.moveToFirst()); it.getInt(0)
    }

    private fun pngChunks(raw: ByteArray): List<String> {
        assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), raw.copyOfRange(0, 8))
        val kinds = mutableListOf<String>()
        var at = 8
        while (at < raw.size) {
            assertTrue(at + 12 <= raw.size)
            val length = ByteBuffer.wrap(raw, at, 4).int
            assertTrue(length >= 0 && at.toLong() + length + 12 <= raw.size)
            val kind = raw.copyOfRange(at + 4, at + 8).toString(Charsets.US_ASCII)
            kinds += kind
            at += length + 12
        }
        assertEquals(raw.size, at)
        assertEquals("IHDR", kinds.first())
        assertEquals("IEND", kinds.last())
        return kinds
    }

    @Test fun pickedPhotoIsOrientedBoundedAndSharedAsNewPngWithoutOriginalExif() = runBlocking {
        val owned = folder("photo-fixture")
        val source = Bitmap.createBitmap(2_050, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        var loaded: Bitmap? = null
        try {
            val jpeg = File(owned, "synthetic-camera.jpg")
            jpeg.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 92, it)) }
            ExifInterface(jpeg.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "127/1,0/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E")
                saveAttributes()
            }
            val original = jpeg.readBytes()
            val sourceUri = FileProvider.getUriForFile(app, "${app.packageName}.files", jpeg)
            loaded = RunSharePhotoLoader(app).load(sourceUri)
            assertTrue("Camera EXIF rotation is applied", loaded!!.height > loaded!!.width)
            assertEquals(2_048, loaded!!.height)
            assertTrue(loaded!!.width in 1..2_048)
            assertTrue(loaded!!.config != Bitmap.Config.HARDWARE)
            assertArrayEquals("Reading never edits the chosen original", original, jpeg.readBytes())
            val store = RunShareImageStore(app, cacheDirectory = owned)
            val uri = store.cache(loaded!!)
            val raw = bytes(uri)
            val chunks = pngChunks(raw)
            assertFalse(chunks.any { it in setOf("eXIf", "tEXt", "iTXt", "zTXt") })
            val output = BitmapFactory.decodeByteArray(raw, 0, raw.size)!!
            try {
                assertEquals(loaded!!.width, output.width)
                assertEquals(loaded!!.height, output.height)
                assertEquals(loaded!!.getPixel(0, 0), output.getPixel(0, 0))
            } finally { output.recycle() }
            assertEquals("image/png", app.contentResolver.getType(uri))
            assertFalse(uri.lastPathSegment!!.contains("synthetic-camera"))
            val intent = store.shareIntent(uri, "합성 러닝 기록")
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("image/png", intent.type)
            assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals(1, intent.clipData!!.itemCount)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,
                intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
            assertNull(intent.`package`)
            assertTrue(runCatching { store.shareIntent(sourceUri, "잘못된 원본") }.isFailure)
            assertTrue(runCatching { RunSharePhotoLoader(app).load(Uri.parse("https://example.invalid/photo.jpg")) }.isFailure)
            val textFile = File(owned, "not-a-photo.txt").apply { writeText("synthetic text") }
            val textUri = FileProvider.getUriForFile(app, "${app.packageName}.files", textFile)
            assertTrue(runCatching { RunSharePhotoLoader(app).load(textUri) }.isFailure)
            assertFalse("The store borrows its input", loaded!!.isRecycled)
        } finally {
            loaded?.recycle(); source.recycle(); owned.deleteRecursively()
        }
    }

    @Test fun cancellingPendingGalleryWriteRemovesOnlyItsOwnRow() = runBlocking {
        val input = bitmap()
        val inserted = CompletableDeferred<Uri>()
        var owned: Uri? = null
        val store = RunShareImageStore(app, beforeGalleryPublish = { uri ->
            owned = uri
            inserted.complete(uri)
            awaitCancellation()
        })
        val request = launch { store.save(input) }
        try {
            val uri = withTimeout(30_000) { inserted.await() }
            assertEquals(1, withContext(Dispatchers.IO) { pending(uri) })
            request.cancelAndJoin()
            assertFalse(withContext(Dispatchers.IO) { imageExists(uri) })
            assertFalse(input.isRecycled)
        } finally {
            try {
                withContext(NonCancellable) {
                    request.cancelAndJoin()
                    withContext(Dispatchers.IO) { deleteOwnedRows(listOfNotNull(owned)) }
                }
            } finally { input.recycle() }
        }
    }

    @Test fun failedGalleryPublishIsCleanedAndSuccessfulSaveIsAStandaloneOwnedPng() = runBlocking {
        val input = bitmap()
        val owned = mutableListOf<Uri>()
        try {
            val failedStore = RunShareImageStore(app, beforeGalleryPublish = { uri ->
                owned += uri
                throw IOException("Synthetic publish failure")
            })
            assertTrue(runCatching { failedStore.save(input) }.isFailure)
            assertEquals(1, owned.size)
            assertFalse(withContext(Dispatchers.IO) { imageExists(owned.single()) })
            val current = AtomicBoolean(true)
            val staleStore = RunShareImageStore(app, beforeGalleryPublish = { uri ->
                owned += uri
                // The actual pending PNG has been written, then the canonical
                // record changes before visibility is granted to the photo app.
                assertEquals(1, pending(uri))
                current.set(false)
            })
            assertTrue(runCatching { staleStore.save(input, isCurrent = current::get) }.isFailure)
            assertEquals(2, owned.size)
            assertFalse(withContext(Dispatchers.IO) { imageExists(owned.last()) })
            val uri = RunShareImageStore(app).save(input).also { owned += it }
            withContext(Dispatchers.IO) {
                app.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.IS_PENDING), null, null, null)!!.use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.getString(0).matches(Regex("Runmoni-[0-9a-f-]{36}\\.png")))
                    assertEquals("image/png", it.getString(1))
                    assertEquals("${Environment.DIRECTORY_PICTURES}/Runmoni", it.getString(2).trimEnd('/'))
                    assertEquals(0, it.getInt(3))
                }
                val raw = bytes(uri)
                assertFalse(pngChunks(raw).any { it == "eXIf" })
                val image = BitmapFactory.decodeByteArray(raw, 0, raw.size)!!
                try {
                    assertEquals(input.width, image.width)
                    assertEquals(input.height, image.height)
                    assertEquals(input.getPixel(0, 0), image.getPixel(0, 0))
                } finally { image.recycle() }
            }
            assertFalse(input.isRecycled)
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) { deleteOwnedRows(owned) }
            } finally { input.recycle() }
        }
    }

    @Test fun cancelledCacheHandoffIsRemovedWhileFreshSharedFilesAndUnrelatedNamesSurviveExpiry() = runBlocking {
        val owned = folder("cache-fixture")
        val input = bitmap()
        var request: kotlinx.coroutines.Deferred<Uri>? = null
        try {
            fun cachedFile() = File(owned, "runmoni-card-${UUID.randomUUID()}.png").apply {
                outputStream().use { assertTrue(input.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            val old = cachedFile().apply { assertTrue(setLastModified(System.currentTimeMillis() - 8 * 24 * 60 * 60_000L)) }
            val fresh = cachedFile()
            val unrelated = File(owned, "keep-unrelated.txt").apply { writeText("synthetic fixture") }
            val store = RunShareImageStore(app, cacheDirectory = owned)
            val handedOff = store.cache(input)
            assertFalse(old.exists())
            assertTrue(fresh.exists())
            assertTrue(unrelated.exists())
            val before = bytes(handedOff)
            val pending = CompletableDeferred<Uri>()
            val cancelledStore = RunShareImageStore(app, cacheDirectory = owned,
                beforeCacheHandoff = { uri -> pending.complete(uri); awaitCancellation() })
            request = async { cancelledStore.cache(input) }
            val notHandedOff = withTimeout(30_000) { pending.await() }
            request!!.cancelAndJoin()
            assertTrue(runCatching { bytes(notHandedOff) }.isFailure)
            assertArrayEquals(before, bytes(handedOff))
            assertTrue(fresh.exists())
            assertTrue(unrelated.exists())
            assertFalse(input.isRecycled)
        } finally {
            request?.cancelAndJoin()
            input.recycle(); owned.deleteRecursively()
        }
    }
}
