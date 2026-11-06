package com.security.lockguard.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.security.lockguard.intruder.IntruderAlarmEngine
import com.security.lockguard.intruder.IntruderCaptureEngine
import com.security.lockguard.service.LockGuardForegroundService
import com.security.lockguard.ui.LockScreenActivity

/**
 * Anti-Theft SIM Card Change Detector.
 * Triggered automatically when the physical SIM card is removed, replaced, or tampered with.
 */
class SimChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "android.intent.action.SIM_STATE_CHANGED") return

        val stateExtra = intent.getStringExtra("ss") ?: return
        Log.w(TAG, "SIM State Change Detected: $stateExtra")

        // Check if SIM is ABSENT or LOCKED
        if (stateExtra.equals("ABSENT", ignoreCase = true) ||
            stateExtra.equals("LOCKED", ignoreCase = true) ||
            stateExtra.equals("CARD_IO_ERROR", ignoreCase = true)) {

            Log.e(TAG, "SECURITY ALERT: SIM Card removed or unauthorized state!")

            // 1. Force screen lock
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_SIM_ALERT", true)
            }
            context.startActivity(lockIntent)

            // 2. Trigger high-priority foreground monitoring service
            val serviceIntent = Intent(context, LockGuardForegroundService::class.java).apply {
                action = LockGuardForegroundService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // 3. Activate Anti-Theft siren
            try {
                val alarmEngine = IntruderAlarmEngine(context)
                alarmEngine.triggerAlarm()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start siren: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SimChangeReceiver"
    }
}

