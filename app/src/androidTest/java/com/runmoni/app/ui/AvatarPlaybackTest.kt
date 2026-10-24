package com.runmoni.app.ui

import android.animation.ValueAnimator
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.runmoni.app.domain.AppState
import com.runmoni.app.domain.AvatarStyle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Actual real-clock playback; no frameOverride and no advanceTimeBy substitute. */
class AvatarPlaybackTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val avatar = AvatarStyle(hair=2,top=4,bottom=4,socks=2,shoes=1,headwear=1,ear=1,wrist=1)

    private fun shell(command: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun animatorScale(value: Float) {
        shell("settings put global animator_duration_scale $value")
        compose.waitUntil(timeoutMillis=5000) { ValueAnimator.areAnimatorsEnabled() == (value > 0f) }
    }

    private fun withAnimators(block: () -> Unit) {
        val original = Settings.Global.getString(instrumentation.targetContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        require(original == null || original.toFloatOrNull()?.isFinite() == true) { "Invalid original animator scale" }
        try {
            animatorScale(1f)
            compose.mainClock.autoAdvance = true
            block()
        } finally {
            if(original == null) shell("settings delete global animator_duration_scale")
            else shell("settings put global animator_duration_scale ${original.toFloat()}")
        }
    }

    private fun hash(tag: String): Int {
        val bitmap=compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val pixels=IntArray(bitmap.width*bitmap.height)
        bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        return pixels.contentHashCode()
    }

    private fun assertMoving(tag: String) {
        val hashes=mutableSetOf<Int>()
        val deadline=SystemClock.uptimeMillis()+4000
        while(hashes.size<3 && SystemClock.uptimeMillis()<deadline) {
            hashes+=hash(tag)
            Thread.sleep(35) // Test thread only; never sleep the UI thread.
        }
        assertTrue("Normal playback did not produce three different actual captures",hashes.size>=3)
    }

    private fun assertFrozen(tag: String) {
        Thread.sleep(200) // Let any in-flight rendered frame settle.
        // Run the coroutine delay scheduler as well as wall time. Frame selection
        // still reads SystemClock; this never substitutes a frameOverride.
        compose.mainClock.advanceTimeBy(200)
        val stable=hash(tag)
        repeat(5) { Thread.sleep(100); compose.mainClock.advanceTimeBy(100); assertEquals("Stopped animation changed pixels",stable,hash(tag)) }
    }

    @Test fun realClockPlaybackRendersAllEightFrames() = withAnimators {
        val drawn=Collections.synchronizedSet(mutableSetOf<Int>())
        compose.setContent {
            RunmoniTheme { MiniMi(avatar,Modifier.size(144.dp,168.dp).testTag("playback_avatar"),running=true,
                onFrameRendered={ drawn.add(it) }) }
        }
        compose.waitUntil(timeoutMillis=6000) { drawn.containsAll((0..7).toList()) }
        assertEquals((0..7).toSet(),synchronized(drawn) { drawn.toSet() })
        assertMoving("playback_avatar")
    }

    @Test fun actualClosetPauseAndResumeFreezeAndRestartPixels() = withAnimators {
        val state=mutableStateOf(AppState())
        compose.setContent { RunmoniTheme { ClosetDialog(state.value.profile,RunmoniUiTest.FakeActions(state)) {} } }
        assertMoving("closet_avatar_preview")
        compose.onNodeWithTag("closet_animation").performClick()
        assertFrozen("closet_avatar_preview")
        compose.onNodeWithTag("closet_animation").performClick()
        assertMoving("closet_avatar_preview")
    }

    private class Owner : LifecycleOwner {
        val registry=LifecycleRegistry(this)
        override val lifecycle: Lifecycle get()=registry
    }

    @Test fun lifecycleStopFreezesLastFrameAndStartResumes() = withAnimators {
        lateinit var owner: Owner
        instrumentation.runOnMainSync { owner=Owner(); owner.registry.currentState=Lifecycle.State.RESUMED }
        try {
            compose.setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    RunmoniTheme { MiniMi(avatar,Modifier.size(144.dp,168.dp).testTag("playback_avatar"),running=true) }
                }
            }
            assertMoving("playback_avatar")
            instrumentation.runOnMainSync { owner.registry.currentState=Lifecycle.State.CREATED }
            assertFrozen("playback_avatar")
            instrumentation.runOnMainSync { owner.registry.currentState=Lifecycle.State.RESUMED }
            assertMoving("playback_avatar")
        } finally {
            instrumentation.runOnMainSync { owner.registry.currentState=Lifecycle.State.DESTROYED }
        }
    }

    @Test fun systemDisabledAnimationsFreezeAndCanResume() = withAnimators {
        val latestRenderedFrame = AtomicInteger(-1)
        compose.setContent { RunmoniTheme { MiniMi(avatar,Modifier.size(144.dp,168.dp).testTag("playback_avatar"),running=true, onFrameRendered = { latestRenderedFrame.set(it) }) } }
        assertMoving("playback_avatar")
        animatorScale(0f)
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5000) { latestRenderedFrame.get() == 0 }
        assertFrozen("playback_avatar")
        animatorScale(1f)
        assertMoving("playback_avatar")
    }
}
