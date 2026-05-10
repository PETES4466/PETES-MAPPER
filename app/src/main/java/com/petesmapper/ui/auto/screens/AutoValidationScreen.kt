package com.petesmapper.ui.auto.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.AutoValidationSeverity
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoValidationScreen(vm: AutoWorkflowViewModel) {
    val state by vm.state.collectAsState()
    val report = state.autoValidationReport
    val issues = report?.issues.orEmpty()
    val hasCritical = issues.any { it.severity == AutoValidationSeverity.CRITICAL }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Validation", style = MaterialTheme.typography.titleLarge)

        Card(colors = CardDefaults.cardColors(containerColor = if (hasCritical) Color(0xFF4A1C1C) else Color(0xFF1B3A2A))) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Summary")
                Text("Total issues: ${issues.size}")
                Text("Critical: ${issues.count { it.severity == AutoValidationSeverity.CRITICAL }}")
                Text("Warnings: ${issues.count { it.severity == AutoValidationSeverity.WARNING }}")
                Text("Export allowed: ${if (report?.canExport == true) "YES" else "NO"}")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runValidation() }) { Text("Run Validation") }
            Button(onClick = { if (!hasCritical) vm.resetFromStep(6) }, enabled = !hasCritical) { Text("Proceed to Export") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(issues) { issue ->
                val color = when (issue.severity) {
                    AutoValidationSeverity.INFO -> Color(0xFF1E3A5F)
                    AutoValidationSeverity.WARNING -> Color(0xFF5B3B00)
                    AutoValidationSeverity.CRITICAL -> Color(0xFF5A1D1D)
                }
                Card(colors = CardDefaults.cardColors(containerColor = color)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${issue.severity} • ${issue.code}")
                        Text(issue.message)
                        if (issue.code.contains("CONTINUITY") || issue.code.contains("GAP")) Text("Continuity warning")
                        if (issue.code.contains("SPACING")) Text("Spacing warning")
                        if (issue.code.contains("JUMP")) Text("Jump mismatch warning")
                        if (issue.code.contains("MISSING") || issue.code.contains("NODES")) Text("Missing node warning")
                        if (issue.code.contains("CONFIDENCE")) Text("Confidence warning")
                    }
                }
            }
        }
    }
}
