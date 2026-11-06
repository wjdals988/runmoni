package com.runmoni.app.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A local entry choice, never a community membership or an automatic backup authorization. */
enum class AppEntryChoice(val wireValue: String) {
    CHOOSE("choose"), GUEST("guest"), GOOGLE("google"), EXISTING("existing");

    companion object {
        fun fromWire(value: String?): AppEntryChoice = entries.firstOrNull { it.wireValue == value } ?: CHOOSE
    }
}

data class AppEntryState(
    val choice: AppEntryChoice = AppEntryChoice.CHOOSE,
    val googleConfigured: Boolean = false,
    val googleBusy: Boolean = false,
    val savingChoice: Boolean = false,
    val issue: String? = null,
) {
    val requiresChoice: Boolean get() = choice == AppEntryChoice.CHOOSE
}

sealed interface GoogleSignInOutcome {
    data object Success : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

/**
 * Only coordinates local navigation and remembering the user's explicit choice.
 * Authentication, age consent, community profiles and uploads stay outside this controller.
 */
internal class AppEntryController(
    initialChoice: AppEntryChoice,
    private val persistChoice: suspend (AppEntryChoice) -> Boolean,
    private val onRememberFailure: () -> Unit = {},
) {
    private val lock = Any()
    private val mutableState = MutableStateFlow(AppEntryState(choice = initialChoice))
    val state: StateFlow<AppEntryState> = mutableState.asStateFlow()
    private var generation = 0L
    private var googleToken: Long? = null

    fun setGoogleConfigured(configured: Boolean) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(googleConfigured = configured)
    }

    fun beginGoogle(): Long? = synchronized(lock) {
        val current = mutableState.value
        if (!current.requiresChoice || !current.googleConfigured || current.googleBusy || current.savingChoice) return@synchronized null
        (++generation).also { token ->
            googleToken = token
            mutableState.value = current.copy(googleBusy = true, issue = null)
        }
    }

    fun isCurrentGoogle(token: Long): Boolean = synchronized(lock) { currentGoogleLocked(token) }

    private fun currentGoogleLocked(token: Long): Boolean = googleToken == token && generation == token &&
        mutableState.value.let { it.requiresChoice && it.googleBusy && !it.savingChoice }

    suspend fun finishGoogle(token: Long, outcome: GoogleSignInOutcome) {
        val shouldRemember = synchronized(lock) {
            if (!currentGoogleLocked(token)) return
            googleToken = null
            when (outcome) {
                is GoogleSignInOutcome.Failure -> {
                    mutableState.value = mutableState.value.copy(googleBusy = false, issue = outcome.message)
                    false
                }
                GoogleSignInOutcome.Success -> {
                    mutableState.value = mutableState.value.copy(googleBusy = false, savingChoice = true, issue = null)
                    true
                }
            }
        }
        if (shouldRemember) rememberAndEnter(token, AppEntryChoice.GOOGLE)
    }

    fun cancelGoogle(token: Long) = synchronized(lock) {
        if (currentGoogleLocked(token)) {
            generation++
            googleToken = null
            mutableState.value = mutableState.value.copy(googleBusy = false, issue = null)
        }
    }

    suspend fun enterGuest() = enterLocally(AppEntryChoice.GUEST)

    /** Preserve a returning user's work without relabeling their crew or summary account as app login. */
    suspend fun preserveExistingUse() = enterLocally(AppEntryChoice.EXISTING)

    private suspend fun enterLocally(choice: AppEntryChoice) {
        val token = synchronized(lock) {
            val current = mutableState.value
            if (!current.requiresChoice || current.savingChoice) return
            googleToken = null
            (++generation).also {
                mutableState.value = current.copy(googleBusy = false, savingChoice = true, issue = null)
            }
        }
        rememberAndEnter(token, choice)
    }

    private suspend fun rememberAndEnter(token: Long, choice: AppEntryChoice) {
        var cancelled: CancellationException? = null
        val remembered = try {
            persistChoice(choice)
        } catch (failure: CancellationException) {
            cancelled = failure
            false
        } catch (_: Exception) {
            false
        }
        val entered = synchronized(lock) {
            if (generation != token || !mutableState.value.requiresChoice || !mutableState.value.savingChoice) false
            else {
                mutableState.value = mutableState.value.copy(choice = choice, googleBusy = false, savingChoice = false,
                    issue = if (remembered) null else "이번 실행에서는 계속할 수 있어요. 다음 실행에서 선택이 다시 필요할 수 있어요.")
                true
            }
        }
        if (entered && !remembered) onRememberFailure()
        cancelled?.let { throw it }
    }
}
