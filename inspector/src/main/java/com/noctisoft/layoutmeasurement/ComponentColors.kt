package com.noctisoft.layoutmeasurement

import java.util.Locale

/** Color properties at capture time, before ancestor opacity and screen compositing. */
data class ComponentColors(
    val text: ColorValue,
    val background: ColorValue,
    val border: ColorValue,
    /** Non-null when text belongs to descendants rather than the selected node itself. */
    val textOrigin: String? = null,
)

/** Missing information is deliberately not represented by transparent or black. */
sealed interface ColorValue {
    data class Solid(val argb: Int) : ColorValue
    data class Multiple(val argb: List<Int>) : ColorValue
    data object None : ColorValue
    data object NotApplicable : ColorValue
    data class Unavailable(val reason: String) : ColorValue

    fun copyText(): String? = when (this) {
        is Solid -> ColorCode.format(argb)
        is Multiple -> argb.joinToString("\n", transform = ColorCode::format)
        else -> null
    }

    companion object {
        fun fromColors(colors: List<Int>): ColorValue {
            val distinct = colors.distinct()
            return when (distinct.size) {
                0 -> None
                1 -> Solid(distinct.single())
                else -> Multiple(distinct)
            }
        }
    }
}

object ColorCode {
    /** Android order: alpha, red, green, blue (not CSS's trailing alpha). */
    fun format(argb: Int): String = String.format(Locale.ROOT, "#%08X", argb)
}
