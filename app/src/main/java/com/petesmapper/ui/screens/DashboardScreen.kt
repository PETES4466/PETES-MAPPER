package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.IndustrialPanel
import com.petesmapper.ui.components.MetricRow
import com.petesmapper.ui.model.MetricChip
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun DashboardScreen(
    projectViewModel: ProjectViewModel,
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onWiringLibrary: () -> Unit,
    onTemplates: () -> Unit,
) {
    val state by projectViewModel.uiState.collectAsState()
    val recentProjects = listOf("ACME-ENTRY-A", "CITY-MALL-M", "NOVA-SIGN-C")
    val wiringPreview = listOf("Snake", "Z Strip", "Spiral", "Hybrid Pillar")
    val templatePreview = listOf("H", "M", "O", "C", "A")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("LED Mapper Dashboard", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            MetricRow(
                metrics = listOf(
                    MetricChip("Controller Type", state.controllerType),
                    MetricChip("Voltage", state.voltageType),
                    MetricChip("Pixel Pitch", "${"%.2f".format(state.pixelSpacing)} in"),
                    MetricChip("Last Project", recentProjects.first()),
                ),
            )
        }
        item {
            IndustrialPanel(title = "Current Project Status") {
                StatusRow("Letter", state.selectedLetter.ifBlank { "Not selected" })
                StatusRow("Wiring Pattern", state.selectedWiringPattern.displayName)
                StatusRow("Pixel Count", state.letterGeometry.nodeCount.toString())
                StatusRow("Export Status", if (state.finalPixelMapSummary == "Pixel map pending") "Pending" else "Ready")
            }
        }
        item {
            IndustrialPanel(title = "Recent Projects") {
                recentProjects.forEach { RecentProjectCard(projectName = it) }
            }
        }
        item {
            IndustrialPanel(title = "Wiring Library Preview") {
                ChipGrid(items = wiringPreview)
            }
        }
        item {
            IndustrialPanel(title = "Templates Preview") {
                ChipGrid(items = templatePreview)
            }
        }
        item {
            IndustrialPanel(title = "Quick Actions") {
                ActionButton("New Project", onNewProject)
                ActionButton("Open Project", onOpenProject)
                ActionButton("Wiring Library", onWiringLibrary)
                ActionButton("Templates", onTemplates)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentProjectCard(projectName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(projectName, fontWeight = FontWeight.SemiBold)
            Text("Last revision synced", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChipGrid(items: List<String>) {
    items.chunked(2).forEach { rowItems ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            rowItems.forEach { item ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(item, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (rowItems.size == 1) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Text(label)
        }
    }
}
