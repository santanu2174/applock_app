package com.security.lockguard.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MasterPinCryptoManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "secure_pin_vault"
        private const val KEY_PIN_HASH = "salted_pin_hash"
        private const val KEY_PIN_SALT = "pin_salt_bytes"
        private const val KEY_PIN_INITIALIZED = "pin_is_initialized"
        private const val ITERATIONS = 120_000
        private const val KEY_LENGTH = 512
    }

    private val masterKey = KeystoreManager.getOrCreateMasterKey(context)

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        // If first launch and no master PIN configured, provision default demo PIN "135790"
        if (!isPinRegistered()) {
            registerMasterPin("135790")
        }
    }

    fun isPinRegistered(): Boolean {
        return prefs.getBoolean(KEY_PIN_INITIALIZED, false) &&
                prefs.getString(KEY_PIN_HASH, null) != null
    }

    fun registerMasterPin(pin: String) {
        require(pin.length == 6 && pin.all { it.isDigit() }) {
            "PIN must be exactly 6 numeric digits."
        }

        val salt = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val hash = hashPin(pin.toCharArray(), salt)

        prefs.edit()
            .putBoolean(KEY_PIN_INITIALIZED, true)
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(candidatePin: String): Boolean {
        if (candidatePin.length != 6 || !candidatePin.all { it.isDigit() }) return false

        val storedSaltB64 = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val storedHashB64 = prefs.getString(KEY_PIN_HASH, null) ?: return false

        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val candidateHash = hashPin(candidatePin.toCharArray(), salt)
        val storedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)

        return constantTimeEquals(candidateHash, storedHash)
    }

    private fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, ITERATIONS, KEY_LENGTH)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return skf.generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

