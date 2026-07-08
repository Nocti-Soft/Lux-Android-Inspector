package dev.pinij.inspector

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorControllerTest {

    @After fun reset() {
        InspectorController.isActive = false
        InspectorController.mode = MeasureMode.SIZE
    }

    @Test fun `toggle flips isActive`() {
        InspectorController.isActive = false
        InspectorController.toggle()
        assertEquals(true, InspectorController.isActive)
        InspectorController.toggle()
        assertEquals(false, InspectorController.isActive)
    }

    @Test fun `listener fires on activation and mode change`() {
        var fired = 0
        val l = { fired++; Unit }
        InspectorController.addListener(l)
        InspectorController.isActive = true
        InspectorController.mode = MeasureMode.GAP
        InspectorController.removeListener(l)
        InspectorController.isActive = false // must not fire
        assertEquals(2, fired)
    }

    @Test fun `setting same value does not notify`() {
        var fired = 0
        val l = { fired++; Unit }
        InspectorController.isActive = false
        InspectorController.addListener(l)
        InspectorController.isActive = false
        InspectorController.removeListener(l)
        assertEquals(0, fired)
    }
}
