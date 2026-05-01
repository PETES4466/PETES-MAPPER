package com.petesmapper.ui.auto

import com.petesmapper.ui.geometry.JumpConnection
import com.petesmapper.ui.geometry.RouteMetadata
import com.petesmapper.ui.geometry.RouteNode
import com.petesmapper.ui.geometry.RoutePatternId
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.RouteSegmentType
import com.petesmapper.ui.geometry.ShapeClass
import com.petesmapper.ui.geometry.Vec2

class ReverseRouteBuilder {

    fun buildRoutePlan(
        stripPathModel: StripPathModel,
        pixelNodeMap: PixelNodeMap,
        flowDirectionModel: FlowDirectionModel,
    ): RoutePlan {
        val orderedPixels = orderByFlow(pixelNodeMap.nodes, flowDirectionModel.flowDirection)
        val routeNodes = orderedPixels.mapIndexed { index, node ->
            RouteNode(id = index, point = Vec2(node.x, node.y))
        }

        val startNode = resolveStart(routeNodes, flowDirectionModel)
        val endNode = resolveEnd(routeNodes, flowDirectionModel)

        val segments = buildSegments(stripPathModel, routeNodes, flowDirectionModel.flowDirection)
        val jumpConnections = buildJumpConnections(stripPathModel, routeNodes)
        val jumpNodeIds = jumpConnections.flatMap { listOf(it.fromNodeId, it.toNodeId) }.toSet()
        val jumpNodes = routeNodes.filter { it.id in jumpNodeIds }

        val metadata = RouteMetadata(
            patternId = RoutePatternId.RPL_CURVE_FOLLOW,
            shapeClass = ShapeClass.HYBRID,
            continuousStripPriority = true,
            cutMidwayAvoided = true,
            nearestReconnectUsed = jumpConnections.isNotEmpty(),
            islandsRespected = true,
            avoidedVoidCrossing = true,
            installerFlowScore = flowDirectionModel.confidence,
            segmentCount = segments.size,
            note = "Reverse route built from AUTO detection pipeline.",
        )

        return RoutePlan(
            orderedRouteNodes = routeNodes,
            startNode = startNode,
            endNode = endNode,
            jumpNodes = jumpNodes,
            jumpConnections = jumpConnections,
            routeSegments = segments,
            metadata = metadata,
        )
    }

    private fun orderByFlow(nodes: List<PixelNode>, direction: FlowDirection): List<PixelNode> {
        val ordered = nodes.sortedBy { it.index }
        return if (direction == FlowDirection.REVERSE) ordered.reversed() else ordered
    }

    private fun resolveStart(nodes: List<RouteNode>, flowDirectionModel: FlowDirectionModel): RouteNode {
        if (nodes.isEmpty()) return RouteNode(0, Vec2(0f, 0f))
        val target = flowDirectionModel.startNode
        return if (target == null) nodes.first() else {
            nodes.minByOrNull { n -> sq(n.point.x - target.x) + sq(n.point.y - target.y) } ?: nodes.first()
        }
    }

    private fun resolveEnd(nodes: List<RouteNode>, flowDirectionModel: FlowDirectionModel): RouteNode {
        if (nodes.isEmpty()) return RouteNode(0, Vec2(0f, 0f))
        val target = flowDirectionModel.endNode
        return if (target == null) nodes.last() else {
            nodes.minByOrNull { n -> sq(n.point.x - target.x) + sq(n.point.y - target.y) } ?: nodes.last()
        }
    }

    private fun buildSegments(
        stripPathModel: StripPathModel,
        routeNodes: List<RouteNode>,
        flowDirection: FlowDirection,
    ): List<RouteSegment> {
        if (routeNodes.isEmpty()) {
            return listOf(RouteSegment(RouteSegmentType.LOOP, contourIndex = 0, points = listOf(Vec2(0f, 0f))))
        }

        val autoSegments = if (flowDirection == FlowDirection.REVERSE) stripPathModel.segments.reversed() else stripPathModel.segments
        val routePoints = routeNodes.map { it.point }
        var cursor = 0

        return autoSegments.mapIndexed { idx, seg ->
            val size = seg.points.size.coerceAtLeast(2).coerceAtMost(routePoints.size - cursor).coerceAtLeast(1)
            val pts = routePoints.subList(cursor, (cursor + size).coerceAtMost(routePoints.size))
            cursor = (cursor + size - 1).coerceAtLeast(cursor)

            RouteSegment(
                type = when (seg.type) {
                    StripSegmentType.STRAIGHT -> RouteSegmentType.BAR
                    StripSegmentType.CURVE -> RouteSegmentType.LOOP
                    StripSegmentType.JUMP -> RouteSegmentType.BRIDGE
                    StripSegmentType.TURN -> RouteSegmentType.VALLEY
                },
                contourIndex = idx,
                points = if (pts.size >= 2) pts else listOf(pts.first(), pts.first()),
            )
        }
    }

    private fun buildJumpConnections(stripPathModel: StripPathModel, routeNodes: List<RouteNode>): List<JumpConnection> {
        if (routeNodes.size < 2) return emptyList()
        val jumps = mutableListOf<JumpConnection>()
        val jumpSegments = stripPathModel.segments.filter { it.type == StripSegmentType.JUMP }

        jumpSegments.forEach { seg ->
            val start = seg.points.firstOrNull() ?: return@forEach
            val end = seg.points.lastOrNull() ?: return@forEach
            val from = nearestNode(start.first, start.second, routeNodes) ?: return@forEach
            val to = nearestNode(end.first, end.second, routeNodes) ?: return@forEach
            if (from.id == to.id) return@forEach
            jumps += JumpConnection(
                fromNodeId = from.id,
                toNodeId = to.id,
                wireLength = distance(from.point.x, from.point.y, to.point.x, to.point.y),
            )
        }
        return jumps.distinctBy { "${it.fromNodeId}->${it.toNodeId}" }
    }

    private fun nearestNode(x: Float, y: Float, nodes: List<RouteNode>): RouteNode? {
        return nodes.minByOrNull { n -> sq(n.point.x - x) + sq(n.point.y - y) }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return kotlin.math.sqrt(sq(ax - bx) + sq(ay - by))
    }

    private fun sq(v: Float): Float = v * v
}
