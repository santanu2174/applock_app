package com.security.lockguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.security.lockguard.security.MasterPinCryptoManager
import com.security.lockguard.security.RateLimiterPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LockScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val rateLimiter = RateLimiterPolicy(application)
    private val pinManager = MasterPinCryptoManager(application)

    private val _state = MutableStateFlow(
        LockScreenState(
            isLockoutActive = rateLimiter.isLockoutActive(),
            remainingLockoutSeconds = rateLimiter.getRemainingLockoutMillis() / 1000,
            remainingAttempts = rateLimiter.getRemainingAttempts()
        )
    )
    val state: StateFlow<LockScreenState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<LockScreenEffect>()
    val effect: SharedFlow<LockScreenEffect> = _effect.asSharedFlow()

    private var timerJob: Job? = null

    init {
        checkAndStartLockoutTimerIfNeeded()
    }

    fun handleIntent(intent: LockScreenIntent) {
        when (intent) {
            is LockScreenIntent.TriggerBiometricScan -> {
                if (!_state.value.isLockoutActive) {
                    viewModelScope.launch {
                        _effect.emit(LockScreenEffect.LaunchBiometricPrompt)
                    }
                }
            }

            is LockScreenIntent.BiometricSuccess,
            is LockScreenIntent.FaceVerified -> {
                handleAuthenticationSuccess()
            }

            is LockScreenIntent.BiometricFailure -> {
                handleAuthenticationFailure(intent.errorMsg)
            }

            is LockScreenIntent.FaceMismatch -> {
                handleAuthenticationFailure("Face not recognized as registered owner.")
            }

            is LockScreenIntent.SubmitPin -> {
                handlePinSubmission(intent.pin)
            }

            is LockScreenIntent.TickCooldownTimer -> {
                updateCooldown()
            }

            is LockScreenIntent.ClearFeedback -> {
                _state.update { it.copy(feedbackMessage = "") }
            }
        }
    }

    private fun handleAuthenticationSuccess() {
        rateLimiter.clearLockout()
        timerJob?.cancel()

        _state.update {
            it.copy(
                isLockoutActive = false,
                remainingAttempts = RateLimiterPolicy.MAX_FAILED_ATTEMPTS,
                feedbackMessage = "Access Granted",
                isAlarmActive = false,
                isUnlocked = true
            )
        }

        viewModelScope.launch {
            _effect.emit(LockScreenEffect.StopSirenAlarm)
            _effect.emit(LockScreenEffect.UnlockDevice)
        }
    }

    private fun handleAuthenticationFailure(errorMessage: String) {
        val failedCount = rateLimiter.recordFailedAttempt()
        val isLocked = rateLimiter.isLockoutActive()
        val remainingAttempts = rateLimiter.getRemainingAttempts()

        _state.update {
            it.copy(
                isLockoutActive = isLocked,
                remainingAttempts = remainingAttempts,
                feedbackMessage = errorMessage,
                isAlarmActive = true
            )
        }

        viewModelScope.launch {
            // Immediately execute intruder defense triggers
            _effect.emit(LockScreenEffect.CaptureIntruderPhoto)
            _effect.emit(LockScreenEffect.TriggerSirenAlarm)
        }

        if (isLocked) {
            startLockoutTimer()
        }
    }

    private fun handlePinSubmission(pin: String) {
        val isValid = pinManager.verifyPin(pin)
        if (isValid) {
            handleAuthenticationSuccess()
        } else {
            handleAuthenticationFailure("Invalid Security PIN entered.")
        }
    }

    private fun checkAndStartLockoutTimerIfNeeded() {
        if (rateLimiter.isLockoutActive()) {
            startLockoutTimer()
        }
    }

    private fun startLockoutTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (rateLimiter.isLockoutActive()) {
                val remainingSeconds = rateLimiter.getRemainingLockoutMillis() / 1000
                _state.update {
                    it.copy(
                        isLockoutActive = true,
                        remainingLockoutSeconds = remainingSeconds
                    )
                }
                delay(1000)
            }
            // Cooldown finished
            rateLimiter.clearLockout()
            _state.update {
                it.copy(
                    isLockoutActive = false,
                    remainingLockoutSeconds = 0,
                    remainingAttempts = RateLimiterPolicy.MAX_FAILED_ATTEMPTS,
                    feedbackMessage = "Lockout period ended. Biometrics re-enabled."
                )
            }
        }
    }

    private fun updateCooldown() {
        if (rateLimiter.isLockoutActive()) {
            val remainingSeconds = rateLimiter.getRemainingLockoutMillis() / 1000
            _state.update { it.copy(remainingLockoutSeconds = remainingSeconds) }
        }
    }
}

