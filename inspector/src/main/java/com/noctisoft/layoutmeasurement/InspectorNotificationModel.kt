package com.noctisoft.layoutmeasurement

data class InspectorNotificationModel(
    val contentText: String,
    val showStopAction: Boolean,
)

fun buildInspectorNotificationModel(
    isActive: Boolean,
    mode: MeasureMode,
): InspectorNotificationModel {
    if (!isActive) return InspectorNotificationModel("Tap to show inspector controls", false)
    val name = mode.name.lowercase().replaceFirstChar { it.uppercase() }
    return InspectorNotificationModel("$name active", true)
}
