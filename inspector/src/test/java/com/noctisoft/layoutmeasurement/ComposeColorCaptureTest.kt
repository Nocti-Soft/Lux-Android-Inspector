package com.noctisoft.layoutmeasurement

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.InspectorValueInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import org.junit.Assert.*
import org.junit.Test

class ComposeColorCaptureTest {
    private fun metadata(name: String, values: Map<String, Any?>): InspectableValue =
        object : InspectorValueInfo({ this.name = name; values.forEach { (key, value) -> properties[key] = value } }) {}

    @Test fun `solid background and border modifier colors are independent`() {
        val result = ComposeColorCapture.modifierColors(listOf(
            metadata("background", mapOf("color" to Color.Red)),
            metadata("border", mapOf("color" to Color.Blue)),
        ))
        assertEquals(ColorValue.Solid(Color.Red.toArgb()), result.background)
        assertEquals(ColorValue.Solid(Color.Blue.toArgb()), result.border)
    }

    @Test fun `solid brush applies explicit modifier alpha`() {
        val result = ComposeColorCapture.modifierColors(listOf(
            metadata("background", mapOf("brush" to SolidColor(Color.Red), "alpha" to 0.5f)),
        ))
        assertEquals(ColorValue.Solid(Color.Red.copy(alpha = 0.5f).toArgb()), result.background)
    }

    @Test fun `gradient is unavailable instead of guessed as the first stop`() {
        val result = ComposeColorCapture.modifierColors(listOf(
            metadata("background", mapOf("brush" to Brush.linearGradient(listOf(Color.Red, Color.Blue)))),
        ))
        assertTrue(result.background is ColorValue.Unavailable)
    }

    @Test fun `missing background metadata is distinct from no background modifier`() {
        val missing = ComposeColorCapture.modifierColors(listOf(metadata("BackgroundElement", emptyMap())))
        assertTrue(missing.background is ColorValue.Unavailable)
        assertEquals(ColorValue.None, ComposeColorCapture.modifierColors(emptyList()).background)
    }

    @Test fun `transparent and several modifier layers retain their distinct values`() {
        val result = ComposeColorCapture.modifierColors(listOf(
            metadata("background", mapOf("color" to Color.Transparent)),
            metadata("background", mapOf("color" to Color.Green)),
        ))
        assertEquals(ColorValue.fromColors(listOf(Color.Transparent.toArgb(), Color.Green.toArgb())), result.background)
    }

    @Test fun `unsupported custom drawing does not claim there is no border`() {
        val result = ComposeColorCapture.modifierColors(listOf(metadata("drawBehind", emptyMap())))
        assertTrue(result.background is ColorValue.Unavailable)
        assertTrue(result.border is ColorValue.Unavailable)
    }

    @Test fun `text style reads base and span colors without adding unused base color`() {
        val text = AnnotatedString("AB", listOf(
            AnnotatedString.Range(SpanStyle(color = Color.Red), 0, 1),
            AnnotatedString.Range(SpanStyle(color = Color.Blue), 1, 2),
        ))
        assertEquals(ColorValue.fromColors(listOf(Color.Red.toArgb(), Color.Blue.toArgb())),
            ComposeColorCapture.textStyleColors(text, TextStyle(color = Color.Green)))
    }

    @Test fun `unspecified text color is not falsely turned into black`() {
        assertTrue(ComposeColorCapture.textStyleColors(AnnotatedString("A"), TextStyle.Default) is ColorValue.Unavailable)
    }

    @Test fun `gradient text style does not report one solid color`() {
        val style = TextStyle(brush = Brush.linearGradient(listOf(Color.Red, Color.Blue)))
        assertTrue(ComposeColorCapture.textStyleColors(AnnotatedString("A"), style) is ColorValue.Unavailable)
    }
}
