package com.runmoni.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AvatarStyle
import com.runmoni.app.domain.RunRecord
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/**
 * Real editor controller/approved renderer/MediaStore, in a ComponentActivity with a synthetic
 * completed record. No AppViewModel, record DB, wallet or authentication is changed. A blocking
 * instrumentation monitor only detects forbidden chooser attempts; this is not an actual system
 * chooser selection or successful delivery to another app. Root runs this class serially.
 */
class RunShareControllerTest : RunmoniDesignTestBase() {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private data class OwnedImage(val uri: Uri, val pending: Int, val mime: String)

    private fun ownedImages(): Map<Long, OwnedImage> {
        val result = linkedMapOf<Long, OwnedImage>()
        app.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.IS_PENDING,
                MediaStore.Images.Media.MIME_TYPE),
            "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf(app.packageName, "Runmoni-%.png"), null)!!.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val relative = cursor.getString(2)?.trimEnd('/')
                if (name.matches(GALLERY_NAME) && relative == "${Environment.DIRECTORY_PICTURES}/Runmoni") {
                    val id = cursor.getLong(0)
                    result[id] = OwnedImage(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                        cursor.getInt(3), cursor.getString(4))
                }
            }
        }
        return result
    }

    private fun ownedCacheFiles(): Set<String> {
        val root = File(app.cacheDir, "exports/run-shares").canonicalFile
        return root.listFiles()?.filter {
            it.isFile && it.name.matches(CACHE_NAME) && it.canonicalFile.parentFile == root
        }?.map { it.name }?.toSet().orEmpty()
    }

    @Test fun actualControllerSavesOnceAndRevokesUnrecomposedAndDisposedExportCallbacks() {
        val before = ownedImages()
        val beforeCache = ownedCacheFiles()
        val now = System.currentTimeMillis()
        val record = RunRecord("controller-only-synthetic-run", now - 1_200_000L, now,
            distanceMeters = 5230.0, activeDurationMillis = 1_200_000L)
        val allowed = AtomicBoolean(true)
        val visible = mutableStateOf(true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val chooserMonitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, Intent()), true)
        try {
            compose.setContent {
                if (visible.value) RunmoniTheme {
                    RunShareScreen(record, AvatarStyle(top = 1, bottom = 1, shoes = 2), "ControllerFixture",
                        recordAvailable = true, authorize = { allowed.get() }, onBack = { visible.value = false })
                }
            }
            compose.waitUntil(120_000) {
                compose.onAllNodes(hasTestTag("save_run_share") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitUntil(30_000) {
                compose.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && compose.activity.hasWindowFocus()
            }
            val oldSave = compose.onNodeWithTag("save_run_share").fetchSemanticsNode().config[RecordActionSnapshot]
            val oldShare = compose.onNodeWithTag("send_run_share").fetchSemanticsNode().config[RecordActionSnapshot]
            compose.runOnIdle {
                // AtomicBoolean does not trigger recomposition: the controller must consult the
                // current authorization rather than a previously enabled semantics node.
                allowed.set(false)
                oldSave.invoke()
                oldShare.invoke()
            }
            compose.waitForIdle()
            assertEquals(before.keys, ownedImages().keys)
            assertEquals(0, chooserMonitor.hits)

            val paperChoice = requireNotNull(compose.onNodeWithTag("run_share_theme_2").fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action)
            compose.runOnIdle {
                allowed.set(true)
                // These nodes are still mounted in this UI turn. Change the actual draft,
                // then replay the older stable footer callbacks before recomposition.
                paperChoice.invoke()
                oldSave.invoke()
                oldShare.invoke()
            }
            compose.waitUntil(120_000) {
                compose.onAllNodes(hasTestTag("save_run_share") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("run_share_theme_2").assertIsSelected()
            assertEquals("A draft edit revokes the older export even without recomposition", before.keys, ownedImages().keys)
            assertEquals("The revoked draft must not create a cache image", beforeCache, ownedCacheFiles())
            assertEquals(0, chooserMonitor.hits)

            val forestChoice = requireNotNull(compose.onNodeWithTag("run_share_theme_0").fetchSemanticsNode()
                .config[SemanticsActions.OnClick].action)
            compose.runOnIdle { forestChoice.invoke() }
            compose.waitUntil(120_000) {
                compose.onAllNodes(hasTestTag("save_run_share") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("run_share_theme_0").assertIsSelected()
            val currentSave = compose.onNodeWithTag("save_run_share").fetchSemanticsNode().config[RecordActionSnapshot]
            val currentShare = compose.onNodeWithTag("send_run_share").fetchSemanticsNode().config[RecordActionSnapshot]
            compose.runOnIdle {
                // Replay the same real product callback twice before recomposition disables it.
                currentSave.invoke()
                currentSave.invoke()
                currentShare.invoke()
            }
            compose.waitUntil(90_000) {
                compose.onAllNodesWithTag("run_share_message").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("run_share_message").assertTextEquals("사진 앱에 저장했어요.")
            val after = ownedImages()
            val created = after.filterKeys { it !in before }
            assertEquals("Same-turn duplicate taps create only one owned gallery image", 1, created.size)
            val saved = created.values.single()
            assertEquals(0, saved.pending)
            assertEquals("image/png", saved.mime)
            val raw = app.contentResolver.openInputStream(saved.uri)!!.use { it.readBytes() }
            assertArrayEquals(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10), raw.copyOfRange(0, 8))
            val image = BitmapFactory.decodeByteArray(raw, 0, raw.size)!!
            try {
                assertEquals(1080, image.width)
                assertEquals(1080, image.height)
                assertEquals(255, android.graphics.Color.alpha(image.getPixel(0, 0)))
            } finally { image.recycle() }
            assertEquals("No chooser should have been launched while saving", 0, chooserMonitor.hits)

            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
            compose.onNodeWithTag("run_share_page").assertDoesNotExist()
            compose.runOnIdle {
                // Authorization remains true: the disposed editor/operation scope must reject these.
                assertTrue(allowed.get())
                currentSave.invoke()
                currentShare.invoke()
            }
            compose.waitForIdle()
            assertEquals(after.keys, ownedImages().keys)
            assertEquals(0, chooserMonitor.hits)
        } finally {
            try {
                compose.runOnIdle { visible.value = false }
                compose.waitForIdle()
            } finally {
                instrumentation.removeMonitor(chooserMonitor)
                // Only this serial case's new UUID PNGs in this package-owned Runmoni album are removed.
                // Existing rows, other albums, other package owners and arbitrary cache names survive.
                ownedImages().filterKeys { it !in before }.values.forEach {
                    app.contentResolver.delete(it.uri, null, null)
                }
                val root = File(app.cacheDir, "exports/run-shares").canonicalFile
                ownedCacheFiles().filterNot { it in beforeCache }.forEach { File(root, it).delete() }
                assertEquals("Original owned gallery rows are preserved", before.keys, ownedImages().keys)
            }
        }
    }

    companion object {
        private val GALLERY_NAME = Regex("Runmoni-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")
        private val CACHE_NAME = Regex("runmoni-card-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")
    }
}
