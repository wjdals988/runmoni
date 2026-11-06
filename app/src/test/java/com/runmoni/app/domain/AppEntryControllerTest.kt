package com.runmoni.app.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppEntryControllerTest {
    @Test fun freshEntryRequiresAChoiceAndDoesNotStartUnconfiguredGoogle() {
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { true })
        assertTrue(controller.state.value.requiresChoice)
        assertNull(controller.beginGoogle())
        assertFalse(controller.state.value.googleBusy)
    }

    @Test fun failedGoogleAuthenticationDoesNotEnterOrPersistAnyChoice() = runBlocking {
        val writes = mutableListOf<AppEntryChoice>()
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { writes += it; true })
        controller.setGoogleConfigured(true)
        val token = controller.beginGoogle()!!
        controller.finishGoogle(token, GoogleSignInOutcome.Failure("계정 선택을 완료하지 못했어요"))
        assertTrue(controller.state.value.requiresChoice)
        assertFalse(controller.state.value.googleBusy)
        assertEquals("계정 선택을 완료하지 못했어요", controller.state.value.issue)
        assertTrue(writes.isEmpty())
    }

    @Test fun cancelledGoogleCannotAcceptALateSuccess() = runBlocking {
        var writes = 0
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { writes++; true })
        controller.setGoogleConfigured(true)
        val token = controller.beginGoogle()!!
        controller.cancelGoogle(token)
        controller.finishGoogle(token, GoogleSignInOutcome.Success)
        assertTrue(controller.state.value.requiresChoice)
        assertFalse(controller.state.value.googleBusy)
        assertFalse(controller.isCurrentGoogle(token))
        assertEquals(0, writes)
    }

    @Test fun guestChoicePreemptsGoogleAndDuplicateRequestsNeverQueue() = runBlocking {
        val writes = mutableListOf<AppEntryChoice>()
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { writes += it; true })
        controller.setGoogleConfigured(true)
        val token = controller.beginGoogle()!!
        assertNull(controller.beginGoogle())
        controller.enterGuest()
        controller.finishGoogle(token, GoogleSignInOutcome.Success)
        assertEquals(AppEntryChoice.GUEST, controller.state.value.choice)
        assertFalse(controller.isCurrentGoogle(token))
        assertEquals(listOf(AppEntryChoice.GUEST), writes)
    }

    @Test fun successfulGoogleWaitsForTheSaveAndCannotBeReplacedWhileSaving() = runBlocking {
        val saving = CompletableDeferred<AppEntryChoice>()
        val release = CompletableDeferred<Boolean>()
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { saving.complete(it); release.await() })
        controller.setGoogleConfigured(true)
        val token = controller.beginGoogle()!!
        val completion = launch(start = CoroutineStart.UNDISPATCHED) { controller.finishGoogle(token, GoogleSignInOutcome.Success) }
        assertEquals(AppEntryChoice.GOOGLE, saving.await())
        assertTrue(controller.state.value.requiresChoice)
        assertTrue(controller.state.value.savingChoice)
        assertFalse(controller.state.value.googleBusy)
        assertNull(controller.beginGoogle())
        controller.enterGuest()
        controller.preserveExistingUse()
        controller.cancelGoogle(token)
        controller.setGoogleConfigured(false)
        release.complete(true)
        completion.join()
        assertEquals(AppEntryChoice.GOOGLE, controller.state.value.choice)
        assertFalse(controller.state.value.savingChoice)
        assertFalse(controller.state.value.googleConfigured)
    }

    @Test fun guestSaveFailureStillAllowsTheExplicitLocalSessionAndReportsItOnce() = runBlocking {
        var notices = 0
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { false }, { notices++ })
        controller.enterGuest()
        assertEquals(AppEntryChoice.GUEST, controller.state.value.choice)
        assertFalse(controller.state.value.requiresChoice)
        assertFalse(controller.state.value.savingChoice)
        assertNotNull(controller.state.value.issue)
        assertEquals(1, notices)
        controller.enterGuest()
        assertEquals(1, notices)
    }

    @Test fun saveExceptionDoesNotLeakStorageDetailsOrTrapTheUser() = runBlocking {
        var notices = 0
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { throw IllegalStateException("private-storage-detail") }, { notices++ })
        controller.enterGuest()
        assertEquals(AppEntryChoice.GUEST, controller.state.value.choice)
        assertFalse(controller.state.value.issue.orEmpty().contains("private-storage-detail"))
        assertEquals(1, notices)
    }

    @Test fun cancelledChoiceSaveClearsBusyAllowsTheExplicitSessionAndPropagatesCancellation() = runBlocking {
        val saving = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Boolean>()
        var notices = 0
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { saving.complete(Unit); never.await() }, { notices++ })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { controller.enterGuest() }
        saving.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(AppEntryChoice.GUEST, controller.state.value.choice)
        assertFalse(controller.state.value.savingChoice)
        assertEquals(1, notices)
    }

    @Test fun completedGuestGoogleAndLegacyChoicesDoNotReopenWhenConfigurationChanges() = runBlocking {
        for (choice in listOf(AppEntryChoice.GUEST, AppEntryChoice.GOOGLE, AppEntryChoice.EXISTING)) {
            var writes = 0
            val controller = AppEntryController(choice, { writes++; true })
            controller.setGoogleConfigured(true)
            controller.setGoogleConfigured(false)
            assertNull(controller.beginGoogle())
            controller.enterGuest()
            controller.preserveExistingUse()
            assertEquals(choice, controller.state.value.choice)
            assertFalse(controller.state.value.requiresChoice)
            assertEquals(0, writes)
        }
    }

    @Test fun existingRunOrRecoveryPreemptsPendingGoogleWithoutRelabelingItAsGoogleLogin() = runBlocking {
        val writes = mutableListOf<AppEntryChoice>()
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { writes += it; true })
        controller.setGoogleConfigured(true)
        val token = controller.beginGoogle()!!
        controller.preserveExistingUse()
        controller.finishGoogle(token, GoogleSignInOutcome.Success)
        assertEquals(AppEntryChoice.EXISTING, controller.state.value.choice)
        assertEquals(listOf(AppEntryChoice.EXISTING), writes)
        assertFalse(controller.state.value.googleBusy)
    }

    @Test fun confirmedGoogleSuccessCanEnterThisSessionEvenIfRememberingFails() = runBlocking {
        var notices = 0
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { false }, { notices++ })
        controller.setGoogleConfigured(true)
        controller.finishGoogle(controller.beginGoogle()!!, GoogleSignInOutcome.Success)
        assertEquals(AppEntryChoice.GOOGLE, controller.state.value.choice)
        assertFalse(controller.state.value.savingChoice)
        assertEquals(1, notices)
    }

    @Test fun aPreviousAttemptCannotCompleteOrCancelANewerGoogleAttempt() = runBlocking {
        val writes = mutableListOf<AppEntryChoice>()
        val controller = AppEntryController(AppEntryChoice.CHOOSE, { writes += it; true })
        controller.setGoogleConfigured(true)
        val old = controller.beginGoogle()!!
        controller.finishGoogle(old, GoogleSignInOutcome.Failure("다시 시도해 주세요"))
        val fresh = controller.beginGoogle()!!
        assertNotEquals(old, fresh)
        controller.cancelGoogle(old)
        controller.finishGoogle(old, GoogleSignInOutcome.Success)
        assertTrue(controller.isCurrentGoogle(fresh))
        controller.finishGoogle(fresh, GoogleSignInOutcome.Success)
        assertEquals(AppEntryChoice.GOOGLE, controller.state.value.choice)
        assertEquals(listOf(AppEntryChoice.GOOGLE), writes)
    }

    @Test fun persistedWireValuesRoundTripAndUnknownValuesRequireAChoice() {
        AppEntryChoice.entries.forEach { choice -> assertEquals(choice, AppEntryChoice.fromWire(choice.wireValue)) }
        assertEquals(AppEntryChoice.CHOOSE, AppEntryChoice.fromWire(null))
        assertEquals(AppEntryChoice.CHOOSE, AppEntryChoice.fromWire("unknown"))
    }
}
