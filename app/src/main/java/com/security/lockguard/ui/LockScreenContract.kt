package com.security.lockguard.ui

sealed interface LockScreenIntent {
    data object TriggerBiometricScan : LockScreenIntent
    data object BiometricSuccess : LockScreenIntent
    data class BiometricFailure(val errorMsg: String) : LockScreenIntent
    data object FaceVerified : LockScreenIntent
    data object FaceMismatch : LockScreenIntent
    data class SubmitPin(val pin: String) : LockScreenIntent
    data object TickCooldownTimer : LockScreenIntent
    data object ClearFeedback : LockScreenIntent
}

data class LockScreenState(
    val isLockoutActive: Boolean = false,
    val remainingLockoutSeconds: Long = 0L,
    val remainingAttempts: Int = 5,
    val feedbackMessage: String = "",
    val isAlarmActive: Boolean = false,
    val isUnlocked: Boolean = false
)

sealed interface LockScreenEffect {
    data object UnlockDevice : LockScreenEffect
    data object LaunchBiometricPrompt : LockScreenEffect
    data object CaptureIntruderPhoto : LockScreenEffect
    data object TriggerSirenAlarm : LockScreenEffect
    data object StopSirenAlarm : LockScreenEffect
}

