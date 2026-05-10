package com.petesmapper.ui.auto

import com.petesmapper.ui.geometry.JumpConnection
import com.petesmapper.ui.geometry.RouteMetadata
import com.petesmapper.ui.geometry.RouteNode
import com.petesmapper.ui.geometry.RoutePatternId
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.ShapeClass
import com.petesmapper.ui.geometry.Vec2
import kotlin.math.cos
import kotlin.math.sin

data class AutoLetterUnit(
    val routePlan: RoutePlan,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
)

data class AutoBounds(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
)

data class AutoSignLayout(
    val letterUnits: List<AutoLetterUnit>,
    val combinedRoutePlan: RoutePlan,
    val overallBounds: AutoBounds,
)

class AutoLayoutComposer {

    fun addLetterUnit(layout: AutoSignLayout, unit: AutoLetterUnit): AutoSignLayout {
        val next = layout.letterUnits + unit
        return compose(next)
    }

    fun removeLetterUnit(layout: AutoSignLayout, index: Int): AutoSignLayout {
        val next = layout.letterUnits.toMutableList().apply { if (index in indices) removeAt(index) }
        return compose(next)
    }

    fun moveLetterUnit(layout: AutoSignLayout, index: Int, dx: Float, dy: Float): AutoSignLayout {
        val next = layout.letterUnits.toMutableList()
        if (index in next.indices) {
            val u = next[index]
            next[index] = u.copy(offsetX = u.offsetX + dx, offsetY = u.offsetY + dy)
        }
        return compose(next)
    }

    fun scaleLetterUnit(layout: AutoSignLayout, index: Int, scale: Float): AutoSignLayout {
        val next = layout.letterUnits.toMutableList()
        if (index in next.indices) {
            val u = next[index]
            next[index] = u.copy(scale = scale.coerceAtLeast(0.01f))
        }
        return compose(next)
    }

    fun rotateLetterUnit(layout: AutoSignLayout, index: Int, rotation: Float): AutoSignLayout {
        val next = layout.letterUnits.toMutableList()
        if (index in next.indices) next[index] = next[index].copy(rotation = rotation)
        return compose(next)
    }

    fun alignBaseline(layout: AutoSignLayout, baselineY: Float = 0f): AutoSignLayout {
        val next = layout.letterUnits.map { unit ->
            val b = boundsOf(unit)
            unit.copy(offsetY = unit.offsetY + (baselineY - b.minY))
        }
        return compose(next)
    }

    fun mergeRoutePlans(units: List<AutoLetterUnit>): RoutePlan = compose(units).combinedRoutePlan

    fun compose(units: List<AutoLetterUnit>): AutoSignLayout {
        if (units.isEmpty()) {
            val node = RouteNode(0, Vec2(0f, 0f))
            val empty = RoutePlan(emptyList(), node, node, emptyList(), emptyList(), emptyList(),
                RouteMetadata(RoutePatternId.RPL_LINEAR, ShapeClass.LINEAR, true, true, false, true, true, 0f, 0, "Empty AUTO layout"))
            return AutoSignLayout(emptyList(), empty, AutoBounds(0f, 0f, 0f, 0f))
        }

        val transformedNodes = mutableListOf<RouteNode>()
        val transformedSegments = mutableListOf<RouteSegment>()
        val transformedJumps = mutableListOf<JumpConnection>()
        var nodeOffset = 0

        units.forEach { unit ->
            val transformed = transformRoutePlan(unit)
            val nodeMap = transformed.orderedRouteNodes.mapIndexed { i, n -> n.id to (i + nodeOffset) }.toMap()

            transformed.orderedRouteNodes.forEachIndexed { i, n ->
                transformedNodes += RouteNode(id = i + nodeOffset, point = n.point)
            }
            transformed.routeSegments.forEach { seg -> transformedSegments += seg }
            transformed.jumpConnections.forEach { j ->
                val from = nodeMap[j.fromNodeId] ?: return@forEach
                val to = nodeMap[j.toNodeId] ?: return@forEach
                transformedJumps += JumpConnection(from, to, j.wireLength)
            }
            nodeOffset += transformed.orderedRouteNodes.size
        }

        val segmentsRebound = transformedSegments.mapIndexed { idx, seg ->
            RouteSegment(type = seg.type, contourIndex = idx, points = seg.points)
        }

        val start = transformedNodes.first()
        val end = transformedNodes.last()
        val jumpNodeIds = transformedJumps.flatMap { listOf(it.fromNodeId, it.toNodeId) }.toSet()
        val jumpNodes = transformedNodes.filter { it.id in jumpNodeIds }

        val combined = RoutePlan(
            orderedRouteNodes = transformedNodes,
            startNode = start,
            endNode = end,
            jumpNodes = jumpNodes,
            jumpConnections = transformedJumps,
            routeSegments = segmentsRebound,
            metadata = RouteMetadata(
                patternId = RoutePatternId.RPL_CURVE_FOLLOW,
                shapeClass = ShapeClass.HYBRID,
                continuousStripPriority = true,
                cutMidwayAvoided = true,
                nearestReconnectUsed = transformedJumps.isNotEmpty(),
                islandsRespected = true,
                avoidedVoidCrossing = true,
                installerFlowScore = 1f,
                segmentCount = segmentsRebound.size,
                note = "AUTO composed signage layout",
            ),
        )

        val allPoints = transformedNodes.map { it.point }
        val bounds = AutoBounds(
            minX = allPoints.minOf { it.x },
            minY = allPoints.minOf { it.y },
            maxX = allPoints.maxOf { it.x },
            maxY = allPoints.maxOf { it.y },
        )
        return AutoSignLayout(units, combined, bounds)
    }

    private fun transformRoutePlan(unit: AutoLetterUnit): RoutePlan {
        fun transform(p: Vec2): Vec2 {
            val sx = p.x * unit.scale
            val sy = p.y * unit.scale
            val r = Math.toRadians(unit.rotation.toDouble())
            val rx = (sx * cos(r) - sy * sin(r)).toFloat() + unit.offsetX
            val ry = (sx * sin(r) + sy * cos(r)).toFloat() + unit.offsetY
            return Vec2(rx, ry)
        }

        val nodes = unit.routePlan.orderedRouteNodes.map { RouteNode(it.id, transform(it.point)) }
        val start = nodes.firstOrNull() ?: RouteNode(0, Vec2(0f, 0f))
        val end = nodes.lastOrNull() ?: start
        val nodeById = nodes.associateBy { it.id }
        val jumps = unit.routePlan.jumpConnections.mapNotNull { j ->
            val from = nodeById[j.fromNodeId] ?: return@mapNotNull null
            val to = nodeById[j.toNodeId] ?: return@mapNotNull null
            JumpConnection(from.id, to.id, distance(from.point, to.point))
        }
        val jumpNodes = nodes.filter { n -> jumps.any { it.fromNodeId == n.id || it.toNodeId == n.id } }

        val segments = unit.routePlan.routeSegments.map { seg ->
            seg.copy(points = seg.points.map { transform(it) })
        }

        return unit.routePlan.copy(
            orderedRouteNodes = nodes,
            startNode = start,
            endNode = end,
            jumpNodes = jumpNodes,
            jumpConnections = jumps,
            routeSegments = segments,
        )
    }

    private fun boundsOf(unit: AutoLetterUnit): AutoBounds {
        val t = transformRoutePlan(unit)
        val pts = t.orderedRouteNodes.map { it.point }
        return AutoBounds(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
    }

    private fun distance(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
