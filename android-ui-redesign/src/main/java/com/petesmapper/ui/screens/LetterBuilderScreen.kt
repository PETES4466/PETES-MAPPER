package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.GridCanvasPlaceholder
import com.petesmapper.ui.components.IndustrialPanel
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun LetterBuilderScreen(
    projectViewModel: ProjectViewModel,
    onGenerated: () -> Unit,
) {
    val state by projectViewModel.uiState.collectAsState()
    var text by remember { mutableStateOf(state.selectedLetter.ifBlank { "PETE'S LED" }) }
    var font by remember { mutableStateOf(state.selectedFont) }
    var height by remember { mutableFloatStateOf(state.letterHeight) }
    var width by remember { mutableFloatStateOf(state.letterWidth) }
    var stroke by remember { mutableFloatStateOf(state.strokeThickness) }
    var spacing by remember { mutableFloatStateOf(state.pixelSpacing) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            IndustrialPanel(title = "Letter Builder") {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text Input") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = font, onValueChange = { font = it }, label = { Text("Font Selector") }, modifier = Modifier.fillMaxWidth())
                NumericSlider("Height (in)", height) { height = it }
                NumericSlider("Width (in)", width) { width = it }
                NumericSlider("Stroke Thickness (in)", stroke, 0.1f..4f) { stroke = it }
                NumericSlider("Pixel Spacing (in)", spacing, 0.1f..2f) { spacing = it }
                Button(
                    onClick = {
                        projectViewModel.updateLetter(text, font, width, height, stroke, spacing)
                        onGenerated()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Generate Letter")
                }
            }
        }
        item {
            IndustrialPanel(title = "Live Geometry") {
                Text("${state.projectName.ifBlank { "Project" }} • ${state.controllerType} • ${state.voltageType}")
                GridCanvasPlaceholder(label = "${state.letterGeometrySummary} • ${state.selectedFont}", modifier = Modifier.fillMaxWidth().height(260.dp))
            }
        }
    }
}

@Composable
private fun NumericSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 1f..60f, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label: ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
