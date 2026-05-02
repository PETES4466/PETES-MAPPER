package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.IndustrialPanel
import com.petesmapper.ui.components.SegmentedToggle
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun ProjectSetupScreen(
    projectViewModel: ProjectViewModel,
    onStartDesigning: () -> Unit,
) {
    val state = projectViewModel.uiState
    var projectName by remember { mutableStateOf(state.value.projectName.ifBlank { "New Signage Project" }) }
    var controller by remember { mutableStateOf(state.value.controllerType) }
    var voltage by remember { mutableStateOf(state.value.voltageType) }
    var pixelSpacing by remember { mutableFloatStateOf(state.value.pixelSpacing) }
    var defaultWiring by remember { mutableStateOf(state.value.selectedWiringPattern) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IndustrialPanel(title = "Project Setup") {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = controller,
                    onValueChange = { controller = it },
                    label = { Text("Controller Type") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voltage")
                    SegmentedToggle(items = listOf("5V", "12V"), selected = voltage, onSelect = { voltage = it })
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default Wiring Preference")
                    SegmentedToggle(
                        items = listOf("Snake", "Zigzag", "Contour", "Hybrid"),
                        selected = defaultWiring,
                        onSelect = { defaultWiring = it },
                    )
                }
                OutlinedTextField(
                    value = "${"%.2f".format(pixelSpacing)}",
                    onValueChange = { pixelSpacing = it.toFloatOrNull() ?: pixelSpacing },
                    label = { Text("Pixel Spacing (in)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        projectViewModel.updateProjectSetup(projectName, controller, voltage, pixelSpacing, defaultWiring)
                        onStartDesigning()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start Designing")
                }
            }
        }
    }
}
