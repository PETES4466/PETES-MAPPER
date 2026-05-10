package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.viewmodel.ExportSettings
import com.petesmapper.ui.viewmodel.ExportViewModel
import com.petesmapper.ui.viewmodel.ProjectViewModel

@Composable
fun ExportScreen(
    exportViewModel: ExportViewModel,
    projectViewModel: ProjectViewModel,
) {
    val project by projectViewModel.uiState.collectAsState()
    val settings = project.exportSettings

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Export", fontWeight = FontWeight.Bold)
            Text("Bundle: ${project.finalPixelMapSummary}")
        }
        item {
            ExportSettingsCard(settings = settings) { projectViewModel.setExportSettings(it) }
        }
        items(exportViewModel.options) { option ->
            Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${option.label} (${option.extension})", fontWeight = FontWeight.SemiBold)
                    Text(option.helperText)
                    Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (option.key == "dxf") "Export DXF" else "Export ${option.label}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSettingsCard(settings: ExportSettings, onChange: (ExportSettings) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingRow("Include DXF layers", settings.includeDxfLayers) { onChange(settings.copy(includeDxfLayers = it)) }
            SettingRow("Include controller map", settings.includeControllerMap) { onChange(settings.copy(includeControllerMap = it)) }
            SettingRow("Include PDF summary", settings.includePdfSummary) { onChange(settings.copy(includePdfSummary = it)) }
        }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label)
        Checkbox(checked = checked, onCheckedChange = onChecked)
    }
}
