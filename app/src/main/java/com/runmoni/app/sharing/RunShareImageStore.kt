package com.runmoni.app.sharing

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** A rendered image is independent of the original photo, GPS file and stored run. */
internal class RunShareImageStore(
    context: Context,
    // Tests select a unique child under this owned cache root, never existing exports.
    private val cacheDirectory: File = File(context.cacheDir, "exports/run-shares"),
    private val beforeGalleryPublish: suspend (Uri) -> Unit = {},
    private val beforeCacheHandoff: suspend (Uri) -> Unit = {},
) {
    private val app = context.applicationContext

    /** The caller owns starting a chooser, its lifecycle fence and the user-visible result. */
    suspend fun cache(bitmap: Bitmap): Uri {
        var created: File? = null
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                validate(bitmap)
                currentCoroutineContext().ensureActive()
                val root = File(app.cacheDir, "exports/run-shares").canonicalFile
                val folder = cacheDirectory.canonicalFile
                require(folder == root || folder.path.startsWith(root.path + File.separator))
                check(folder.isDirectory || folder.mkdirs()) { "공유 이미지를 준비하지 못했어요." }
                val now = System.currentTimeMillis()
                // Only our own expired PNGs are removed. A fresh file remains available after
                // the chooser/editor closes so a recipient can finish reading its one URI.
                folder.listFiles()?.filter { file ->
                    file.isFile && file.name.matches(CACHE_NAME) &&
                        file.canonicalFile.parentFile == folder && file.lastModified() > 0 &&
                        now - file.lastModified() > CACHE_TTL_MILLIS
                }?.forEach { it.delete() }
                val file = File(folder, "runmoni-card-${UUID.randomUUID()}.png")
                check(file.createNewFile()) { "공유 이미지를 준비하지 못했어요." }
                created = file
                file.outputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "이미지를 만들지 못했어요." }
                    output.flush()
                }
                currentCoroutineContext().ensureActive()
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                beforeCacheHandoff(uri)
                currentCoroutineContext().ensureActive()
                uri
            }
            currentCoroutineContext().ensureActive()
            delivered = true
            return result
        } finally {
            // This also covers cancellation while withContext dispatches a completed URI back.
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) { created?.delete() }
        }
    }

    /** Scoped Pictures/Runmoni PNG; no storage permission or original metadata is requested. */
    suspend fun save(bitmap: Bitmap, isCurrent: () -> Boolean = { true }): Uri {
        var owned: Uri? = null
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                validate(bitmap)
                currentCoroutineContext().ensureActive()
                check(isCurrent()) { "러닝 기록이 바뀌었어요. 공유 화면을 다시 열어 주세요." }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Runmoni-${UUID.randomUUID()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Runmoni")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("사진 앱에 저장할 공간을 준비하지 못했어요.")
                // Own the URI before any suspension or write, including test/provider failure.
                owned = uri
                app.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "이미지를 만들지 못했어요." }
                    output.flush()
                } ?: throw IOException("이미지 저장 파일을 열지 못했어요.")
                currentCoroutineContext().ensureActive()
                beforeGalleryPublish(uri)
                currentCoroutineContext().ensureActive()
                check(isCurrent()) { "러닝 기록이 바뀌었어요. 공유 화면을 다시 열어 주세요." }
                val published = app.contentResolver.update(uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                check(published == 1) { "이미지 저장을 완료하지 못했어요." }
                currentCoroutineContext().ensureActive()
                check(isCurrent()) { "러닝 기록이 바뀌었어요. 공유 화면을 다시 열어 주세요." }
                uri
            }
            currentCoroutineContext().ensureActive()
            check(isCurrent()) { "러닝 기록이 바뀌었어요. 공유 화면을 다시 열어 주세요." }
            delivered = true
            return result
        } finally {
            // Keep cancellation active during rendering/writing. Only cleanup is non-cancellable;
            // a result lost on dispatch back cannot leave a pending or unacknowledged owned row.
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                owned?.let { app.contentResolver.delete(it, null, null) }
            }
        }
    }

    fun shareIntent(uri: Uri, text: String): Intent {
        val parts = uri.pathSegments
        require(uri.scheme == "content" && uri.authority == "${app.packageName}.files" &&
            uri.query == null && uri.fragment == null && parts.size >= 3 &&
            parts[0] == "exports" && parts[1] == "run-shares" && parts.last().matches(CACHE_NAME) &&
            parts.drop(2).dropLast(1).all { it.matches(Regex("[A-Za-z0-9_-]+")) }) {
            "공유 이미지를 다시 만들어 주세요."
        }
        require(text.length <= 2_000) { "공유 문구가 너무 길어요." }
        return Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("Runmoni 기록 이미지", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun validate(bitmap: Bitmap) {
        require(!bitmap.isRecycled && bitmap.width in 1..MAX_DIMENSION && bitmap.height in 1..MAX_DIMENSION &&
            bitmap.width.toLong() * bitmap.height <= MAX_PIXELS) { "공유 이미지를 다시 만들어 주세요." }
    }

    companion object {
        private val CACHE_NAME = Regex("runmoni-card-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")
        private const val CACHE_TTL_MILLIS = 7 * 24 * 60 * 60_000L
        private const val MAX_DIMENSION = 4_096
        private const val MAX_PIXELS = 8_388_608L
    }
}
