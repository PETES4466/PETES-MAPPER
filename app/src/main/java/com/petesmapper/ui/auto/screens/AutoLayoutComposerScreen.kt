package com.petesmapper.ui.auto.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.auto.AutoLayoutComposer
import com.petesmapper.ui.auto.AutoLetterUnit
import com.petesmapper.ui.auto.AutoSignLayout
import com.petesmapper.ui.auto.viewmodel.AutoWorkflowViewModel

@Composable
fun AutoLayoutComposerScreen(
    vm: AutoWorkflowViewModel,
    modifier: Modifier = Modifier,
    onLayoutComposed: (AutoSignLayout) -> Unit = {},
) {
    val composer = remember { AutoLayoutComposer() }
    var layout by remember { mutableStateOf(composer.compose(emptyList())) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var spacingX by remember { mutableFloatStateOf(20f) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("AUTO Layout Composer", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141414))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(0.25f, 5f)
                            panX += pan.x
                            panY += pan.y
                        }
                    }
                    .pointerInput(layout, selectedIndex) {
                        detectTapGestures { tap ->
                            selectedIndex = hitTest(layout, tap, zoom, panX, panY)
                        }
                    }
                    .pointerInput(layout, selectedIndex) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            if (selectedIndex >= 0) {
                                layout = composer.moveLetterUnit(layout, selectedIndex, dragAmount.x / zoom, dragAmount.y / zoom)
                            }
                        }
                    },
            ) {
                layout.letterUnits.forEachIndexed { i, unit ->
                    val color = if (i == selectedIndex) Color(0xFFFFC107) else Color(0xFF4FC3F7)
                    drawLetterUnit(unit, color, zoom, panX, panY, showBounds = true)
                }
                // preview merged layout overlay
                val merged = layout.combinedRoutePlan.orderedRouteNodes
                if (merged.size > 1) {
                    val p = Path()
                    val first = merged.first().point
                    p.moveTo(first.x * zoom + panX, first.y * zoom + panY)
                    merged.drop(1).forEach { n -> p.lineTo(n.point.x * zoom + panX, n.point.y * zoom + panY) }
                    drawPath(p, Color(0x88FF5252), style = Stroke(width = 2f))
                    drawCircle(Color.Green, radius = 5f, center = Offset(merged.first().point.x * zoom + panX, merged.first().point.y * zoom + panY))
                    drawCircle(Color.Red, radius = 5f, center = Offset(merged.last().point.x * zoom + panX, merged.last().point.y * zoom + panY))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val route = vm.state.value.routePlan ?: return@Button
                val offset = layout.letterUnits.size * spacingX
                layout = composer.addLetterUnit(layout, AutoLetterUnit(routePlan = route, offsetX = offset, offsetY = 0f))
            }) { Text("Add Letter") }
            Button(onClick = {
                if (selectedIndex >= 0) layout = composer.removeLetterUnit(layout, selectedIndex)
            }) { Text("Remove") }
            Button(onClick = {
                if (selectedIndex >= 0) {
                    val u = layout.letterUnits[selectedIndex]
                    layout = composer.addLetterUnit(layout, u.copy(offsetX = u.offsetX + 30f, offsetY = u.offsetY + 15f))
                }
            }) { Text("Duplicate") }
            Button(onClick = { layout = composer.alignBaseline(layout) }) { Text("Align Baseline") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (selectedIndex > 0) {
                    val list = layout.letterUnits.toMutableList(); val t = list[selectedIndex]; list[selectedIndex] = list[selectedIndex - 1]; list[selectedIndex - 1] = t
                    layout = composer.compose(list); selectedIndex -= 1
                }
            }) { Text("Layer Up") }
            Button(onClick = {
                if (selectedIndex >= 0 && selectedIndex < layout.letterUnits.lastIndex) {
                    val list = layout.letterUnits.toMutableList(); val t = list[selectedIndex]; list[selectedIndex] = list[selectedIndex + 1]; list[selectedIndex + 1] = t
                    layout = composer.compose(list); selectedIndex += 1
                }
            }) { Text("Layer Down") }
            Button(onClick = { onLayoutComposed(layout) }) { Text("Merge for Export") }
        }

        if (selectedIndex >= 0 && selectedIndex < layout.letterUnits.size) {
            val unit = layout.letterUnits[selectedIndex]
            Text("Scale")
            Slider(value = unit.scale, onValueChange = { layout = composer.scaleLetterUnit(layout, selectedIndex, it) }, valueRange = 0.2f..3f)
            Text("Rotation")
            Slider(value = unit.rotation, onValueChange = { layout = composer.rotateLetterUnit(layout, selectedIndex, it) }, valueRange = -180f..180f)
        }

        Text("Spacing")
        Slider(value = spacingX, onValueChange = { spacingX = it }, valueRange = 0f..120f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLetterUnit(
    unit: AutoLetterUnit,
    color: Color,
    zoom: Float,
    panX: Float,
    panY: Float,
    showBounds: Boolean,
) {
    val points = unit.routePlan.orderedRouteNodes.map { it.point }
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x * unit.scale * zoom + panX + unit.offsetX * zoom, points.first().y * unit.scale * zoom + panY + unit.offsetY * zoom)
        points.drop(1).forEach { p -> lineTo(p.x * unit.scale * zoom + panX + unit.offsetX * zoom, p.y * unit.scale * zoom + panY + unit.offsetY * zoom) }
    }
    drawPath(path, color, style = Stroke(width = 2f))

    if (showBounds) {
        val minX = points.minOf { it.x } * unit.scale * zoom + panX + unit.offsetX * zoom
        val maxX = points.maxOf { it.x } * unit.scale * zoom + panX + unit.offsetX * zoom
        val minY = points.minOf { it.y } * unit.scale * zoom + panY + unit.offsetY * zoom
        val maxY = points.maxOf { it.y } * unit.scale * zoom + panY + unit.offsetY * zoom
        drawRect(Color(0x66FFFFFF), topLeft = Offset(minX, minY), size = androidx.compose.ui.geometry.Size(maxX - minX, maxY - minY), style = Stroke(width = 1f))
    }
}

private fun hitTest(layout: AutoSignLayout, tap: Offset, zoom: Float, panX: Float, panY: Float): Int {
    return layout.letterUnits.indexOfLast { unit ->
        val pts = unit.routePlan.orderedRouteNodes.map { it.point }
        if (pts.isEmpty()) return@indexOfLast false
        val minX = pts.minOf { it.x } * unit.scale * zoom + panX + unit.offsetX * zoom
        val maxX = pts.maxOf { it.x } * unit.scale * zoom + panX + unit.offsetX * zoom
        val minY = pts.minOf { it.y } * unit.scale * zoom + panY + unit.offsetY * zoom
        val maxY = pts.maxOf { it.y } * unit.scale * zoom + panY + unit.offsetY * zoom
        tap.x in minX..maxX && tap.y in minY..maxY
    }
}
