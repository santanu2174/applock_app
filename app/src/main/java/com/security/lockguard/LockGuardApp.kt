package com.security.lockguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class LockGuardApp : Application() {

    companion object {
        const val INTRUSION_CHANNEL_ID = "lockguard_security_channel"
        const val INTRUSION_CHANNEL_NAME = "LockGuard Intrusion Monitor"
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INTRUSION_CHANNEL_ID,
                INTRUSION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Monitors unauthorized intrusion attempts and device lock security"
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}

