package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
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
}
