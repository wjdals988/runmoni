package com.runmoni.app.audio

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.runmoni.app.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/** Real extractor/decoder/AudioTrack/foreground-service path. No cloud or audible quality claim. */
@RunWith(AndroidJUnit4::class)
class AudioLocalPlaybackTest {
    @Test fun localFileHasIndependentGainsAndFinishesAfterPcmTail() = withController { context, controller, files ->
        val clip = pcmWav(context, 2).also(files::add)
        val began = SystemClock.elapsedRealtime()
        onMain { controller.playLocalMusicFile(Uri.fromFile(clip)) }
        assertEquals(MusicSource.FILE, controller.status.value.musicSource)
        assertFalse(controller.status.value.musicSending)
        assertFalse(controller.status.value.microphoneEnabled)
        assertEquals(AudioConnection.DISCONNECTED, controller.status.value.connection)
        onMain { controller.setMusicVolume(.25f); controller.setVoiceVolume(.4f) }
        assertEquals(.25f, controller.status.value.musicVolume, 0f)
        assertEquals(.4f, controller.status.value.voiceVolume, 0f)
        assertEquals(0L, controller.status.value.bytesSent)
        assertEquals(0L, controller.status.value.queuedBytes)
        until("natural EOF") { controller.status.value.musicSource == MusicSource.NONE }
        assertNull("decoder/output succeeded", controller.status.value.error)
        assertTrue("2-second PCM tail was not immediately discarded", SystemClock.elapsedRealtime() - began >= 1750)
        until("foreground service ends after EOF") { !audioServiceRunning(context) }
    }

    @Test fun explicitStopReleasesOutputAndPreviousWorkerCannotStopNewFile() = withController { context, controller, files ->
        val longClip = pcmWav(context, 8).also(files::add)
        val nextClip = pcmWav(context, 2).also(files::add)
        onMain { controller.playLocalMusicFile(Uri.fromFile(longClip)) }
        assertEquals(MusicSource.FILE, controller.status.value.musicSource)
        Thread.sleep(250)
        onMain { controller.stopMusic() }
        assertEquals(MusicSource.NONE, controller.status.value.musicSource)
        assertFalse(controller.status.value.musicSending)
        onMain { controller.playLocalMusicFile(Uri.fromFile(nextClip)) }
        Thread.sleep(250)
        assertEquals("previous canceled worker leaves new playback active", MusicSource.FILE, controller.status.value.musicSource)
        until("restarted file EOF") { controller.status.value.musicSource == MusicSource.NONE }
        assertNull("restart acquired and released actual output", controller.status.value.error)
        until("foreground service ends after restart") { !audioServiceRunning(context) }
    }

    private fun withController(test: (Context, AudioController, MutableList<File>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= 33) UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        val controller = onMain { AudioController(context) }
        val files = mutableListOf<File>()
        var leaseStops = 0
        onMain { controller.onBroadcastStopped = { leaseStops++ } }
        try {
            test(context, controller, files)
            assertEquals("local playback never acquires/revokes a DJ lease", 0, leaseStops)
        } finally {
            onMain { controller.close() }
            scenario.close()
            files.forEach { it.delete() }
        }
    }

    private fun pcmWav(context: Context, seconds: Int): File {
        val samples = seconds * 48_000
        val pcmBytes = samples * 4
        val data = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        data.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + pcmBytes)
        data.put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
        data.putShort(1).putShort(2).putInt(48_000).putInt(192_000).putShort(4).putShort(16)
        data.put("data".toByteArray(Charsets.US_ASCII)).putInt(pcmBytes)
        repeat(samples) { index ->
            val tone = (sin(2 * PI * 440 * index / 48_000) * 1000).toInt().toShort()
            data.putShort(tone).putShort(tone)
        }
        return File.createTempFile("audio-pipeline-", ".wav", context.cacheDir).apply { writeBytes(data.array()) }
    }

    @Suppress("DEPRECATION")
    private fun audioServiceRunning(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == AudioProjectionService::class.java.name }

    private fun until(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
        assertTrue("$label before 10-second timeout", condition())
    }

    private fun <T> onMain(block: suspend () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
}
