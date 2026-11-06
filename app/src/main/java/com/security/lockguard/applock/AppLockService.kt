package com.security.lockguard.applock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.security.lockguard.ui.LockScreenActivity

class AppLockService : Service() {

    companion object {
        private const val TAG = "AppLockService"
        private const val FOREGROUND_CHANNEL_ID = "lockguard_applock_channel"
        const val INTERCEPT_CHANNEL_ID = "lockguard_intercept_channel"
        private const val NOTIFICATION_ID = 2002
        const val INTERCEPT_NOTIF_ID = 2003
        private const val CHECK_INTERVAL_MS = 300L

        fun start(context: Context) {
            val intent = Intent(context, AppLockService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AppLockService: ${e.message}")
            }
        }
    }

    private lateinit var appLockManager: AppLockManager
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var lastForegroundPackage = ""
    private var lastInterceptedPackage = ""
    private var lastInterceptTime = 0L

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen turned off. Locking all session unlocked apps.")
                appLockManager.lockAllSessionApps()
                lastForegroundPackage = ""
                lastInterceptedPackage = ""
            }
        }
    }

    private val monitorRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkForegroundApp()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appLockManager = AppLockManager(this)
        createNotificationChannels()
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, filter)

        handler.post(monitorRunnable)
        Log.i(TAG, "AppLockService started with real-time app protection.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(monitorRunnable)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}
        Log.i(TAG, "AppLockService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkForegroundApp() {
        val currentPackage = getForegroundPackage() ?: return
        if (currentPackage.isEmpty()) return

        // Ignore if user is currently interacting with LockGuard itself
        if (currentPackage == packageName) return

        // If user navigated away from a previously unlocked app to a different app/launcher, lock it again
        if (lastForegroundPackage.isNotEmpty() && lastForegroundPackage != currentPackage) {
            appLockManager.lockApp(lastForegroundPackage)
        }
        lastForegroundPackage = currentPackage

        // If target app is in locked list and not currently temporarily unlocked, intercept
        if (appLockManager.isAppLocked(currentPackage)) {
            launchLockScreen(currentPackage)
        }
    }

    private fun launchLockScreen(targetPackage: String) {
        val now = System.currentTimeMillis()
        if (targetPackage == lastInterceptedPackage && (now - lastInterceptTime) < 1800) {
            return
        }
        lastInterceptedPackage = targetPackage
        lastInterceptTime = now

        Log.w(TAG, "Intercepting launch of locked app: $targetPackage")

        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(LockScreenActivity.EXTRA_LOCKED_PACKAGE, targetPackage)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            targetPackage.hashCode(),
            lockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Multi-tier intercept:
        // Tier 1: Try PendingIntent.send() (bypasses standard service launch restrictions if overlay permission is present)
        try {
            pendingIntent.send()
        } catch (e: Exception) {
            try {
                startActivity(lockIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Direct launch failed: ${ex.message}")
            }
        }

        // Tier 2: Post high-priority full-screen intent notification so Android OS forces the activity to top
        val fullScreenNotification = NotificationCompat.Builder(this, INTERCEPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("LockGuard Protection")
            .setContentText("Authentication required to access this application")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(INTERCEPT_NOTIF_ID, fullScreenNotification)
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val time = System.currentTimeMillis()

        // 1. Check UsageEvents for recent activity resumed / foreground events (last 4 seconds)
        try {
            val events = usageStatsManager.queryEvents(time - 4000, time)
            if (events != null) {
                val event = UsageEvents.Event()
                var lastPkg: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == 1 ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        lastPkg = event.packageName
                    }
                }
                if (!lastPkg.isNullOrEmpty()) {
                    return lastPkg
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UsageEvents error: ${e.message}")
        }

        // 2. Fallback to queryUsageStats
        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 15,
                time
            )
            if (!stats.isNullOrEmpty()) {
                val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
                if (mostRecent != null && mostRecent.lastTimeUsed > time - 20000) {
                    return mostRecent.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryUsageStats fallback error: ${e.message}")
        }

        return null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "LockGuard Background Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps LockGuard background application protection running"
            }
            manager.createNotificationChannel(foregroundChannel)

            val interceptChannel = NotificationChannel(
                INTERCEPT_CHANNEL_ID,
                "LockGuard App Intercept",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Triggers lockscreen authentication prompt when protected apps open"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(interceptChannel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("LockGuard Engine Active")
            .setContentText("App Locker shield running in background")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}

