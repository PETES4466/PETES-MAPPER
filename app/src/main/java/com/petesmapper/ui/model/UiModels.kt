package com.petesmapper.ui.model

data class DashboardAction(
    val title: String,
    val subtitle: String,
    val route: String,
)

data class MetricChip(
    val label: String,
    val value: String,
)

data class WiringOption(
    val id: String,
    val label: String,
    val estimatedLengthFt: Float,
    val jumpWireCount: Int,
    val stripSegments: Int,
    val powerInjections: Int,
)

data class LayerTab(
    val key: String,
    val title: String,
)

data class ExportOption(
    val key: String,
    val label: String,
    val extension: String,
    val helperText: String,
)
