# LockGuard - High-Security Mobile Lock Screen & App Locker (applock_app)

LockGuard is an enterprise-grade mobile security lock screen, app locker, and intrusion defense application for Android built with Kotlin, Jetpack Compose, CameraX, Google ML Kit, and Android Keystore.

---

## 1. System Architecture

```
                                  [ User Interaction ]
                                           │
                    ┌──────────────────────┴──────────────────────┐
                    ▼                                             ▼
        [ Biometric Hardware ]                           [ Master PIN Entry ]
      (BiometricPrompt + Crypto)                        (PBKDF2WithHmacSHA512)
                    │                                             │
                    ├─────────────────┬───────────────────────────┤
                    ▼                 ▼                           ▼
              [ Verified ]       [ Mismatch ]               [ Verified ]
                    │                 │                           │
                    │                 ▼                           │
                    │      [ Increment Rate Limiter ]             │
                    │                 │                           │
                    │      ┌──────────┴──────────┐                │
                    │      ▼                     ▼                │
                    │  [ 1 - 4 Fails ]      [ 5 Fails ]           │
                    │      │                     │                │
                    │      │                     ▼                │
                    │      │             [ 5-Min Cooldown ]       │
                    │      │             [ Biometrics Off ]       │
                    │      │                     │                │
                    │      └──────────┬──────────┘                │
                    │                 ▼                           │
                    │     [ Intruder Capture Pipeline ]           │
                    │     • Headless Front CameraX                │
                    │     • GPS Coordinates Stamp                 │
                    │     • AES-256-GCM EncryptedFile             │
                    │     • USAGE_ALARM Siren Override            │
                    │                                             │
                    └─────────────────┬───────────────────────────┘
                                      ▼
                             [ Dismiss LockScreen ]
                             [ Reset Rate Limiter ]
                             [ Disengage Siren ]
```

---

## 2. Core Security Capabilities

### A. Triple-Tier Authentication
1. **Hardware Biometric Fingerprint:** Integrates `androidx.biometric.BiometricPrompt` bound to an authenticated `CryptoObject` `Cipher` generated inside Android Keystore (`KeyGenParameterSpec` with `setUserAuthenticationRequired(true)`).
2. **AI Facial Recognition:** ML Kit Face Detection pipeline evaluating front-camera frames against enrolled owner embeddings using cosine similarity.
3. **Master Security PIN:** 6-digit numeric fallback hashed with **PBKDF2WithHmacSHA512** (120,000 iterations + 32-byte cryptographically secure salt) and stored inside **Jetpack EncryptedSharedPreferences**.

### B. Real-Time Application Locker (App Locker)
- **Zero-Latency App Interception:** Detects launches of protected applications and immediately presents the biometric/PIN lock screen overlay.
- **Auto Face & Fingerprint Prompt:** Opens biometric sensor prompt automatically upon launching a locked application.
- **Anti-Bypass Guard:** Redirects to home screen on back navigation to prevent unauthorized access.
- **Automatic App Re-locking:** Instantly re-locks protected applications whenever the user leaves the app or turns off the screen.

### C. Intruder Detection & Evidence Gathering
- **Headless CameraX Snapshot:** Immediately takes a photo from the front-facing camera upon failed authentication without showing an on-screen preview or engaging the camera flash.
- **GPS Coordinates Stamping:** Attaches current latitude and longitude to the evidence file name and metadata sidecar.
- **Hardware-Level Encryption:** The snapshot is written directly into AES-256-GCM encrypted storage using `androidx.security.crypto.EncryptedFile`. Plaintext temporary files are wiped from memory and cache.
- **Mute & DND Bypass Siren:** Intruder alarm sound plays over `AudioAttributes.USAGE_ALARM` with `FLAG_AUDIBILITY_ENFORCED`, overriding system silent/vibrate switches.

### D. Rate Limiting & Anti-Tampering Lockout
- **Lockout Policy:** Tracks combined failed attempts across biometric and PIN inputs.
- **Cooldown Window:** At 5 failed attempts, enforces a strict 5-minute cooldown timer during which biometric sensors are disabled.
- **Clock-Tampering Defense:** Uses `SystemClock.elapsedRealtime()` (hardware monotonic boot clock) alongside encrypted timestamps to prevent attackers from bypassing the cooldown by advancing the device clock.
- **Anti-Screenshot & Recording:** `WindowManager.LayoutParams.FLAG_SECURE` prevents capturing PIN entries or security overlays.

---

## 3. Directory Layout

```
mobile_security_application/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
├── simulator.html
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/ (colors.xml, strings.xml, themes.xml)
        │   │   └── xml/ (accessibility_service_config.xml, device_admin_policies.xml)
        │   └── java/com/security/lockguard/
        │       ├── LockGuardApp.kt
        │       ├── applock/
        │       │   ├── AppLockManager.kt
        │       │   ├── AppLockService.kt
        │       │   └── AppLockAccessibilityService.kt
        │       ├── security/
        │       │   ├── KeystoreManager.kt
        │       │   ├── BiometricSecurityHandler.kt
        │       │   ├── MasterPinCryptoManager.kt
        │       │   └── RateLimiterPolicy.kt
        │       ├── intruder/
        │       │   ├── IntruderCaptureEngine.kt
        │       │   ├── IntruderAlarmEngine.kt
        │       │   └── EncryptedStorageManager.kt
        │       ├── ml/
        │       │   ├── FaceDetectorHelper.kt
        │       │   └── FaceEmbeddingComparator.kt
        │       ├── service/
        │       │   └── LockGuardForegroundService.kt
        │       ├── receiver/
        │       │   ├── BootCompletedReceiver.kt
        │       │   └── SimChangeReceiver.kt
        │       └── ui/
        │           ├── MainActivity.kt
        │           ├── LockScreenActivity.kt
        │           ├── FaceEnrollmentActivity.kt
        │           ├── LockScreenViewModel.kt
        │           ├── LockScreenContract.kt
        │           ├── theme/ (Color.kt, Theme.kt, Type.kt)
        │           └── components/
        │               ├── LockScreenHUD.kt
        │               ├── FaceScannerOverlay.kt
        │               ├── BiometricScannerRing.kt
        │               ├── LockoutCountdownDisplay.kt
        │               └── PinKeypadOverlay.kt
        └── test/
            └── java/com/security/lockguard/
                └── FaceEmbeddingComparatorTest.kt
```

---

## 4. How to Build & Run

### Prerequisites
- **Android Studio** (Hedgehog or newer).
- **JDK 17** (bundled with Android Studio).
- Target device or Android Emulator running **Android 9.0 (API 28)** or higher.

### Opening the Project
1. Launch **Android Studio**.
2. Select **Open** and choose this project root folder.
3. Let Gradle sync dependencies.
4. Run the app on your physical test device or emulator.

### Initial Credentials
- Default demo Master PIN: `135790` (can be updated at any time via App Settings).
