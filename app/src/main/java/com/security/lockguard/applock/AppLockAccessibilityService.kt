package com.security.lockguard.applock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.security.lockguard.ui.LockScreenActivity

class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockAccessibility"
    }

    private lateinit var appLockManager: AppLockManager
    private var lastInterceptedPackage = ""
    private var lastInterceptTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        appLockManager = AppLockManager(this)
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 50
        }
        Log.i(TAG, "AppLockAccessibilityService connected and ready for zero-latency intercept.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName.isEmpty() || packageName == this.packageName) return

        if (::appLockManager.isInitialized && appLockManager.isAppLocked(packageName)) {
            val now = System.currentTimeMillis()
            if (packageName == lastInterceptedPackage && (now - lastInterceptTime) < 1800) {
                return
            }
            lastInterceptedPackage = packageName
            lastInterceptTime = now

            Log.w(TAG, "Accessibility intercepted launch of protected app: $packageName")
            val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(LockScreenActivity.EXTRA_LOCKED_PACKAGE, packageName)
            }
            try {
                startActivity(lockIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start lock screen from accessibility service: ${e.message}")
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AppLockAccessibilityService interrupted.")
    }
}

