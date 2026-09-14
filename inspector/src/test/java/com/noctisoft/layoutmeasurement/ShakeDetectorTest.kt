package com.noctisoft.layoutmeasurement

import android.hardware.SensorEvent
import org.robolectric.shadow.api.Shadow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {
    @After
    fun resetController() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `sensor shake reveals startup hidden controls but not deliberately hidden controls`() {
        val detector = ShakeDetector { InspectorController.revealControls(RevealSource.SHAKE) }

        detector.onSensorChanged(shakeEvent())
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)

        InspectorController.hideControls()
        ShakeDetector { InspectorController.revealControls(RevealSource.SHAKE) }.onSensorChanged(shakeEvent())
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)
    }

    private fun shakeEvent(): SensorEvent = Shadow.newInstanceOf(SensorEvent::class.java).also { event ->
        SensorEvent::class.java.getDeclaredField("values").apply { isAccessible = true }
            .set(event, floatArrayOf(30f, 0f, 0f))
    }
}
