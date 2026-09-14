package com.noctisoft.layoutmeasurement

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationTrigger {
    private const val CHANNEL_ID = "layout_inspector"
    private const val NOTIFICATION_ID = 0x1A1
    const val ACTION_SHOW = "com.noctisoft.layoutmeasurement.ACTION_SHOW"
    const val ACTION_STOP = "com.noctisoft.layoutmeasurement.ACTION_STOP"

    private var appContext: Context? = null
    private val controllerListener: () -> Unit = {
        appContext?.let(::show)
    }

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        InspectorController.addListener(controllerListener)
        show(context)
    }

    fun isRecoveryAvailable(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun show(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Layout Inspector", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (!isRecoveryAvailable(app)) return

        val model = buildInspectorNotificationModel(InspectorController.isActive, InspectorController.mode)
        val showIntent = PendingIntent.getBroadcast(
            app,
            1,
            Intent(ACTION_SHOW).setPackage(app.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_crop)
            .setContentTitle("Layout Inspector")
            .setContentText(model.contentText)
            .setOngoing(true)
            .setContentIntent(showIntent)

        if (model.showStopAction) {
            val stopIntent = PendingIntent.getBroadcast(
                app,
                2,
                Intent(ACTION_STOP).setPackage(app.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Stop Inspector", stopIntent)
        }

        postInspectorNotification(
            post = { manager.notify(NOTIFICATION_ID, builder.build()) },
            reportFailure = { throwable ->
                Log.w("LayoutInspector", "Failed to post inspector notification", throwable)
            },
        )
    }
}

internal fun postInspectorNotification(
    post: () -> Unit,
    reportFailure: (Throwable) -> Unit,
) {
    runCatching(post).onFailure(reportFailure)
}

class InspectorActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationTrigger.ACTION_SHOW -> InspectorController.revealControls(RevealSource.NOTIFICATION)
            NotificationTrigger.ACTION_STOP -> InspectorController.stopInspection()
        }
        NotificationTrigger.show(context)
    }
}
