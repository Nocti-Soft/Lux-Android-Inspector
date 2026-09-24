package com.noctisoft.layoutmeasurement

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.GenericFontFamily
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** Public text-layout metadata only. Returned snapshots do not retain nodes or user text. */
internal object ComposeTextCapture {
    fun capture(node: SemanticsNode): TextProperties? = try {
        val own = ownText(node)
        if (own != null) own else descendants(node)
    } catch (_: Exception) {
        TextProperties.unavailable("Text layout metadata unavailable")
    } catch (_: LinkageError) {
        TextProperties.unavailable("Compose text API unavailable")
    }

    private fun descendants(node: SemanticsNode): TextProperties? {
        val pending = ArrayDeque<SemanticsNode>()
        pending.addAll(node.children)
        val found = mutableListOf<TextProperties>()
        var visited = 0
        while (pending.isNotEmpty()) {
            if (++visited > 64 || pending.size > 128) {
                return TextProperties.unavailable("Descendant text exceeds inspection limit").copy(origin = "Descendant text")
            }
            val child = pending.removeFirst()
            if (child.boundsInWindow.width <= 0f || child.boundsInWindow.height <= 0f) continue
            val properties = ownText(child)
            if (properties == null) pending.addAll(child.children) else found.add(properties)
        }
        if (found.isEmpty()) return null
        val noun = if (found.size == 1) "node" else "nodes"
        return TextProperties.combine(found).copy(origin = "Descendant text (${found.size} $noun)")
    }

    private fun ownText(node: SemanticsNode): TextProperties? {
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        if (action == null) {
            return if (node.config.getOrNull(SemanticsProperties.Text) != null ||
                node.config.getOrNull(SemanticsProperties.EditableText) != null
            ) TextProperties.unavailable("Text layout metadata unavailable") else null
        }
        val layouts = mutableListOf<TextLayoutResult>()
        if (!action(layouts) || layouts.isEmpty()) return TextProperties.unavailable("Text layout not ready")
        if (layouts.size > 8) return TextProperties.unavailable("Too many text layouts")
        return TextProperties.combine(layouts.map {
            val input = it.layoutInput
            textStyles(input.text, input.style, input.density, input.layoutDirection)
        })
    }

    internal fun textStyles(text: AnnotatedString, style: TextStyle, density: Density, direction: LayoutDirection): TextProperties {
        if (text.length > 16_384 || text.spanStyles.size > 128) return TextProperties.unavailable("Styled text exceeds inspection limit")
        val base = resolveDefaults(style, direction)
        if (text.isEmpty()) return properties(base, density)
        val points = (listOf(0, text.length) + text.spanStyles.flatMap {
            listOf(it.start.coerceIn(0, text.length), it.end.coerceIn(0, text.length))
        }).distinct().sorted()
        return TextProperties.combine(points.zipWithNext().map { (start, end) ->
            var merged = base
            text.spanStyles.forEach { span ->
                if (span.start < end && span.end > start) merged = merged.merge(span.item)
            }
            properties(merged, density)
        })
    }

    private fun properties(style: TextStyle, density: Density): TextProperties {
        val size = if (style.fontSize.isSp) {
            TextPropertyFormat.size(style.fontSize.value, with(density) { style.fontSize.toPx() })
        } else TextPropertyValue.Unavailable("Relative em font size cannot be resolved")
        val family = when (val configured = style.fontFamily) {
            is GenericFontFamily -> TextPropertyValue.Known("${configured.name.take(128)} (configured)")
            FontFamily.Default -> TextPropertyValue.Known("Default (configured)")
            else -> TextPropertyValue.Unavailable("Custom font family name not exposed")
        }
        val spacing = when {
            style.letterSpacing.isSp -> TextPropertyFormat.spacing(style.letterSpacing.value, "sp")
            style.letterSpacing.isEm -> TextPropertyFormat.spacing(style.letterSpacing.value, "em")
            else -> TextPropertyValue.Unavailable("Letter spacing unspecified")
        }
        return TextProperties(
            fontSize = size,
            fontFamily = family,
            fontWeight = style.fontWeight?.let { TextPropertyFormat.weight(it.weight) }
                ?: TextPropertyValue.Unavailable("Font weight unspecified"),
            fontStyle = TextPropertyValue.Known(if (style.fontStyle == FontStyle.Italic) "Italic" else "Normal"),
            letterSpacing = spacing,
        )
    }
}
