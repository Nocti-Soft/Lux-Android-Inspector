package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectorNotificationModelTest {
    @Test
    fun `stopped session invites user to show controls`() {
        assertEquals(
            InspectorNotificationModel("Tap to show inspector controls", false),
            buildInspectorNotificationModel(false, MeasureMode.SIZE),
        )
    }

    @Test
    fun `active session shows active mode and stop action`() {
        assertEquals(
            InspectorNotificationModel("Gap active", true),
            buildInspectorNotificationModel(true, MeasureMode.GAP),
        )
    }

    @Test
    fun `posting notification reports failures without propagating`() {
        var posted = false
        val failure = IllegalStateException("notify failed")
        val reported = mutableListOf<Throwable>()

        postInspectorNotification({ posted = true }, reported::add)
        postInspectorNotification({ throw failure }, reported::add)

        assertTrue(posted)
        assertEquals(1, reported.size)
        assertSame(failure, reported.single())
    }
}
