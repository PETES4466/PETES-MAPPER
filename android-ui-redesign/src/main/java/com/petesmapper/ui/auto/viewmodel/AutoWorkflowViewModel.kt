package com.petesmapper.ui.auto.viewmodel

import androidx.lifecycle.ViewModel
import com.petesmapper.ui.auto.AutoExportBridge
import com.petesmapper.ui.auto.AutoExportBundle
import com.petesmapper.ui.auto.AutoFlowCorrectionEngine
import com.petesmapper.ui.auto.AutoValidationReport
import com.petesmapper.ui.auto.AutoValidationEngine
import com.petesmapper.ui.auto.CorrectedFlowModel
import com.petesmapper.ui.auto.FlowDirectionEngine
import com.petesmapper.ui.auto.FlowDirectionModel
import com.petesmapper.ui.auto.NormalizedImage
import com.petesmapper.ui.auto.PixelDetectionEngine
import com.petesmapper.ui.auto.PixelNodeMap
import com.petesmapper.ui.auto.ReverseRouteBuilder
import com.petesmapper.ui.auto.StripDetectionEngine
import com.petesmapper.ui.auto.StripPathModel
import com.petesmapper.ui.export.PricingInput
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.viewmodel.ProjectUiState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AutoWorkflowState(
    val normalizedImage: NormalizedImage? = null,
    val stripPathModel: StripPathModel? = null,
    val pixelNodeMap: PixelNodeMap? = null,
    val flowDirectionModel: FlowDirectionModel? = null,
    val correctedFlowModel: CorrectedFlowModel? = null,
    val routePlan: RoutePlan? = null,
    val autoValidationReport: AutoValidationReport? = null,
    val autoExportBundle: AutoExportBundle? = null,
)

class AutoWorkflowViewModel : ViewModel() {
    private val _state = MutableStateFlow(AutoWorkflowState())
    val state: StateFlow<AutoWorkflowState> = _state.asStateFlow()
    private val snapshots = ArrayDeque<AutoWorkflowState>()

    private val stripDetectionEngine = StripDetectionEngine()
    private val pixelDetectionEngine = PixelDetectionEngine()
    private val flowDirectionEngine = FlowDirectionEngine()
    private val flowCorrectionEngine = AutoFlowCorrectionEngine()
    private val reverseRouteBuilder = ReverseRouteBuilder()
    private val autoValidationEngine = AutoValidationEngine()
    private val autoExportBridge = AutoExportBridge()

    fun setNormalizedImage(v: NormalizedImage) = _state.update {
        it.copy(normalizedImage = v, stripPathModel = null, pixelNodeMap = null, flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null)
    }

    fun runStripDetection() {
        val image = _state.value.normalizedImage ?: return
        val strip = stripDetectionEngine.buildPathModel(image)
        _state.update { it.copy(stripPathModel = strip, pixelNodeMap = null, flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runPixelDetection() {
        val s = _state.value
        val image = s.normalizedImage ?: return
        val strip = s.stripPathModel ?: return
        val pixels = pixelDetectionEngine.detectPixelNodes(image, strip)
        _state.update { it.copy(pixelNodeMap = pixels, flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runFlowDetection() {
        val s = _state.value
        val pixels = s.pixelNodeMap ?: return
        val strip = s.stripPathModel ?: return
        val flow = flowDirectionEngine.detect(pixels, strip)
        _state.update { it.copy(flowDirectionModel = flow, correctedFlowModel = flowCorrectionEngine.fromDetected(flow), routePlan = null, autoValidationReport = null, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runFlowCorrection(corrected: CorrectedFlowModel) {
        _state.update { it.copy(correctedFlowModel = corrected, routePlan = null, autoValidationReport = null, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runReverseBuild() {
        val s = _state.value
        val strip = s.stripPathModel ?: return
        val pixels = s.pixelNodeMap ?: return
        val corrected = s.correctedFlowModel ?: return
        val plan = reverseRouteBuilder.buildRoutePlan(strip, pixels, corrected)
        _state.update { it.copy(routePlan = plan, autoValidationReport = null, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runValidation() {
        val s = _state.value
        val report = autoValidationEngine.validate(s.stripPathModel ?: return, s.pixelNodeMap ?: return, s.correctedFlowModel ?: return, s.routePlan ?: return)
        _state.update { it.copy(autoValidationReport = report, autoExportBundle = null) }
        autoSaveSnapshot()
    }

    fun runExport(projectUiState: ProjectUiState, pricingInput: PricingInput, outputPdfFile: File) {
        val s = _state.value
        val plan = s.routePlan ?: return
        val bundle = autoExportBridge.exportAll(outputPdfFile, plan, projectUiState, pricingInput)
        _state.update { it.copy(autoExportBundle = bundle) }
        autoSaveSnapshot()
    }

    fun autoSaveSnapshot() {
        snapshots.addLast(_state.value.copy())
        while (snapshots.size > 10) snapshots.removeFirst()
    }

    fun restoreWorkflow() {
        snapshots.lastOrNull()?.let { _state.value = it }
    }

    fun resetFromStep(step: Int) {
        _state.update {
            when (step) {
                0 -> AutoWorkflowState()
                1 -> it.copy(stripPathModel = null, pixelNodeMap = null, flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null)
                2 -> it.copy(pixelNodeMap = null, flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null)
                3 -> it.copy(flowDirectionModel = null, correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null)
                4 -> it.copy(correctedFlowModel = null, routePlan = null, autoValidationReport = null, autoExportBundle = null)
                5 -> it.copy(routePlan = null, autoValidationReport = null, autoExportBundle = null)
                6 -> it.copy(autoValidationReport = null, autoExportBundle = null)
                else -> it
            }
        }
    }
}
