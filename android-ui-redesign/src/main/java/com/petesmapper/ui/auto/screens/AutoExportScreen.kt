package com.petesmapper.ui.auto.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.AutoExportBridge
import com.petesmapper.ui.export.PricingInput
import com.petesmapper.ui.viewmodel.ProjectUiState
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel
import java.io.File

@Composable
fun AutoExportScreen(vm: AutoWorkflowViewModel) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val bridge = remember { AutoExportBridge() }
    var exportLock by remember { mutableStateOf(false) }
    var lastStatus by remember { mutableStateOf("Idle") }
    var exportedCount by remember { mutableIntStateOf(0) }

    val canExport = state.autoValidationReport?.canExport ?: false
    val route = state.routePlan
    val projectUi = ProjectUiState(
        projectName = "AUTO Export",
        controllerType = "T8000",
        voltageType = "12V",
        pixelSpacing = 0.5f,
    )
    val pricing = PricingInput(1f, 0.5f, 0.6f, 120f, 180f, 1f, 0.3f, 0.2f, 150f, 100f, 200f, 50f, 20f)

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Export", style = MaterialTheme.typography.titleLarge)

        Card(colors = CardDefaults.cardColors(containerColor = if (canExport) Color(0xFF1C3B2A) else Color(0xFF4A1E1E))) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Validation gate: ${if (canExport) "PASS" else "BLOCKED"}")
                Text("Export lock: ${if (exportLock) "ACTIVE" else "IDLE"}")
                Text("Status: $lastStatus")
                Text("Exported file count: $exportedCount")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching { bridge.exportDxf(route!!, projectUi) }
                    .onSuccess { exportedCount += 1; lastStatus = "DXF export success" }
                    .onFailure { lastStatus = "DXF export failed: ${it.message}" }
                exportLock = false
            }) { Text("Export DXF") }

            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching { bridge.exportControllerMap(route!!, projectUi) }
                    .onSuccess { exportedCount += 1; lastStatus = "Controller map export success" }
                    .onFailure { lastStatus = "Controller map export failed: ${it.message}" }
                exportLock = false
            }) { Text("Controller Map") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching {
                    val map = bridge.exportControllerMap(route!!, projectUi)
                    bridge.exportBom(route, map, projectUi)
                }.onSuccess { exportedCount += 1; lastStatus = "BOM export success" }
                    .onFailure { lastStatus = "BOM export failed: ${it.message}" }
                exportLock = false
            }) { Text("Export BOM") }

            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching {
                    val map = bridge.exportControllerMap(route!!, projectUi)
                    val bom = bridge.exportBom(route, map, projectUi)
                    bridge.exportQuote(bom, pricing)
                }.onSuccess { exportedCount += 1; lastStatus = "Quote export success" }
                    .onFailure { lastStatus = "Quote export failed: ${it.message}" }
                exportLock = false
            }) { Text("Export Quote") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching {
                    val map = bridge.exportControllerMap(route!!, projectUi)
                    val bom = bridge.exportBom(route, map, projectUi)
                    val quote = bridge.exportQuote(bom, pricing)
                    bridge.exportPdf(File(context.cacheDir, "auto_export_${System.currentTimeMillis()}.pdf"), route, projectUi, map, bom, quote, pricing)
                }.onSuccess { exportedCount += 1; lastStatus = "PDF export success" }
                    .onFailure { lastStatus = "PDF export failed: ${it.message}" }
                exportLock = false
            }) { Text("Export PDF") }

            Button(enabled = !exportLock && canExport && route != null, onClick = {
                exportLock = true
                runCatching {
                    val bundle = bridge.exportAll(File(context.cacheDir, "auto_all_${System.currentTimeMillis()}.pdf"), route!!, projectUi, pricing)
                    vm.setExport(bundle)
                }.onSuccess { exportedCount += 5; lastStatus = "Export ALL success" }
                    .onFailure { lastStatus = "Export ALL failed: ${it.message}" }
                exportLock = false
            }) { Text("Export ALL") }
        }
    }
}
