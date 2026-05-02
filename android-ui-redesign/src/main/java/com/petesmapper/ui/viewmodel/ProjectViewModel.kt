package com.petesmapper.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WiringPatternModel(
    val key: String,
    val displayName: String,
    val directionHint: String = "Forward",
    val jumpWireCount: Int = 0,
) {
    override fun toString(): String = displayName
}

data class LayerConfigModel(
    val key: String,
    val displayName: String,
    val includeBorder: Boolean = true,
    val includeFill: Boolean = true,
    val includeInnerIslands: Boolean = false,
) {
    override fun toString(): String = displayName
}

data class LetterGeometryModel(
    val glyphText: String = "",
    val contours: List<List<Pair<Float, Float>>> = emptyList(),
    val boundingWidthIn: Float = 0f,
    val boundingHeightIn: Float = 0f,
    val nodeCount: Int = 0,
)

data class DxfExportSettings(
    val units: String = "INCH",
    val scale: Float = 1f,
    val layerNaming: String = "ByFeature",
)

data class ExportSettings(
    val includeDxfLayers: Boolean = true,
    val includeControllerMap: Boolean = true,
    val includePdfSummary: Boolean = true,
    val dxf: DxfExportSettings = DxfExportSettings(),
)

data class ProjectUiState(
    val projectName: String = "",
    val selectedLetter: String = "",
    val selectedFont: String = "DIN 1451",
    val letterWidth: Float = 36f,
    val letterHeight: Float = 24f,
    val strokeThickness: Float = 1.5f,
    val pixelSpacing: Float = 0.5f,
    val selectedWiringPattern: WiringPatternModel = WiringPatternModel(key = "snake", displayName = "Snake"),
    val selectedLayerConfig: LayerConfigModel = LayerConfigModel(key = "border", displayName = "Border"),
    val controllerType: String = "T1000/T8000",
    val voltageType: String = "12V",
    val estimatedCurrent: Float = 0f,
    val injectionPoints: Int = 0,
    val powerSupplyType: String = "Mean Well 12V",
    val exportSettings: ExportSettings = ExportSettings(),
    val letterGeometry: LetterGeometryModel = LetterGeometryModel(),
    val letterGeometrySummary: String = "Geometry not generated",
    val finalPixelMapSummary: String = "Pixel map pending",
)

class ProjectViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    fun updateProjectSetup(
        projectName: String,
        controllerType: String,
        voltageType: String,
        pixelSpacing: Float,
        defaultWiring: String,
    ) {
        _uiState.update {
            it.copy(
                projectName = projectName,
                controllerType = controllerType,
                voltageType = voltageType,
                pixelSpacing = pixelSpacing,
                selectedWiringPattern = toWiringPattern(defaultWiring),
            )
        }
    }

    fun updateLetter(
        selectedLetter: String,
        selectedFont: String,
        letterWidth: Float,
        letterHeight: Float,
        strokeThickness: Float,
        pixelSpacing: Float,
    ) {
        val nodeCountEstimate = ((letterWidth * letterHeight) / (pixelSpacing * pixelSpacing)).toInt().coerceAtLeast(0)
        val geometry = LetterGeometryModel(
            glyphText = selectedLetter,
            contours = buildPlaceholderContour(letterWidth, letterHeight),
            boundingWidthIn = letterWidth,
            boundingHeightIn = letterHeight,
            nodeCount = nodeCountEstimate,
        )

        _uiState.update {
            it.copy(
                selectedLetter = selectedLetter,
                selectedFont = selectedFont,
                letterWidth = letterWidth,
                letterHeight = letterHeight,
                strokeThickness = strokeThickness,
                pixelSpacing = pixelSpacing,
                letterGeometry = geometry,
                letterGeometrySummary = "${selectedLetter.ifBlank { "(blank)" }} • ${"%.1f".format(letterWidth)}x${"%.1f".format(letterHeight)} in",
            )
        }
    }

    fun setWiringPattern(pattern: String) {
        _uiState.update {
            it.copy(
                selectedWiringPattern = toWiringPattern(pattern),
                finalPixelMapSummary = "Map draft with ${toWiringPattern(pattern).displayName} pattern",
            )
        }
    }

    fun setLayerConfig(config: String) {
        _uiState.update { it.copy(selectedLayerConfig = toLayerConfig(config)) }
    }

    fun setPowerPlanning(
        estimatedCurrent: Float,
        injectionPoints: Int,
        powerSupplyType: String,
    ) {
        _uiState.update {
            it.copy(
                estimatedCurrent = estimatedCurrent,
                injectionPoints = injectionPoints,
                powerSupplyType = powerSupplyType,
            )
        }
    }

    fun setExportSettings(settings: ExportSettings) {
        _uiState.update { it.copy(exportSettings = settings) }
    }

    fun setDxfSettings(
        units: String,
        scale: Float,
        layerNaming: String,
    ) {
        _uiState.update {
            it.copy(
                exportSettings = it.exportSettings.copy(
                    dxf = DxfExportSettings(
                        units = units,
                        scale = scale,
                        layerNaming = layerNaming,
                    ),
                ),
            )
        }
    }

    fun generateFinalPixelMap() {
        _uiState.update {
            it.copy(
                finalPixelMapSummary = "${it.selectedLetter.ifBlank { "Letter" }} • ${it.selectedWiringPattern.displayName} • ${it.selectedLayerConfig.displayName}",
            )
        }
    }

    private fun toWiringPattern(pattern: String): WiringPatternModel {
        val normalized = pattern.trim().ifBlank { "Snake" }
        return WiringPatternModel(
            key = normalized.lowercase().replace(" ", "_"),
            displayName = normalized,
            directionHint = if (normalized.contains("zig", ignoreCase = true)) "Alternating" else "Forward",
            jumpWireCount = if (normalized.contains("hybrid", ignoreCase = true)) 2 else 0,
        )
    }

    private fun toLayerConfig(config: String): LayerConfigModel {
        val normalized = config.trim().ifBlank { "Border" }
        return LayerConfigModel(
            key = normalized.lowercase().replace(" ", "_"),
            displayName = normalized,
            includeBorder = normalized.equals("Border", true) || normalized.equals("Fill", true),
            includeFill = !normalized.equals("Inner islands", true),
            includeInnerIslands = normalized.equals("Inner islands", true),
        )
    }

    private fun buildPlaceholderContour(width: Float, height: Float): List<List<Pair<Float, Float>>> {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        return listOf(
            listOf(
                0f to 0f,
                w to 0f,
                w to h,
                0f to h,
                0f to 0f,
            ),
        )
    }
}
