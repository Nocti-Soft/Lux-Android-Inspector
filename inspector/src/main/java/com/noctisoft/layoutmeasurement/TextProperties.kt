package com.noctisoft.layoutmeasurement

import java.util.Locale

/** Immutable typography values. No text content, Views, Typefaces or layout objects are retained. */
data class TextProperties(
    val fontSize: TextPropertyValue,
    val fontFamily: TextPropertyValue,
    val fontWeight: TextPropertyValue,
    val fontStyle: TextPropertyValue,
    val letterSpacing: TextPropertyValue,
    /** Non-null when the selected Compose container exposes text through its descendants. */
    val origin: String? = null,
) {
    internal companion object {
        fun unavailable(reason: String) = TextPropertyValue.Unavailable(reason).let {
            TextProperties(it, it, it, it, it)
        }

        fun combine(values: List<TextProperties>): TextProperties {
            if (values.isEmpty()) return unavailable("Text layout not ready")
            fun property(select: (TextProperties) -> TextPropertyValue): TextPropertyValue {
                val fields = values.map(select)
                fields.filterIsInstance<TextPropertyValue.Unavailable>().firstOrNull()?.let { return it }
                return fields.distinct().singleOrNull() ?: TextPropertyValue.Mixed
            }
            return TextProperties(property { it.fontSize }, property { it.fontFamily },
                property { it.fontWeight }, property { it.fontStyle }, property { it.letterSpacing })
        }
    }
}

/** Mixed is different from missing metadata; unknown properties are never guessed. */
sealed interface TextPropertyValue {
    data class Known(val value: String) : TextPropertyValue
    data object Mixed : TextPropertyValue
    data class Unavailable(val reason: String) : TextPropertyValue

    fun displayText(): String = when (this) {
        is Known -> value
        Mixed -> "Mixed"
        is Unavailable -> "Unavailable · $reason"
    }
}

internal object TextPropertyFormat {
    fun size(sp: Float, px: Float): TextPropertyValue =
        if (sp.isFinite() && px.isFinite() && sp >= 0f && px >= 0f) {
            TextPropertyValue.Known("${number(sp)} sp · ${number(px)} px")
        } else TextPropertyValue.Unavailable("Invalid font size")

    fun spacing(value: Float, unit: String): TextPropertyValue =
        if (value.isFinite()) TextPropertyValue.Known("${number(value, 3)} $unit")
        else TextPropertyValue.Unavailable("Invalid letter spacing")

    fun weight(weight: Int, synthetic: Boolean = false): TextPropertyValue {
        if (weight !in 1..1000) return TextPropertyValue.Unavailable("Font weight unavailable")
        val name = when (weight) {
            100 -> "Thin"; 200 -> "ExtraLight"; 300 -> "Light"; 400 -> "Normal"
            500 -> "Medium"; 600 -> "SemiBold"; 700 -> "Bold"; 800 -> "ExtraBold"; 900 -> "Black"
            else -> null
        }
        return TextPropertyValue.Known("$weight${name?.let { " · $it" } ?: ""}${if (synthetic) " (synthetic bold)" else ""}")
    }

    private fun number(value: Float, decimals: Int = 1) = String.format(Locale.ROOT, "%.${decimals}f", value)
}
