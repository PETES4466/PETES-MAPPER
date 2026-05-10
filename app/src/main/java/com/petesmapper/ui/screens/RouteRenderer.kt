package com.petesmapper.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.RouteSegmentType
import com.petesmapper.ui.geometry.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 8f

@Stable
class RouteViewportState(
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
) {
    var zoom by mutableStateOf(zoom)
    var panX by mutableStateOf(panX)
    var panY by mutableStateOf(panY)

    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    fun update(zoomDelta: Float, panDelta: Offset) {
        zoom = (zoom * zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX += panDelta.x
        panY += panDelta.y
    }
}

@Composable
fun rememberRouteViewportState(): RouteViewportState = remember { RouteViewportState() }

@Composable
fun RouteRenderer(
    routePlan: RoutePlan,
    modifier: Modifier = Modifier,
    lockedSegments: Set<Int> = emptySet(),
    viewportState: RouteViewportState = rememberRouteViewportState(),
    editable: Boolean = false,
    onNodeDragged: ((nodeId: Int, worldPosition: Vec2) -> Unit)? = null,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeDragNodeId by remember { mutableIntStateOf(-1) }

    val mapper = remember(routePlan, canvasSize, viewportState.zoom, viewportState.panX, viewportState.panY) {
        RouteTransformMapper(routePlan, canvasSize, viewportState)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .onSizeChanged { canvasSize = it }
            .background(Color(0xFF121820))
            .pointerInput(routePlan, editable) {
                detectTapGestures(
                    onDoubleTap = { viewportState.reset() },
                    onTap = { pos ->
                        if (editable) {
                            activeDragNodeId = hitNode(routePlan, mapper, pos) ?: -1
                        }
                    },
                )
            }
            .pointerInput(routePlan, editable) {
                detectDragGestures(
                    onDragStart = { pos ->
                        if (editable) activeDragNodeId = hitNode(routePlan, mapper, pos) ?: -1
                    },
                    onDragEnd = { activeDragNodeId = -1 },
                    onDragCancel = { activeDragNodeId = -1 },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (editable && activeDragNodeId >= 0 && onNodeDragged != null) {
                            val world = mapper.screenToWorld(change.position)
                            onNodeDragged.invoke(activeDragNodeId, world)
                        } else {
                            viewportState.update(zoomDelta = 1f, panDelta = dragAmount)
                        }
                    },
                )
            }
            .pointerInput(routePlan) {
                detectTransformGestures { _, pan, zoom, _ ->
                    viewportState.update(zoomDelta = zoom, panDelta = pan)
                }
            },
    ) {
        if (routePlan.orderedRouteNodes.isEmpty()) return@Canvas

        routePlan.routeSegments.forEachIndexed { idx, segment ->
            val color = segmentColor(segment, idx)
            val stroke = if (idx in lockedSegments) 4.2f else 2.8f
            if (segment.points.size >= 2) {
                segment.points.zipWithNext().forEach { (a, b) ->
                    val p1 = mapper.worldToScreen(a)
                    val p2 = mapper.worldToScreen(b)
                    drawLine(color = color, start = p1, end = p2, strokeWidth = stroke)
                    drawArrow(p1, p2, color)
                }
            }
            if (idx in lockedSegments && segment.points.isNotEmpty()) {
                segment.points.forEach { p ->
                    drawCircle(color = Color.Yellow, radius = 2.5f, center = mapper.worldToScreen(p), style = Stroke(width = 1.5f))
                }
            }
        }

        val jumpSet = routePlan.jumpNodes.map { it.id }.toSet()
        routePlan.orderedRouteNodes.zipWithNext().forEach { (a, b) ->
            if (a.id in jumpSet || b.id in jumpSet) {
                drawLine(
                    color = Color(0xFFFFC107),
                    start = mapper.worldToScreen(a.point),
                    end = mapper.worldToScreen(b.point),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                )
            }
        }

        drawCircle(color = Color(0xFF4CAF50), radius = 5f, center = mapper.worldToScreen(routePlan.startNode.point))
        drawCircle(color = Color(0xFFF44336), radius = 5f, center = mapper.worldToScreen(routePlan.endNode.point))
    }
}

private class RouteTransformMapper(
    private val plan: RoutePlan,
    private val size: IntSize,
    private val viewportState: RouteViewportState,
) {
    private val points = plan.orderedRouteNodes.map { it.point }
    private val minX = points.minOfOrNull { it.x } ?: 0f
    private val maxX = points.maxOfOrNull { it.x } ?: 1f
    private val minY = points.minOfOrNull { it.y } ?: 0f
    private val maxY = points.maxOfOrNull { it.y } ?: 1f

    private val pad = 16f
    private val width = (maxX - minX).coerceAtLeast(1f)
    private val height = (maxY - minY).coerceAtLeast(1f)
    private val baseScale = if (size.width == 0 || size.height == 0) 1f
    else minOf((size.width - pad * 2) / width, (size.height - pad * 2) / height)

    fun worldToScreen(p: Vec2): Offset {
        val sx = ((p.x - minX) * baseScale * viewportState.zoom) + pad + viewportState.panX
        val sy = ((p.y - minY) * baseScale * viewportState.zoom) + pad + viewportState.panY
        return Offset(sx, sy)
    }

    fun screenToWorld(o: Offset): Vec2 {
        val x = ((o.x - pad - viewportState.panX) / (baseScale * viewportState.zoom)) + minX
        val y = ((o.y - pad - viewportState.panY) / (baseScale * viewportState.zoom)) + minY
        return Vec2(x, y)
    }
}

private fun hitNode(plan: RoutePlan, mapper: RouteTransformMapper, pointer: Offset): Int? {
    return plan.orderedRouteNodes
        .minByOrNull { node ->
            val p = mapper.worldToScreen(node.point)
            val dx = p.x - pointer.x
            val dy = p.y - pointer.y
            (dx * dx) + (dy * dy)
        }
        ?.takeIf {
            val p = mapper.worldToScreen(it.point)
            val dx = p.x - pointer.x
            val dy = p.y - pointer.y
            ((dx * dx) + (dy * dy)) <= 220f
        }
        ?.id
}

private fun segmentColor(segment: RouteSegment, index: Int): Color = when (segment.type) {
    RouteSegmentType.PILLAR -> Color(0xFF29B6F6)
    RouteSegmentType.BAR -> Color(0xFFFFB74D)
    RouteSegmentType.VALLEY -> Color(0xFFBA68C8)
    RouteSegmentType.LOOP -> Color(0xFF81C784)
    RouteSegmentType.TAIL -> Color(0xFFE57373)
    RouteSegmentType.BRIDGE -> Color(0xFFFFD54F)
    RouteSegmentType.ISLAND -> Color(0xFF64B5F6)
}.copy(alpha = if (index % 2 == 0) 0.95f else 0.8f)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(start: Offset, end: Offset, color: Color) {
    val angle = atan2((end.y - start.y), (end.x - start.x))
    val arrowSize = 8f
    val p1 = Offset(end.x - arrowSize * cos(angle - 0.45f), end.y - arrowSize * sin(angle - 0.45f))
    val p2 = Offset(end.x - arrowSize * cos(angle + 0.45f), end.y - arrowSize * sin(angle + 0.45f))
    drawLine(color = color, start = end, end = p1, strokeWidth = 1.5f)
    drawLine(color = color, start = end, end = p2, strokeWidth = 1.5f)
}
