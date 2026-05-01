package com.petesmapper.ui.auto.viewmodel

import androidx.lifecycle.ViewModel
import com.petesmapper.ui.auto.AutoExportBundle
import com.petesmapper.ui.auto.AutoValidationReport
import com.petesmapper.ui.auto.CorrectedFlowModel
import com.petesmapper.ui.auto.FlowDirectionModel
import com.petesmapper.ui.auto.NormalizedImage
import com.petesmapper.ui.auto.PixelNodeMap
import com.petesmapper.ui.auto.StripPathModel
import com.petesmapper.ui.geometry.RoutePlan
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

    fun setNormalizedImage(v: NormalizedImage) = _state.update { it.copy(normalizedImage = v) }
    fun setStripPathModel(v: StripPathModel) = _state.update { it.copy(stripPathModel = v) }
    fun setPixelNodeMap(v: PixelNodeMap) = _state.update { it.copy(pixelNodeMap = v) }
    fun setFlowDirectionModel(v: FlowDirectionModel) = _state.update { it.copy(flowDirectionModel = v) }
    fun setCorrectedFlowModel(v: CorrectedFlowModel) = _state.update { it.copy(correctedFlowModel = v) }
    fun setRoutePlan(v: RoutePlan) = _state.update { it.copy(routePlan = v) }
    fun setValidation(v: AutoValidationReport) = _state.update { it.copy(autoValidationReport = v) }
    fun setExport(v: AutoExportBundle) = _state.update { it.copy(autoExportBundle = v) }
}
