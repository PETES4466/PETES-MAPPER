package com.petesmapper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.geometry.RouteNode
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.RouteSegmentType
import com.petesmapper.ui.geometry.Vec2

data class ManualEditSnapshot(
    val plan: RoutePlan,
    val lockedSegments: Set<Int>,
)

class ManualRouteEditorState(initialPlan: RoutePlan) {
    var plan by mutableStateOf(initialPlan)
        private set
    var warningMessage by mutableStateOf<String?>(null)
        private set

    private val lockedSegmentsState = mutableStateListOf<Int>()
    val lockedSegments: Set<Int> get() = lockedSegmentsState.toSet()

    private val undoStack = ArrayDeque<ManualEditSnapshot>()
    private val redoStack = ArrayDeque<ManualEditSnapshot>()

    private fun snapshot() = ManualEditSnapshot(plan, lockedSegments)
    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun updatePlan(updated: RoutePlan) {
        pushUndo()
        plan = updated
        warningMessage = null
    }

    fun dragRouteNode(nodeId: Int, newPosition: Vec2) {
        val nodes = plan.orderedRouteNodes.map { if (it.id == nodeId) RouteNode(it.id, newPosition) else it }
        updatePlan(rebuildPlan(nodes = nodes))
    }

    fun changeStartNode(nodeId: Int) {
        val idx = plan.orderedRouteNodes.indexOfFirst { it.id == nodeId }
        if (idx <= 0) return
        val rotated = plan.orderedRouteNodes.drop(idx) + plan.orderedRouteNodes.take(idx)
        updatePlan(rebuildPlan(nodes = rotated.mapIndexed { i, n -> RouteNode(i, n.point) }))
    }

    fun changeEndNode(nodeId: Int) {
        val idx = plan.orderedRouteNodes.indexOfFirst { it.id == nodeId }
        if (idx < 0) return
        val nodes = plan.orderedRouteNodes.take(idx + 1)
        updatePlan(rebuildPlan(nodes = nodes.mapIndexed { i, n -> RouteNode(i, n.point) }))
    }

    fun reverseSegmentDirection(segmentIndex: Int) {
        if (segmentIndex in lockedSegments) return
        val segments = plan.routeSegments.toMutableList()
        val seg = segments.getOrNull(segmentIndex) ?: return
        segments[segmentIndex] = seg.copy(points = seg.points.reversed())
        updatePlan(rebuildPlan(segments = segments))
    }

    fun reconnectBridgePoints(segmentIndex: Int, from: Vec2, to: Vec2) {
        if (segmentIndex in lockedSegments) return
        val segments = plan.routeSegments.toMutableList()
        val seg = segments.getOrNull(segmentIndex) ?: return
        if (seg.type != RouteSegmentType.BRIDGE) return
        segments[segmentIndex] = seg.copy(points = listOf(from, to))
        updatePlan(rebuildPlan(segments = segments))
    }

    fun insertJumpWire(afterNodeId: Int, jumpPoint: Vec2) {
        val idx = plan.orderedRouteNodes.indexOfFirst { it.id == afterNodeId }
        if (idx < 0) return
        val mutable = plan.orderedRouteNodes.toMutableList()
        mutable.add(idx + 1, RouteNode(-1, jumpPoint))
        updatePlan(rebuildPlan(nodes = mutable.mapIndexed { i, n -> RouteNode(i, n.point) }))
    }

    fun splitStripManually(segmentIndex: Int, force: Boolean = false) {
        if (segmentIndex in lockedSegments) return
        val seg = plan.routeSegments.getOrNull(segmentIndex) ?: return
        if (seg.points.size < 4) {
            warningMessage = "Segment is too short to split safely."
            return
        }
        if (!force) {
            warningMessage = "Manual split can break continuity. Use force=true to confirm."
            return
        }
        val mid = seg.points.size / 2
        val first = seg.copy(points = seg.points.take(mid))
        val second = seg.copy(points = seg.points.drop(mid))
        val list = plan.routeSegments.toMutableList().also {
            it.removeAt(segmentIndex)
            it.add(segmentIndex, second)
            it.add(segmentIndex, first)
        }
        updatePlan(rebuildPlan(segments = list))
    }

    fun deleteSegment(segmentIndex: Int) {
        if (segmentIndex in lockedSegments) return
        val segments = plan.routeSegments.toMutableList()
        if (segmentIndex !in segments.indices) return
        segments.removeAt(segmentIndex)
        updatePlan(rebuildPlan(segments = segments))
    }

    fun reorderSegment(fromIndex: Int, toIndex: Int) {
        if (fromIndex in lockedSegments) return
        val segments = plan.routeSegments.toMutableList()
        if (fromIndex !in segments.indices || toIndex !in segments.indices) return
        val moved = segments.removeAt(fromIndex)
        segments.add(toIndex, moved)
        updatePlan(rebuildPlan(segments = segments))
    }

    fun lockSegment(segmentIndex: Int, locked: Boolean) {
        pushUndo()
        if (locked) lockedSegmentsState.add(segmentIndex) else lockedSegmentsState.remove(segmentIndex)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        plan = prev.plan
        lockedSegmentsState.clear(); lockedSegmentsState.addAll(prev.lockedSegments)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        plan = next.plan
        lockedSegmentsState.clear(); lockedSegmentsState.addAll(next.lockedSegments)
    }

