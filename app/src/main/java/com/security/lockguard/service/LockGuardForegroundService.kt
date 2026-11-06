package com.security.lockguard.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.security.lockguard.LockGuardApp
import com.security.lockguard.ui.LockScreenActivity

class LockGuardForegroundService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "ACTION_START_LOCKGUARD_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_LOCKGUARD_SERVICE"
    }

    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        return START_STICKY
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_SCREEN_OFF -> {
                        // Present high-security lock screen HUD
                        val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        }
                        context.startActivity(lockIntent)
                    }
                }
            }
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, LockScreenActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, LockGuardApp.INTRUSION_CHANNEL_ID)
            .setContentTitle("LockGuard Active")
            .setContentText("Intrusion prevention & device lock active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let {
            unregisterReceiver(it)
            screenReceiver = null
        }
    }
}

