package com.vasilisneo.trackstar.data.workout

// The rest timer running out: the notification sound and a short buzz, unless "Rest Timer Sound"
// is off in Settings. Follows the phone's ringer — silent on silent, buzz only on vibrate.
// iOS: ActiveSessionViewModel.finishRest (system sound 1013 + vibrate).

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator

object RestAlert {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun play() {
        if (!::appContext.isInitialized) return
        val prefs = appContext.getSharedPreferences("trackstar_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("restTimerSound", true)) return

        val ringerMode = appContext.getSystemService(AudioManager::class.java)?.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(appContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }
        }
        if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
            runCatching {
                appContext.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 250), -1))
            }
        }
    }
}
