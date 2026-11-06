package com.security.lockguard.intruder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class IntruderAlarmEngine(private val context: Context) {

    companion object {
        private const val TAG = "LockGuard_AlarmEngine"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var isAlarmActive = false

    fun triggerAlarm() {
        if (isAlarmActive) return
        isAlarmActive = true

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.9).toInt(), 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            triggerHapticPulse()
            Log.w(TAG, "Intruder Alert Siren and Haptic Alarm Engaged.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start intruder alarm tone: ${e.message}", e)
        }
    }

    private fun triggerHapticPulse() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(pattern, 0)
        }
    }

    fun stopAlarm() {
        if (!isAlarmActive) return
        isAlarmActive = false

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
            mediaPlayer = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.cancel()
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
            Log.i(TAG, "Intruder Alert Siren Disengaged.")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm: ${e.message}", e)
        }
    }

    fun isAlerting(): Boolean = isAlarmActive
}

