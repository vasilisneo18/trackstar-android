package com.vasilisneo.trackstar.data.workout

// Sounds in the live workout, following the phone's ringer — silent on silent, buzz only on vibrate.
//  - restOver: the rest timer ran out — notification sound + a double buzz, unless "Rest Timer
//    Sound" is off in Settings (iOS: finishRest, system sound 1013 + vibrate).
//  - timedSetStart: the get-ready countdown hit zero, go — a beep + a buzz (iOS: sound 1322 + vibrate).
//  - timedSetDone: a timed set (plank etc.) ran its full length — a double beep (iOS: sound 1007).

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

object WorkoutSounds {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun restOver() {
        if (!::appContext.isInitialized) return
        val prefs = appContext.getSharedPreferences("trackstar_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("restTimerSound", true)) return

        if (ringerMode() == AudioManager.RINGER_MODE_NORMAL) {
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
        buzz(longArrayOf(0, 250, 120, 250))
    }

    fun timedSetStart() {
        if (!::appContext.isInitialized) return
        tone(ToneGenerator.TONE_PROP_BEEP, 200)
        buzz(longArrayOf(0, 300))
    }

    fun timedSetDone() {
        if (!::appContext.isInitialized) return
        tone(ToneGenerator.TONE_PROP_BEEP2, 400)
    }

    private fun ringerMode(): Int? = appContext.getSystemService(AudioManager::class.java)?.ringerMode

    private fun tone(type: Int, durationMs: Int) {
        if (ringerMode() != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            generator.startTone(type, durationMs)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, durationMs + 300L)
        }
    }

    private fun buzz(pattern: LongArray) {
        if (ringerMode() == AudioManager.RINGER_MODE_SILENT) return
        runCatching {
            appContext.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
