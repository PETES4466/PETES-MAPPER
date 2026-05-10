package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun PixelMappingPreviewScreen(
    projectViewModel: ProjectViewModel,
    onProceed: () -> Unit,
) {
    val state by projectViewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IndustrialPanel(title = "Pixel Mapping Preview") {
                GridCanvasPlaceholder(
                    label = "${state.finalPixelMapSummary} • Pixel Index • Data Flow • Strip ID • PI",
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                Button(
                    onClick = {
                        projectViewModel.generateFinalPixelMap()
                        onProceed()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Proceed to Export")
                }
            }
        }
        item {
            IndustrialPanel(title = "Legend") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pixel index")
                    Text("Data flow")
                    Text("Strip ID")
                    Text("Power injection points")
                }
            }
        }
    }
}
