package com.noctisoft.layoutmeasurement

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationTriggerTest {
    @Test
    fun `recovery requires an enabled inspector channel on API 26 and above`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel("layout_inspector")

        assertFalse(NotificationTrigger.isRecoveryAvailable(context))

        manager.createNotificationChannel(
            NotificationChannel("layout_inspector", "Layout Inspector", NotificationManager.IMPORTANCE_LOW),
        )
        assertTrue(NotificationTrigger.isRecoveryAvailable(context))

        manager.deleteNotificationChannel("layout_inspector")
        manager.createNotificationChannel(
            NotificationChannel("layout_inspector", "Layout Inspector", NotificationManager.IMPORTANCE_NONE),
        )
        assertFalse(NotificationTrigger.isRecoveryAvailable(context))
    }
}
