package com.security.lockguard.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricSecurityHandler(
    private val activity: FragmentActivity,
    private val onAuthSuccess: () -> Unit,
    private val onAuthFailed: (isHardwareError: Boolean, errorMsg: String) -> Unit,
    private val onFallbackToPin: () -> Unit
) {
    companion object {
        private const val BIO_KEY_ALIAS = "LockGuard_BioAuth_Key"
    }

    private val executor = ContextCompat.getMainExecutor(activity)

    fun canAuthenticate(): Int {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }

    fun authenticate() {
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onAuthFailed(false, "Biometric signature unrecognized.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        onFallbackToPin()
                    } else {
                        onAuthFailed(true, errString.toString())
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Identity Verification")
            .setSubtitle("Confirm authorized identity to access application")
            .setNegativeButtonText("Use Security PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val cipher = try {
            KeystoreManager.getBiometricCipher(BIO_KEY_ALIAS)
        } catch (_: Exception) {
            null
        }

        if (cipher != null) {
            try {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                return
            } catch (e: Exception) {
                // Fallback to standard biometric authentication
            }
        }

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onFallbackToPin()
        }
    }
}