    private fun rebuildPlan(
        nodes: List<RouteNode> = plan.orderedRouteNodes,
        segments: List<RouteSegment> = plan.routeSegments,
    ): RoutePlan {
        val remapped = nodes.mapIndexed { i, n -> RouteNode(i, n.point) }
        val start = remapped.firstOrNull() ?: RouteNode(0, Vec2(0f, 0f))
        val end = remapped.lastOrNull() ?: start
        return plan.copy(orderedRouteNodes = remapped, startNode = start, endNode = end, routeSegments = segments)
    }
}

@Composable
fun ManualRouteEditor(
    initialPlan: RoutePlan,
    modifier: Modifier = Modifier,
) {
    val editor = remember(initialPlan) { ManualRouteEditorState(initialPlan) }
    val viewport = rememberRouteViewportState()
    var selectedNodeId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Manual Route Editor", style = MaterialTheme.typography.titleMedium)
        editor.warningMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        BoxWithConstraints {
            val node = selectedNodeId?.let { id -> editor.plan.orderedRouteNodes.find { it.id == id } }
            val nodeScreenPos = node?.let { mapNodeToScreen(editor.plan, viewport, maxWidth.value, 260f, it.point) }

            Box {
                RouteRenderer(
                    routePlan = editor.plan,
                    viewportState = viewport,
                    editable = true,
                    lockedSegments = editor.lockedSegments,
                    onNodeDragged = { nodeId, world ->
                        selectedNodeId = nodeId
                        editor.dragRouteNode(nodeId, world)
                    },
                )

                if (selectedNodeId != null && nodeScreenPos != null) {
                    val nodeId = selectedNodeId!!
                    val segmentIndex = findSegmentForNode(editor.plan, nodeId)
                    NodeToolOverlay(
                        modifier = Modifier.offset {
                            IntOffset(nodeScreenPos.first.toInt() + 12, nodeScreenPos.second.toInt() - 12)
                        },
                        onSetStart = { editor.changeStartNode(nodeId) },
                        onSetEnd = { editor.changeEndNode(nodeId) },
                        onInsertJump = {
                            val current = editor.plan.orderedRouteNodes.find { it.id == nodeId }?.point ?: Vec2(0f, 0f)
                            editor.insertJumpWire(nodeId, Vec2(current.x + 8f, current.y + 8f))
                        },
                        onSplitRoute = { if (segmentIndex >= 0) editor.splitStripManually(segmentIndex, force = true) },
                        onLockSegment = {
                            if (segmentIndex >= 0) editor.lockSegment(segmentIndex, !editor.lockedSegments.contains(segmentIndex))
                        },
                        onDeleteSegment = { if (segmentIndex >= 0) editor.deleteSegment(segmentIndex) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editor.undo() }) { Text("Undo") }
            Button(onClick = { editor.redo() }) { Text("Redo") }
            Button(onClick = { viewport.reset() }) { Text("Reset View") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(editor.plan.routeSegments) { index, segment ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Segment #$index • ${segment.type}")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { editor.reverseSegmentDirection(index) }) { Text("Reverse") }
                            Button(onClick = { editor.lockSegment(index, !editor.lockedSegments.contains(index)) }) { Text(if (editor.lockedSegments.contains(index)) "Unlock" else "Lock") }
                            Button(onClick = { editor.deleteSegment(index) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeToolOverlay(
    modifier: Modifier = Modifier,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onInsertJump: () -> Unit,
    onSplitRoute: () -> Unit,
    onLockSegment: () -> Unit,
    onDeleteSegment: () -> Unit,
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onSetStart) { Text("Set Start") }
            Button(onClick = onSetEnd) { Text("Set End") }
            Button(onClick = onInsertJump) { Text("Insert Jump") }
            Button(onClick = onSplitRoute) { Text("Split Route") }
            Button(onClick = onLockSegment) { Text("Lock Segment") }
            Button(onClick = onDeleteSegment) { Text("Delete Segment") }
        }
    }
}

private fun findSegmentForNode(plan: RoutePlan, nodeId: Int): Int {
    val node = plan.orderedRouteNodes.find { it.id == nodeId } ?: return -1
    return plan.routeSegments.indexOfFirst { segment ->
        segment.points.any { p -> kotlin.math.abs(p.x - node.point.x) < 0.5f && kotlin.math.abs(p.y - node.point.y) < 0.5f }
    }
}

private fun mapNodeToScreen(plan: RoutePlan, viewport: RouteViewportState, canvasWidth: Float, canvasHeight: Float, node: Vec2): Pair<Float, Float> {
    val points = plan.orderedRouteNodes.map { it.point }
    val minX = points.minOfOrNull { it.x } ?: 0f
    val maxX = points.maxOfOrNull { it.x } ?: 1f
    val minY = points.minOfOrNull { it.y } ?: 0f
    val maxY = points.maxOfOrNull { it.y } ?: 1f

    val pad = 16f
    val width = (maxX - minX).coerceAtLeast(1f)
    val height = (maxY - minY).coerceAtLeast(1f)
    val baseScale = minOf((canvasWidth - pad * 2) / width, (canvasHeight - pad * 2) / height)

    val x = ((node.x - minX) * baseScale * viewport.zoom) + pad + viewport.panX
    val y = ((node.y - minY) * baseScale * viewport.zoom) + pad + viewport.panY
    return x to y
}
