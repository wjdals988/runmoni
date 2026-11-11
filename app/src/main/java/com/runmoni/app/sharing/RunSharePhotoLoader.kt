package com.runmoni.app.sharing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Only the explicitly picked content is decoded. No URI, EXIF or source name is persisted in a record. */
internal class RunSharePhotoLoader(context: Context) {
    private val app = context.applicationContext

    suspend fun load(uri: Uri): Bitmap {
        var decoded: Bitmap? = null
        try {
            return withContext(Dispatchers.IO) {
                // A cancelled platform/provider read may finish late. Keep one decode/staging operation
                // in this process, including across editor recreation, and check cancellation on return.
                decodeGate.withLock {
                    currentCoroutineContext().ensureActive()
                    require(uri.scheme == "content") { "사진을 다시 선택해 주세요." }
                    val mime = app.contentResolver.getType(uri)
                    require(mime == null || mime.startsWith("image/")) { "이미지 파일을 선택해 주세요." }
                    val folder = File(app.cacheDir, "run-share-input")
                    check(folder.isDirectory || folder.mkdirs()) { "사진을 준비하지 못했어요." }
                    val now = System.currentTimeMillis()
                    folder.listFiles()?.filter { file ->
                        file.name.startsWith("runmoni-photo-") && file.name.endsWith(".tmp") &&
                            file.isFile && file.lastModified() > 0 && now - file.lastModified() > STAGING_TTL_MILLIS
                    }?.forEach { it.delete() }
                    val staged = File.createTempFile("runmoni-photo-", ".tmp", folder)
                    try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            staged.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                var emptyReads = 0
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) {
                                        check(++emptyReads <= 8) { "사진을 불러오지 못했어요." }
                                        continue
                                    }
                                    emptyReads = 0
                                    total += count
                                    require(total <= MAX_SOURCE_BYTES) { "사진 크기를 줄이거나 다른 사진을 선택해 주세요." }
                                    output.write(buffer, 0, count)
                                }
                                require(total > 0) { "사진을 불러오지 못했어요." }
                            }
                        } ?: throw IOException("사진을 열지 못했어요.")
                        currentCoroutineContext().ensureActive()
                        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(staged)) { decoder, info, _ ->
                            val width = info.size.width
                            val height = info.size.height
                            require(info.mimeType.startsWith("image/") &&
                                width in 1..MAX_SOURCE_DIMENSION && height in 1..MAX_SOURCE_DIMENSION &&
                                width.toLong() * height <= MAX_SOURCE_PIXELS) { "사진 크기를 줄이거나 다른 사진을 선택해 주세요." }
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                            val longest = max(width, height)
                            if (longest > MAX_DECODE_DIMENSION) {
                                val scale = MAX_DECODE_DIMENSION.toDouble() / longest
                                decoder.setTargetSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
                            }
                            decoder.setOnPartialImageListener { false }
                        }
                        decoded = bitmap
                        currentCoroutineContext().ensureActive()
                        check(bitmap.width in 1..MAX_DECODE_DIMENSION && bitmap.height in 1..MAX_DECODE_DIMENSION) {
                            "사진 크기를 줄이거나 다른 사진을 선택해 주세요."
                        }
                        bitmap
                    } finally {
                        // This staging file contains original metadata, stays outside FileProvider's
                        // exports root, and is removed after success, cancellation or decoding failure.
                        staged.delete()
                    }
                }
            }
        } catch (failure: Exception) {
            // withContext can discard a result during dispatch back to a cancelled editor.
            // Only this newly decoded photo is owned here; shared avatar bitmaps are never recycled.
            decoded?.takeUnless { it.isRecycled }?.recycle()
            if (failure is CancellationException) throw failure
            throw IOException("사진을 불러오지 못했어요. 다른 사진을 선택해 주세요.", failure)
        } catch (failure: OutOfMemoryError) {
            decoded?.takeUnless { it.isRecycled }?.recycle()
            throw IOException("사진 크기를 줄이거나 다른 사진을 선택해 주세요.", failure)
        }
    }

    companion object {
        private val decodeGate = Mutex()
        private const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
        private const val MAX_SOURCE_DIMENSION = 32_768
        private const val MAX_SOURCE_PIXELS = 268_435_456L
        internal const val MAX_DECODE_DIMENSION = 2_048
        private const val STAGING_TTL_MILLIS = 24 * 60 * 60_000L
    }
}
