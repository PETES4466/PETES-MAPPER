package com.petesmapper.ui.auto.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoLandingScreen(vm: AutoWorkflowViewModel) {
    val s by vm.state.collectAsState()
    ScreenShell("AUTO Landing") {
        Text("Installer-first AUTO flow", style = MaterialTheme.typography.titleMedium)
        Text("Image: ${if (s.normalizedImage != null) "Loaded" else "Not loaded"}")
    }
}

@Composable fun AutoStripDetectionScreen(vm: AutoWorkflowViewModel) = ScreenShell("Strip Detection") { OverlayHints("Strip path overlay") }
@Composable fun AutoPixelDetectionScreen(vm: AutoWorkflowViewModel) = ScreenShell("Pixel Detection") { OverlayHints("Pixel nodes overlay") }
@Composable fun AutoFlowCorrectionScreen(vm: AutoWorkflowViewModel) = ScreenShell("Flow Correction") { OverlayHints("Start/End + tap selection") }
@Composable fun AutoValidationScreen(vm: AutoWorkflowViewModel) = ScreenShell("Validation") { OverlayHints("Warnings overlay") }
@Composable fun AutoExportScreen(vm: AutoWorkflowViewModel) = ScreenShell("Export") { StepActions() }

@Composable
private fun ScreenShell(title: String, content: @Composable Column.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

@Composable
private fun StepActions() {
    Button(modifier = Modifier.fillMaxWidth(), onClick = {}) { Text("Large Touch Action") }
    Button(modifier = Modifier.fillMaxWidth(), onClick = {}) { Text("Auto-save Snapshot") }
    Button(modifier = Modifier.fillMaxWidth(), onClick = {}) { Text("Resume Project") }
}

@Composable
private fun OverlayHints(text: String) {
    Text("Zoomable image overlay (planned)")
    Text(text)
    Text("Jump wires / warnings visible")
}
