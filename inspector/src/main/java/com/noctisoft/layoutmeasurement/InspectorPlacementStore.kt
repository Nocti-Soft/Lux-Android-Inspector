package com.noctisoft.layoutmeasurement

import android.content.Context

object FloatingPlacementCodec {
    fun encode(placement: FloatingPlacement): String {
        val clean = placement.sanitized()
        return "${clean.xFraction}|${clean.yFraction}|${clean.dockSide.name}"
    }

    fun decode(raw: String?): FloatingPlacement? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        val side = runCatching { DockSide.valueOf(parts[2]) }.getOrNull() ?: return null
        return FloatingPlacement(x, y, side).sanitized()
    }
}

class InspectorPlacementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "layout_measurement_inspector",
        Context.MODE_PRIVATE,
    )

    fun load(): FloatingPlacement =
        FloatingPlacementCodec.decode(preferences.getString(KEY_PLACEMENT, null))
            ?: FloatingPlacement()

    fun save(placement: FloatingPlacement) {
        preferences.edit()
            .putString(KEY_PLACEMENT, FloatingPlacementCodec.encode(placement))
            .apply()
    }

    private companion object {
        const val KEY_PLACEMENT = "floating_control_placement"
    }
}
