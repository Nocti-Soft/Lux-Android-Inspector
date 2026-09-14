package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InspectorInitializerTest {
    @After
    fun resetController() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `initialization restores placement hidden then keeps one overlay through activity recreation`() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("layout_measurement_inspector", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val placement = FloatingPlacement(0.1f, 0.8f, DockSide.RIGHT)
        InspectorPlacementStore(application).save(placement)
        InspectorController.selectMode(MeasureMode.GAP)
        InspectorController.revealControls(RevealSource.SHAKE)

        val initializer = InspectorInitializer()
        initializer.create(application)
        initializer.create(application)

        assertFalse(InspectorController.isActive)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)
        assertEquals(placement, InspectorController.placement)

        val first = Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(1, overlays(first.get()).size)
        first.pause().stop().destroy()

        val recreated = Robolectric.buildActivity(Activity::class.java).setup().get()
        assertEquals(1, overlays(recreated).size)
        assertNotNull(overlays(recreated).single())

        InspectorController.revealControls(RevealSource.SHAKE)
        assertEquals(FloatingControlState.DOCKED_RIGHT, InspectorController.controlState)
    }

    private fun overlays(activity: Activity): List<View> {
        val decor = activity.window.decorView as ViewGroup
        return (0 until decor.childCount).map(decor::getChildAt)
            .filter { it.tag == InspectorOverlay.TAG }
    }
}
