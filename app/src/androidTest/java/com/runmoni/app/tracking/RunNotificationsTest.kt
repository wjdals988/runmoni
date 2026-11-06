package com.runmoni.app.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runmoni.app.domain.ActiveRun
import com.runmoni.app.domain.PauseReason
import com.runmoni.app.domain.RunPersistenceIssue
import com.runmoni.app.domain.RunPersistenceState
import com.runmoni.app.domain.RunPhase
import com.runmoni.app.domain.TrackPoint
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Factory results only: never posts a notification, sends an intent or starts a Service. */
@RunWith(AndroidJUnit4::class)
class RunNotificationsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val healthy = RunPersistenceState(initialized = true)
    private val created = mutableSetOf<PendingIntent>()

    private fun running() = ActiveRun(
        id = "notification-factory-${UUID.randomUUID()}", phase = RunPhase.RUNNING,
        distanceMeters = 1234.0, activeDurationMillis = 75_000,
        currentSpeedMetersPerSecond = 2.5, currentPaceSecondsPerKm = 400,
        gpsStatus = "PRIVATE_COORDINATE 37.1234567,127.7654321",
        points = listOf(TrackPoint(0, 37.1234567, 127.7654321, 5f, 1000)),
    )

    private fun build(run: ActiveRun, storage: RunPersistenceState = healthy): Notification =
        RunNotifications.build(context, run, storage).also { notification ->
            created += notification.contentIntent
            notification.actions.orEmpty().forEach { created += it.actionIntent }
            notification.publicVersion?.contentIntent?.let { created += it }
        }

    private fun actions(notification: Notification) = notification.actions.orEmpty().map { it.title.toString() }
    private fun visible(notification: Notification) = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT,
        Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT)
        .joinToString("\n") { notification.extras.getCharSequence(it)?.toString().orEmpty() }

    @After fun cancelOnlyThisTestsUniquePendingIntents() {
        created.forEach { it.cancel() }
    }

    @Test fun runningNotificationShowsPauseAndConfirmedFinishWithSecondResolution() {
        val run = running()
        val notification = build(run)
        assertEquals(listOf("일시정지", "종료"), actions(notification))
        assertEquals("1.23 km · 01:15", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(visible(notification).contains("현재 9.0 km/h · 페이스 6:40 /km"))
        assertEquals(Notification.CATEGORY_SERVICE, notification.category)
        assertEquals(RunNotifications.CHANNEL, notification.channelId)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_SHOW_WHEN, true))
        assertEquals(RunNotifications.activity(context, run.id, true), notification.actions[1].actionIntent)
        assertNotEquals(notification.contentIntent, notification.actions[1].actionIntent)
    }

    @Test fun bothKindsOfPauseOfferResumeAndFinishButNeverShowStaleSpeed() {
        for (reason in PauseReason.entries) {
            val notification = build(running().copy(phase = RunPhase.PAUSED, pauseReason = reason))
            assertEquals(listOf("재개", "종료"), actions(notification))
            assertEquals("1.23 km · 01:15", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertFalse(visible(notification).contains("9.0 km/h"))
            assertFalse(visible(notification).contains("6:40"))
            assertEquals(reason == PauseReason.AUTOMATIC,
                notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("자동"))
        }
    }

    @Test fun blockedSaveStatesReplaceRunControlsWithOpeningSaveChecking() {
        for (storage in listOf(healthy.copy(finishing = true), healthy.copy(issue = RunPersistenceIssue.FINISH),
            healthy.copy(issue = RunPersistenceIssue.CHECKPOINT))) {
            val notification = build(running(), storage)
            assertEquals(listOf("저장 확인"), actions(notification))
            assertEquals(notification.contentIntent, notification.actions.single().actionIntent)
            assertTrue(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("저장 확인"))
            assertFalse(visible(notification).contains("9.0 km/h"))
        }
    }

    @Test fun privateNotificationAndItsPublicVersionDoNotExposeCoordinatesOrPrivateMetrics() {
        val notification = build(running())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val public = notification.publicVersion
        assertNotNull(public)
        assertTrue(public.actions.isNullOrEmpty())
        assertEquals(notification.contentIntent, public.contentIntent)
        for (text in listOf(visible(notification), visible(public))) {
            assertFalse(text.contains("37.1234567"))
            assertFalse(text.contains("127.7654321"))
            assertFalse(text.contains("PRIVATE_COORDINATE"))
        }
        for (privateValue in listOf("1.23", "01:15", "9.0", "6:40")) {
            assertFalse("public version exposed $privateValue", visible(public).contains(privateValue))
        }
    }

    @Test fun runAndVerbIdentitiesKeepOldActionsSeparateFromNewRunsAndConfirmFinish() {
        val firstRun = running()
        val first = build(firstRun)
        val second = build(running())
        val rebuiltFirst = build(firstRun)
        val resumedFirst = build(firstRun.copy(phase = RunPhase.PAUSED))
        assertNotEquals(first.contentIntent, second.contentIntent)
        assertNotEquals(first.actions[0].actionIntent, second.actions[0].actionIntent)
        assertNotEquals(first.actions[1].actionIntent, second.actions[1].actionIntent)
        assertEquals(first.contentIntent, rebuiltFirst.contentIntent)
        assertEquals(first.actions[0].actionIntent, rebuiltFirst.actions[0].actionIntent)
        assertEquals(first.actions[1].actionIntent, rebuiltFirst.actions[1].actionIntent)
        assertNotEquals(first.actions[0].actionIntent, resumedFirst.actions[0].actionIntent)
        assertNotEquals(first.contentIntent, first.actions[1].actionIntent)
        for (intent in created) assertEquals(context.packageName, intent.creatorPackage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // These inspection APIs do not exist on API29; no API29 immutability claim is made.
            for (intent in created) assertTrue(intent.isImmutable)
            assertTrue(first.contentIntent.isActivity)
            assertTrue(first.actions[1].actionIntent.isActivity)
            assertTrue(first.actions[0].actionIntent.isService)
        }
    }

    @Test fun idleAndRecoveryFactoryResultsNeverOfferAnOperationThatStartsRecording() {
        for (phase in listOf(RunPhase.IDLE, RunPhase.RECOVERY)) {
            assertTrue(actions(build(running().copy(phase = phase))).isEmpty())
        }
    }
}
