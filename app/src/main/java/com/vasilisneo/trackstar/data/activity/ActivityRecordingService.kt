package com.vasilisneo.trackstar.data.activity

// Foreground service that keeps an activity recording alive with the screen off (Android stops
// background location otherwise). It only holds the "Recording" notification — ActivityRecorder does
// the location work. Started on Start, stopped on Finish / Discard.

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vasilisneo.trackstar.MainActivity
import com.vasilisneo.trackstar.R
import com.vasilisneo.trackstar.ui.i18n.tr

class ActivityRecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("Activity recording"), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val type = ActivityRecorder.snapshot.value.type
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(tr("Recording ${type.label.lowercase()}").replaceFirstChar { it.uppercase() })
            .setContentText(tr("Trackstar is recording your route."))
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "activity_recording"
        private const val NOTIFICATION_ID = 4711
    }
}
