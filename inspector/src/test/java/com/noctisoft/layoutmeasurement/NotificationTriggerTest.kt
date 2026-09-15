package com.noctisoft.layoutmeasurement

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotificationTriggerTest {
    @After
    fun resetInspector() {
        val context = RuntimeEnvironment.getApplication()
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorPlacementStore(context).save(FloatingPlacement())
    }

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

    @Test
    @Config(sdk = [24], application = Application::class)
    fun `recovery is available before API 26 when app notifications are enabled`() {
        assertTrue(NotificationTrigger.isRecoveryAvailable(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun `recovery is unavailable when app-wide notifications are disabled`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("layout_inspector", "Layout Inspector", NotificationManager.IMPORTANCE_LOW),
        )
        Shadows.shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(NotificationTrigger.isRecoveryAvailable(context))
    }

    @Test
    fun `receiver undocks and persists left placement without an overlay`() {
        assertNotificationShowPersistsUndockedPlacement(DockSide.LEFT, 0.2f, 0.8f)
    }

    @Test
    @Config(sdk = [24], application = Application::class)
    fun `receiver undocks and persists right placement without an overlay on API 24`() {
        assertNotificationShowPersistsUndockedPlacement(DockSide.RIGHT, 0.7f, 0.3f)
    }

    @Test
    fun `receiver dispatches Show and Stop to the inspector controller`() {
        val context = RuntimeEnvironment.getApplication()
        InspectorController.hideControlsForStartup()
        InspectorActionReceiver().onReceive(context, android.content.Intent(NotificationTrigger.ACTION_SHOW))
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)

        InspectorController.selectMode(MeasureMode.SIZE)
        InspectorActionReceiver().onReceive(context, android.content.Intent(NotificationTrigger.ACTION_STOP))
        assertFalse(InspectorController.isActive)
    }

    private fun assertNotificationShowPersistsUndockedPlacement(
        dockSide: DockSide,
        xFraction: Float,
        yFraction: Float,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val dockedPlacement = FloatingPlacement(xFraction, yFraction, dockSide)
        InspectorPlacementStore(context).save(dockedPlacement)
        InspectorController.restorePlacement(dockedPlacement)
        InspectorController.dock(dockSide)
        InspectorController.selectMode(MeasureMode.GAP)

        InspectorActionReceiver().onReceive(context, android.content.Intent(NotificationTrigger.ACTION_SHOW))

        val expectedPlacement = FloatingPlacement(xFraction, yFraction, DockSide.NONE)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
        assertEquals(expectedPlacement, InspectorController.placement)
        assertEquals(expectedPlacement, InspectorPlacementStore(context).load())
        assertTrue(InspectorController.isActive)
        assertEquals(MeasureMode.GAP, InspectorController.mode)
    }
}
