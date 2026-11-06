package com.security.lockguard.security

import android.content.Context
import android.os.SystemClock
import androidx.security.crypto.EncryptedSharedPreferences

class RateLimiterPolicy(context: Context) {

    companion object {
        private const val PREFS_FILE = "secure_lockout_policy_prefs"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        private const val KEY_LOCKOUT_TIMESTAMP = "lockout_reference_timestamp"
        private const val KEY_LOCKOUT_BOOT_ELAPSED = "lockout_boot_elapsed"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 5 * 60 * 1000L // 5 Minutes
    }

    private val masterKey = KeystoreManager.getOrCreateMasterKey(context)

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun recordFailedAttempt(): Int {
        val currentCount = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        prefs.edit().putInt(KEY_FAILED_ATTEMPTS, currentCount).apply()

        if (currentCount >= MAX_FAILED_ATTEMPTS) {
            enforceLockout()
        }
        return currentCount
    }

    private fun enforceLockout() {
        val nowWallClock = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()

        prefs.edit()
            .putLong(KEY_LOCKOUT_TIMESTAMP, nowWallClock)
            .putLong(KEY_LOCKOUT_BOOT_ELAPSED, nowElapsed)
            .apply()
    }

    fun isLockoutActive(): Boolean {
        if (prefs.getInt(KEY_FAILED_ATTEMPTS, 0) < MAX_FAILED_ATTEMPTS) return false

        val remaining = getRemainingLockoutMillis()
        if (remaining <= 0) {
            clearLockout()
            return false
        }
        return true
    }

    fun getRemainingLockoutMillis(): Long {
        val lockElapsed = prefs.getLong(KEY_LOCKOUT_BOOT_ELAPSED, -1L)
        val lockWall = prefs.getLong(KEY_LOCKOUT_TIMESTAMP, -1L)

        if (lockElapsed == -1L || lockWall == -1L) return 0L

        val currentElapsed = SystemClock.elapsedRealtime()
        val elapsedSinceLock = currentElapsed - lockElapsed

        // Reboot tamper detection: If monotonic clock reset (current < lockElapsed),
        // fallback safely to wall-clock difference
        val effectiveElapsed = if (elapsedSinceLock >= 0) {
            elapsedSinceLock
        } else {
            val wallDiff = System.currentTimeMillis() - lockWall
            if (wallDiff > 0) wallDiff else 0L
        }

        val remaining = LOCKOUT_DURATION_MS - effectiveElapsed
        return if (remaining > 0) remaining else 0L
    }

    fun clearLockout() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_TIMESTAMP, -1L)
            .putLong(KEY_LOCKOUT_BOOT_ELAPSED, -1L)
            .apply()
    }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun getRemainingAttempts(): Int {
        val failed = getFailedAttempts()
        val remaining = MAX_FAILED_ATTEMPTS - failed
        return if (remaining > 0) remaining else 0
    }
}

