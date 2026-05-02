package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.GridCanvasPlaceholder
import com.petesmapper.ui.components.IndustrialPanel
import com.petesmapper.ui.components.SegmentedToggle
import com.petesmapper.ui.viewmodel.LayerEditorViewModel
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun LayerEditorScreen(
    layerViewModel: LayerEditorViewModel,
    projectViewModel: ProjectViewModel,
    onFinalize: () -> Unit,
) {
    val selectedTab by layerViewModel.selectedTab.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SegmentedToggle(
                items = layerViewModel.tabs.map { it.title },
                selected = selectedTab,
                onSelect = {
                    layerViewModel.selectTab(it)
                    projectViewModel.setLayerConfig(it)
                },
            )
        }
        item {
            IndustrialPanel(title = "$selectedTab Layer") {
                Text("Wiring base: ${projectState.selectedWiringPattern}")
                GridCanvasPlaceholder(label = "$selectedTab geometry editor", modifier = Modifier.fillMaxWidth().height(260.dp))
                Button(onClick = onFinalize, modifier = Modifier.fillMaxWidth()) {
                    Text("Finalize Layers")
                }
            }
        }
    }
}
