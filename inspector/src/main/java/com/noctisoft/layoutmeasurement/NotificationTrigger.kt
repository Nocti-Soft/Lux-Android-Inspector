package com.noctisoft.layoutmeasurement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** Ongoing low-priority notification whose tap toggles the inspector. */
object NotificationTrigger {

    private const val CHANNEL_ID = "layout_inspector"
    private const val NOTIFICATION_ID = 0x1A1
    const val ACTION_TOGGLE = "com.noctisoft.layoutmeasurement.ACTION_TOGGLE"

    fun show(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Layout Inspector",
                    NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_TOGGLE).setPackage(context.packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentTitle("Layout Inspector")
            .setContentText("Tap to toggle measurement overlay")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
        // Silently no-op when POST_NOTIFICATIONS not granted (API 33+).
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching { nm.notify(NOTIFICATION_ID, notification) }
        }
    }
}

class ToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationTrigger.ACTION_TOGGLE) InspectorController.revealControls(RevealSource.NOTIFICATION)
    }
}
