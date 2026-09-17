package com.noctisoft.layoutmeasurement

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color as ComposeColor

/** Reads public Compose tooling/semantics; keeps no node references in the returned snapshot. */
internal object ComposeColorCapture {
    data class ModifierColors(val background: ColorValue, val border: ColorValue)

    fun capture(node: SemanticsNode): ComponentColors {
        val surface = safelySurface {
            val modifiers = node.layoutInfo.getModifierInfo()
            if (modifiers.size > 128) return@safelySurface unknownSurface("Too many modifiers")
            modifierColors(modifiers.mapNotNull { it.modifier as? InspectableValue })
        }
        val text = safelyText { textColors(node) }
        return ComponentColors(text.value, surface.background, surface.border, text.origin)
    }

    private data class CapturedText(val value: ColorValue, val origin: String? = null)

    private fun textColors(node: SemanticsNode): CapturedText {
        val own = ownTextColor(node)
        if (own != ColorValue.NotApplicable) return CapturedText(own)
        val pending = ArrayDeque<SemanticsNode>()
        pending.addAll(node.children)
        val values = mutableListOf<ColorValue>()
        var visited = 0
        while (pending.isNotEmpty()) {
            if (++visited > 64 || pending.size > 128) {
                return CapturedText(ColorValue.Unavailable("Descendant text exceeds inspection limit"), "Descendant text")
            }
            val child = pending.removeFirst()
            if (child.boundsInWindow.width <= 0 || child.boundsInWindow.height <= 0) continue
            val color = ownTextColor(child)
            if (color == ColorValue.NotApplicable) pending.addAll(child.children) else values.add(color)
        }
        if (values.isEmpty()) return CapturedText(ColorValue.NotApplicable)
        val noun = if (values.size == 1) "node" else "nodes"
        return CapturedText(combine(values), "Descendant text (${values.size} $noun)")
    }

    private fun ownTextColor(node: SemanticsNode): ColorValue {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (action == null) {
            return if (node.config.getOrNull(SemanticsProperties.Text) != null ||
                node.config.getOrNull(SemanticsProperties.EditableText) != null
            ) ColorValue.Unavailable("Text layout metadata unavailable") else ColorValue.NotApplicable
        }
        val layouts = mutableListOf<TextLayoutResult>()
        if (!action(layouts) || layouts.isEmpty()) return ColorValue.Unavailable("Text layout not ready")
        if (layouts.size > 8) return ColorValue.Unavailable("Too many text layouts")
        return combine(layouts.map { textStyleColors(it.layoutInput.text, it.layoutInput.style) })
    }

    internal fun modifierColors(modifiers: List<InspectableValue>): ModifierColors {
        val backgrounds = mutableListOf<ColorValue>()
        val borders = mutableListOf<ColorValue>()
        for (modifier in modifiers) {
            val name = (modifier.nameFallback ?: modifier.javaClass.simpleName).lowercase()
            if (name in setOf("drawbehind", "drawwithcache", "drawwithcontent", "paint") ||
                name.contains("drawbehind") || name.contains("drawcache") || name.contains("drawbackground") ||
                name.contains("drawwith") || name.contains("painter")
            ) return unknownSurface("Custom drawing; color cannot be attributed")
            val target = when {
                name.contains("background") -> backgrounds
                name.contains("border") -> borders
                else -> continue
            }
            val elements = modifier.inspectableElements.take(65).toList()
            if (elements.size > 64) {
                target.add(ColorValue.Unavailable("Too many modifier properties"))
                continue
            }
            target.add(modifierColor(elements.associate { it.name to it.value }))
        }
        return ModifierColors(combine(backgrounds), combine(borders))
    }

    private fun modifierColor(properties: Map<String, Any?>): ColorValue {
        val brush = properties["brush"]
        if (brush != null && brush !is SolidColor) return ColorValue.Unavailable("Gradient or custom brush")
        val color = (properties["color"] as? ComposeColor)?.takeIf { it.isSpecified }
            ?: (brush as? SolidColor)?.value
            ?: return ColorValue.Unavailable("Color metadata unavailable; restart the debug app")
        if (!color.isSpecified) return ColorValue.Unavailable("Unspecified color")
        val alpha = (properties["alpha"] as? Number)?.toFloat() ?: 1f
        if (!alpha.isFinite()) return ColorValue.Unavailable("Invalid color alpha")
        return ColorValue.Solid(color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f)).toArgb())
    }

    internal fun textStyleColors(text: AnnotatedString, style: TextStyle): ColorValue {
        if (text.length > 16_384 || text.spanStyles.size > 128) return ColorValue.Unavailable("Styled text exceeds inspection limit")
        val base = spanColor(style.toSpanStyle())
        if (text.isEmpty()) return base ?: ColorValue.Unavailable("Unspecified text color")
        val points = (listOf(0, text.length) + text.spanStyles.flatMap {
            listOf(it.start.coerceIn(0, text.length), it.end.coerceIn(0, text.length))
        }).distinct().sorted()
        return combine(points.zipWithNext().map { (start, end) ->
            var value = base
            for (span in text.spanStyles) {
                if (span.start < end && span.end > start) spanColor(span.item)?.let { value = it }
            }
            value ?: ColorValue.Unavailable("Unspecified text color")
        })
    }

    private fun spanColor(style: SpanStyle): ColorValue? {
        val brush = style.brush
        if (brush != null && brush !is SolidColor) return ColorValue.Unavailable("Gradient text brush")
        if (brush is SolidColor) {
            val alpha = style.alpha.takeIf { it.isFinite() } ?: 1f
            return ColorValue.Solid(brush.value.copy(alpha = brush.value.alpha * alpha.coerceIn(0f, 1f)).toArgb())
        }
        return style.color.takeIf { it.isSpecified }?.let { ColorValue.Solid(it.toArgb()) }
    }

    private fun combine(values: List<ColorValue>): ColorValue {
        values.filterIsInstance<ColorValue.Unavailable>().firstOrNull()?.let { return it }
        return ColorValue.fromColors(values.flatMap {
            when (it) {
                is ColorValue.Solid -> listOf(it.argb)
                is ColorValue.Multiple -> it.argb
                else -> emptyList()
            }
        })
    }

    private fun unknownSurface(reason: String) = ModifierColors(ColorValue.Unavailable(reason), ColorValue.Unavailable(reason))

    private inline fun safelySurface(block: () -> ModifierColors): ModifierColors = try {
        block()
    } catch (_: Exception) {
        unknownSurface("Modifier metadata unavailable")
    } catch (_: LinkageError) {
        unknownSurface("Compose tooling API unavailable")
    }

    private inline fun safelyText(block: () -> CapturedText): CapturedText = try {
        block()
    } catch (_: Exception) {
        CapturedText(ColorValue.Unavailable("Text metadata unavailable"))
    } catch (_: LinkageError) {
        CapturedText(ColorValue.Unavailable("Compose text API unavailable"))
    }
}
