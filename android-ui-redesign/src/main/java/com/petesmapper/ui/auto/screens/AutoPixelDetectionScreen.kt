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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.PixelDetectionEngine
import com.petesmapper.ui.auto.PixelNode
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoPixelDetectionScreen(vm: AutoWorkflowViewModel) {
    val s by vm.state.collectAsState()
    val engine = remember { PixelDetectionEngine() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var density by remember { mutableFloatStateOf(150f) }

    val nodes = s.pixelNodeMap?.nodes.orEmpty()
    val spacing = s.pixelNodeMap?.averageSpacing ?: 0f
    val suspicious = nodes.zipWithNext().mapIndexedNotNull { i, pair ->
        val d = kotlin.math.sqrt((pair.second.x - pair.first.x) * (pair.second.x - pair.first.x) + (pair.second.y - pair.first.y) * (pair.second.y - pair.first.y))
        if (spacing > 0f && d > spacing * 2f) i else null
    }.toSet()

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Pixel Detection")
        Text("Average spacing: ${"%.2f".format(spacing)}")

        Card(Modifier.fillMaxWidth().height(360.dp)) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111111))
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
                nodes.forEachIndexed { i, n ->
                    val x = n.x * zoom + panX
                    val y = n.y * zoom + panY
                    val c = when {
                        i == selectedIndex -> Color.Yellow
                        i in suspicious -> Color.Red
                        else -> Color.Cyan
                    }
                    drawCircle(c, radius = 4.5f, center = Offset(x, y))
                    drawContext.canvas.nativeCanvas.drawText(i.toString(), x + 6f, y - 6f, android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 20f
                    })
                }
            }
        }

        Text("Density threshold")
        Slider(value = density, onValueChange = { density = it }, valueRange = 100f..240f)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.runPixelDetection() }) { Text("Re-run Detection") }
            Button(onClick = {
                val list = nodes.toMutableList()
                list.add(PixelNode(x = 0f, y = 0f, brightness = 255f, index = list.size))
                vm.setPixelNodeMapForUi(list, engine.calculatePixelSpacing(list))
            }) { Text("Add Pixel") }
            Button(onClick = {
                if (selectedIndex >= 0 && selectedIndex < nodes.size) {
                    val list = nodes.toMutableList(); list.removeAt(selectedIndex)
                    vm.setPixelNodeMapForUi(list.mapIndexed { i, p -> p.copy(index = i) }, engine.calculatePixelSpacing(list))
                    selectedIndex = -1
                }
            }) { Text("Remove Pixel") }
        }

        Text("Duplicates removed: highlighted when re-run (brightness tie-break)")
        Text("Suspicious spacing: red nodes")
    }
}
