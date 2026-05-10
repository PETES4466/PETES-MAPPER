package com.petesmapper.ui.auto.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.AutoFlowCorrectionEngine
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoFlowCorrectionScreen(vm: AutoWorkflowViewModel) {
    val state by vm.state.collectAsState()
    val correction = remember { AutoFlowCorrectionEngine() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    val corrected = state.correctedFlowModel
    val nodes = state.pixelNodeMap?.nodes.orEmpty()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Flow Correction")
        Text("Direction: ${corrected?.flowDirection ?: "UNKNOWN"}")
        Text("Confidence: ${"%.2f".format(corrected?.confidence ?: 0f)}")
        Text("Lock: ${if (corrected?.locked == true) "LOCKED" else "UNLOCKED"}")

        Card(Modifier.fillMaxWidth().height(360.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.25f, 6f)
                            panX += pan.x
                            panY += pan.y
                        }
                    }
                    .pointerInput(nodes, zoom, panX, panY) {
                        detectTapGestures { tap ->
                            selectedIndex = nodes.indexOfFirst { n ->
                                val x = n.x * zoom + panX
                                val y = n.y * zoom + panY
                                kotlin.math.abs(tap.x - x) < 10f && kotlin.math.abs(tap.y - y) < 10f
                            }
                        }
                    },
            ) {
                if (nodes.size > 1) {
                    val path = Path().apply {
                        moveTo(nodes.first().x * zoom + panX, nodes.first().y * zoom + panY)
                        nodes.drop(1).forEach { n -> lineTo(n.x * zoom + panX, n.y * zoom + panY) }
                    }
                    drawPath(path, Color(0xFF66BB6A), style = Stroke(width = 2f))
                }

                nodes.forEachIndexed { i, n ->
                    val x = n.x * zoom + panX
                    val y = n.y * zoom + panY
                    val isStart = corrected?.startNode?.index == n.index
                    val isEnd = corrected?.endNode?.index == n.index
                    val color = when {
                        isStart -> Color.Green
                        isEnd -> Color.Red
                        i == selectedIndex -> Color.Yellow
                        else -> Color.Cyan
                    }
                    drawCircle(color, 5f, Offset(x, y))
                }

                // Direction arrows
                nodes.zipWithNext().forEach { (a, b) ->
                    val ax = a.x * zoom + panX; val ay = a.y * zoom + panY
                    val bx = b.x * zoom + panX; val by = b.y * zoom + panY
                    drawLine(Color(0x88FFFFFF), Offset(ax, ay), Offset(bx, by), 1.2f)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val c = corrected ?: return@Button
                if (selectedIndex >= 0) vm.runFlowCorrection(correction.setStartNode(c, state.pixelNodeMap ?: return@Button, selectedIndex))
            }) { Text("Set Start") }
            Button(onClick = {
                val c = corrected ?: return@Button
                if (selectedIndex >= 0) vm.runFlowCorrection(correction.setEndNode(c, state.pixelNodeMap ?: return@Button, selectedIndex))
            }) { Text("Set End") }
            Button(onClick = {
                val c = corrected ?: return@Button
                vm.runFlowCorrection(correction.reverseFlow(c))
            }) { Text("Reverse") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val c = corrected ?: return@Button
                vm.runFlowCorrection(correction.swapStartEnd(c))
            }) { Text("Swap") }
            Button(onClick = {
                val c = corrected ?: return@Button
                vm.runFlowCorrection(correction.lockFlow(c, true))
            }) { Text("Lock") }
            Button(onClick = {
                val c = corrected ?: return@Button
                vm.runFlowCorrection(correction.unlockFlow(c))
            }) { Text("Unlock") }
        }
    }
}
