package com.security.lockguard.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.security.lockguard.applock.AppLockManager
import com.security.lockguard.applock.AppLockService
import com.security.lockguard.intruder.IntruderAlarmEngine
import com.security.lockguard.intruder.IntruderCaptureEngine
import com.security.lockguard.ml.FaceDetectorHelper
import com.security.lockguard.ml.FaceEmbeddingComparator
import com.security.lockguard.security.BiometricSecurityHandler
import com.security.lockguard.ui.components.FaceScannerOverlay
import com.security.lockguard.ui.components.LockScreenHUD
import com.security.lockguard.ui.theme.LockGuardTheme
import kotlinx.coroutines.launch

class LockScreenActivity : FragmentActivity() {

    private val viewModel: LockScreenViewModel by viewModels()

    private lateinit var biometricHandler: BiometricSecurityHandler
    private lateinit var captureEngine: IntruderCaptureEngine
    private lateinit var alarmEngine: IntruderAlarmEngine
    private lateinit var faceDetector: FaceDetectorHelper
    private lateinit var embeddingComparator: FaceEmbeddingComparator

    private var currentTargetPackage by mutableStateOf<String?>(null)
    private var hasAutoPromptedBiometrics = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions evaluated
    }

    private val enrollFaceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Will refresh face enrollment state in onResume
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTargetPackage = intent.getStringExtra(EXTRA_LOCKED_PACKAGE)
        dismissInterceptNotification()

        // 1. Enforce Window Security & LockScreen visibility
        configureWindowSecurity()

        // 2. Prevent Back Button bypass - return to home launcher if intercepting an app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val lockedPkg = currentTargetPackage
                if (!lockedPkg.isNullOrEmpty()) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(homeIntent)
                    finish()
                }
            }
        })

        // 3. Initialize security hardware engines
        captureEngine = IntruderCaptureEngine(this)
        alarmEngine = IntruderAlarmEngine(this)
        faceDetector = FaceDetectorHelper()
        embeddingComparator = FaceEmbeddingComparator(this)

        biometricHandler = BiometricSecurityHandler(
            activity = this,
            onAuthSuccess = {
                viewModel.handleIntent(LockScreenIntent.BiometricSuccess)
            },
            onAuthFailed = { isHardwareError, errorMsg ->
                if (!isHardwareError) {
                    viewModel.handleIntent(LockScreenIntent.BiometricFailure(errorMsg))
                }
            },
            onFallbackToPin = {
                // User chose PIN prompt fallback
            }
        )

        // 4. Request required runtime permissions (Camera, Location)
        requestSecurityPermissions()

        // 5. Collect ViewModel Effects & State
        observeViewModelEffects()

        // 6. Set Jetpack Compose View
        setContent {
            LockGuardTheme {
                val state = viewModel.state.collectAsState().value
                var isFaceScanning by remember { mutableStateOf(false) }
                var isFaceEnrolled by remember { mutableStateOf(embeddingComparator.isFaceEnrolled()) }

                // Auto-open Live Camera Face Scanner if face is enrolled
                LaunchedEffect(isFaceEnrolled) {
                    if (isFaceEnrolled && ContextCompat.checkSelfPermission(this@LockScreenActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        isFaceScanning = true
                    }
                }

                val targetAppName = remember(currentTargetPackage) {
                    currentTargetPackage?.let { pkg ->
                        try {
                            val appInfo = packageManager.getApplicationInfo(pkg, 0)
                            packageManager.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkg
                        }
                    }
                }

                // Refresh enrollment status when returning to screen
                DisposableEffect(Unit) {
                    isFaceEnrolled = embeddingComparator.isFaceEnrolled()
                    onDispose {}
                }

                LockScreenHUD(
                    isLockoutActive = state.isLockoutActive,
                    isFaceEnrolled = isFaceEnrolled,
                    remainingSeconds = state.remainingLockoutSeconds,
                    remainingAttempts = state.remainingAttempts,
                    feedbackMessage = state.feedbackMessage,
                    targetAppName = targetAppName,
                    onFaceTrigger = {
                        if (ContextCompat.checkSelfPermission(this@LockScreenActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            isFaceScanning = true
                        } else {
                            requestSecurityPermissions()
                        }
                    },
                    onEnrollFace = {
                        val intent = Intent(this@LockScreenActivity, FaceEnrollmentActivity::class.java)
                        enrollFaceLauncher.launch(intent)
                    },
                    onBiometricTrigger = {
                        viewModel.handleIntent(LockScreenIntent.TriggerBiometricScan)
                    },
                    onPinSubmit = { enteredPin ->
                        viewModel.handleIntent(LockScreenIntent.SubmitPin(enteredPin))
                    }
                )

                // Live CameraX Face Scanner Dialog
                if (isFaceScanning) {
                    FaceScannerOverlay(
                        faceDetector = faceDetector,
                        embeddingComparator = embeddingComparator,
                        onSuccess = {
                            isFaceScanning = false
                            viewModel.handleIntent(LockScreenIntent.FaceVerified)
                        },
                        onFailure = { reason ->
                            isFaceScanning = false
                            viewModel.handleIntent(LockScreenIntent.FaceMismatch)
                        },
                        onDismiss = {
                            isFaceScanning = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentTargetPackage = intent?.getStringExtra(EXTRA_LOCKED_PACKAGE)
        dismissInterceptNotification()
        hasAutoPromptedBiometrics = false
    }

    override fun onResume() {
        super.onResume()
        dismissInterceptNotification()
        if (!hasAutoPromptedBiometrics && !viewModel.state.value.isLockoutActive) {
            hasAutoPromptedBiometrics = true
            viewModel.handleIntent(LockScreenIntent.TriggerBiometricScan)
        }
    }

    private fun dismissInterceptNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(AppLockService.INTERCEPT_NOTIF_ID)
    }

    private fun configureWindowSecurity() {
        // Enforce anti-screen capture / recording of master PIN
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Modern Android 8.0+ Lock Screen Override
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun requestSecurityPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val neededPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    private fun observeViewModelEffects() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effect.collect { effect ->
                    when (effect) {
                        is LockScreenEffect.LaunchBiometricPrompt -> {
                            biometricHandler.authenticate()
                        }

                        is LockScreenEffect.CaptureIntruderPhoto -> {
                            captureEngine.executeSilentCapture(this@LockScreenActivity) { _ ->
                                // Photo securely saved and encrypted
                            }
                        }

                        is LockScreenEffect.TriggerSirenAlarm -> {
                            alarmEngine.triggerAlarm()
                        }

                        is LockScreenEffect.StopSirenAlarm -> {
                            alarmEngine.stopAlarm()
                        }

                        is LockScreenEffect.UnlockDevice -> {
                            alarmEngine.stopAlarm()
                            val lockedPkg = currentTargetPackage ?: intent.getStringExtra(EXTRA_LOCKED_PACKAGE)
                            if (!lockedPkg.isNullOrEmpty()) {
                                com.security.lockguard.applock.AppLockManager(this@LockScreenActivity)
                                    .setAppTemporarilyUnlocked(lockedPkg)

                                // Explicitly launch and bring the locked target application to foreground
                                try {
                                    val launchIntent = packageManager.getLaunchIntentForPackage(lockedPkg)
                                    if (launchIntent != null) {
                                        launchIntent.addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        )
                                        startActivity(launchIntent)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("LockScreenActivity", "Error launching target app: ${e.message}")
                                }
                                finishAndRemoveTask()
                            } else {
                                val mainIntent = Intent(this@LockScreenActivity, MainActivity::class.java)
                                startActivity(mainIntent)
                                finishAndRemoveTask()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmEngine.stopAlarm()
    }

    companion object {
        const val EXTRA_LOCKED_PACKAGE = "extra_locked_package"
    }
}
