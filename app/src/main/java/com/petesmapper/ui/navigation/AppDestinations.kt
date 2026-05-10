package com.petesmapper.ui.navigation

sealed class AppDestination(val route: String) {
    data object Dashboard : AppDestination("dashboard")
    data object ProjectSetup : AppDestination("project_setup")
    data object LetterBuilder : AppDestination("letter_builder")
    data object WiringIntelligence : AppDestination("wiring_intelligence")
    data object LayerEditor : AppDestination("layer_editor")
    data object PixelMappingPreview : AppDestination("pixel_mapping")
    data object Export : AppDestination("export")
    data object WiringLibrary : AppDestination("wiring_library")
}
