package com.petesmapper.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.petesmapper.ui.model.DashboardAction
import com.petesmapper.ui.model.ExportOption
import com.petesmapper.ui.model.LayerTab
import com.petesmapper.ui.model.MetricChip
import com.petesmapper.ui.model.WiringOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    val quickActions = listOf(
        DashboardAction("New Project", "Start from blank canvas", "letter_builder"),
        DashboardAction("Open Project", "Resume latest production file", "open_project"),
        DashboardAction("Templates", "Use approved signage presets", "templates"),
        DashboardAction("Wiring Library", "Browse known controller layouts", "wiring_library"),
    )

    val metrics = listOf(
        MetricChip("Active Job", "ACME-FRONT-12"),
        MetricChip("Controller", "Novastar A5"),
        MetricChip("Last Export", "DXF 17:32"),
    )
}

class WiringViewModel : ViewModel() {
    private val _selectedOption = MutableStateFlow("Snake - Min Jump")
    val selectedOption: StateFlow<String> = _selectedOption.asStateFlow()

    val options = listOf(
        WiringOption("w1", "Snake - Min Jump", 84.5f, 4, 6, 3),
        WiringOption("w2", "Clockwise Rings", 92.0f, 7, 8, 4),
        WiringOption("w3", "Split Feed Dual Start", 76.3f, 5, 5, 2),
    )

    fun selectOption(label: String) {
        _selectedOption.value = label
    }
}

class LayerEditorViewModel : ViewModel() {
    val tabs = listOf(
        LayerTab("border", "Border"),
        LayerTab("fill", "Fill"),
        LayerTab("islands", "Inner islands"),
    )

    private val _selectedTab = MutableStateFlow("Border")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    fun selectTab(title: String) {
        _selectedTab.value = title
    }
}

class ExportViewModel : ViewModel() {
    val options = listOf(
        ExportOption("dxf", "DXF", ".dxf", "Includes vector geometry and layer naming."),
        ExportOption("controller", "Controller Mapping", ".json", "Strip order, addressing, and universe assignments."),
        ExportOption("pdf", "PDF", ".pdf", "Installer-ready annotated document with power notes."),
    )
}
