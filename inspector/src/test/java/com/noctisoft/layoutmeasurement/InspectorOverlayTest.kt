package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InspectorOverlayTest {
    @After
    fun resetController() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `overlay composes a full window canvas below an initially hidden floating control`() {
        val overlay = overlay()

        assertEquals(InspectorOverlay.TAG, overlay.tag)
        assertEquals(2, overlay.childCount)
        assertEquals("MeasureCanvas", overlay.getChildAt(0).javaClass.simpleName)
        assertTrue(overlay.getChildAt(1) is FloatingInspectorControl)
        assertEquals(View.GONE, overlay.getChildAt(0).visibility)
        assertEquals(View.GONE, overlay.getChildAt(1).visibility)
        assertEquals(0, overlay.paddingLeft)
        assertEquals(0, overlay.paddingTop)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, overlay.getChildAt(0).layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, overlay.getChildAt(0).layoutParams.height)
    }

    @Test
    fun `hide preserves measurement while stop from floating controls clears the active session`() {
        val overlay = overlay()
        InspectorController.revealControls(RevealSource.SHAKE)
        button(overlay, "Layout inspector controls").performClick()
        button(overlay, "Size").performClick()

        assertTrue(InspectorController.isActive)
        assertEquals(MeasureMode.SIZE, InspectorController.mode)

        button(overlay, "Layout inspector controls").performClick()
        button(overlay, "Hide Inspector").performClick()
        assertTrue(InspectorController.isActive)
        assertEquals(FloatingControlState.HIDDEN, InspectorController.controlState)

        InspectorController.revealControls(RevealSource.NOTIFICATION)
        button(overlay, "Layout inspector controls").performClick()
        button(overlay, "Settings").performClick()
        button(overlay, "Stop Inspector").performClick()

        assertFalse(InspectorController.isActive)
        assertEquals(View.GONE, overlay.getChildAt(0).visibility)
    }

    private fun overlay(): InspectorOverlay {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return InspectorOverlay(activity).also {
            activity.addContentView(
                it,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            it.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
            )
            it.layout(0, 0, 320, 480)
        }
    }

    private fun button(root: View, label: String): View = find(root) {
        (it is TextView && it.contentDescription == label) ||
            (it is Button && it.text == label)
    } ?: error("Missing control: $label")

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index -> find(root.getChildAt(index), predicate)?.let { return it } }
        return null
    }
}
