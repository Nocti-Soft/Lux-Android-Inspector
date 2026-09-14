package com.noctisoft.layoutmeasurement

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorControllerTest {

    @After
    fun reset() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `selectMode starts session and switches directly`() {
        InspectorController.selectMode(MeasureMode.SIZE)
        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.SIZE, InspectorController.mode)

        InspectorController.selectMode(MeasureMode.GAP)
        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.GAP, InspectorController.mode)
    }

    @Test
    fun `hide controls does not stop active session`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.selectMode(MeasureMode.RULER)
        InspectorController.hideControls()

        assertEquals(true, InspectorController.isActive)
        assertEquals(MeasureMode.RULER, InspectorController.mode)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)
    }

    @Test
    fun `stop session does not force controls hidden`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.selectMode(MeasureMode.BOUNDS)
        InspectorController.stopInspection()

        assertEquals(false, InspectorController.isActive)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
    }

    @Test
    fun `dock and tap restore usable control`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.dock(DockSide.RIGHT)
        assertEquals(FloatingControlState.DOCKED_RIGHT, InspectorController.controlState)

        InspectorController.undockControls()
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
        assertEquals(DockSide.NONE, InspectorController.placement.dockSide)
    }

    @Test
    fun `startup hidden reveal restores persisted dock`() {
        InspectorController.restorePlacement(
            FloatingPlacement(0.9f, 0.4f, DockSide.LEFT)
        )
        InspectorController.hideControlsForStartup()
        InspectorController.revealControls(RevealSource.SHAKE)

        assertEquals(FloatingControlState.DOCKED_LEFT, InspectorController.controlState)
    }

    @Test
    fun `explicit hide ignores shake and notification restores`() {
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.hideControls()

        InspectorController.revealControls(RevealSource.SHAKE)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)

        InspectorController.revealControls(RevealSource.NOTIFICATION)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
    }

    @Test
    fun `same state does not notify twice`() {
        var fired = 0
        val listener = { fired++; Unit }
        InspectorController.addListener(listener)

        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.collapseControls()

        InspectorController.removeListener(listener)
        assertEquals(1, fired)
    }
}
