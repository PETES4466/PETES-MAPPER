package com.petesmapper.ui.geometry

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class RoutePatternId {
    RPL_LINEAR,
    RPL_BAR_PRIORITY,
    RPL_PILLAR_BRIDGE,
    RPL_VALLEY,
    RPL_PILLAR_VALLEY_COMBO,
    RPL_SPIRAL_CLOCKWISE,
    RPL_ISLAND_WRAP,
    RPL_DOUBLE_ISLAND,
    RPL_TAIL_EXIT_BOTTOM,
    RPL_CURVE_FOLLOW,
    RPL_CROSS_OPTIONAL,
    RPL_LOOP_BRANCH,
}

enum class RouteSegmentType { PILLAR, BAR, VALLEY, LOOP, TAIL, BRIDGE, ISLAND }

enum class EntryMode {
    BOTTOM_START,
    TOP_START,
    AUTO,
}

data class RouteSegment(val type: RouteSegmentType, val contourIndex: Int, val points: List<Vec2>)
data class RouteNode(val id: Int, val point: Vec2)

data class RouteMetadata(
    val patternId: RoutePatternId,
    val shapeClass: ShapeClass,
    val continuousStripPriority: Boolean,
    val cutMidwayAvoided: Boolean,
    val nearestReconnectUsed: Boolean,
    val islandsRespected: Boolean,
    val avoidedVoidCrossing: Boolean,
    val installerFlowScore: Float,
    val segmentCount: Int,
    val note: String,
)

data class RoutePlan(
    val orderedRouteNodes: List<RouteNode>,
    val startNode: RouteNode,
    val endNode: RouteNode,
    val jumpNodes: List<RouteNode>,
    val routeSegments: List<RouteSegment>,
    val metadata: RouteMetadata,
)

class ContourWalker(private val contours: List<Contour>) {
    fun walkContour(contourIndex: Int, startPointIndex: Int = 0, clockwise: Boolean = true): List<Vec2> {
        val points = contours.getOrNull(contourIndex)?.points ?: return emptyList()
        if (points.isEmpty()) return emptyList()
        val idx = startPointIndex.mod(points.size)
        return if (clockwise) (0 until points.size).map { points[(idx + it) % points.size] }
        else (0 until points.size).map { points[(idx - it).mod(points.size)] }
    }
}

class NearestConnector(private val contours: List<Contour>) {
    data class Connection(val from: Vec2, val to: Vec2, val targetPointIndex: Int)

    fun nearestLegalConnection(fromPoint: Vec2, targetContourIndex: Int): Connection? {
        val target = contours.getOrNull(targetContourIndex)?.points ?: return null
        val ranked = target.mapIndexed { idx, p -> Triple(idx, p, distance(fromPoint, p)) }.sortedBy { it.third }
        ranked.forEach { (idx, p, _) -> if (isLegalConnection(fromPoint, p)) return Connection(fromPoint, p, idx) }
        return ranked.firstOrNull()?.let { Connection(fromPoint, it.second, it.first) }
    }

    private fun isLegalConnection(a: Vec2, b: Vec2): Boolean {
        val mid = Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val insideInner = contours.drop(1).any { pointInPolygon(mid, it.points) }
        return !insideInner
    }

    private fun pointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x; val yi = polygon[i].y
            val xj = polygon[j].x; val yj = polygon[j].y
            val denom = (yj - yi).takeIf { abs(it) > 1e-6f } ?: 1e-6f
            val intersect = ((yi > point.y) != (yj > point.y)) && (point.x < (xj - xi) * (point.y - yi) / denom + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun distance(a: Vec2, b: Vec2): Float = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
}

class RoutePatternLibrary(
    private val classificationEngine: ShapeClassificationEngine = ShapeClassificationEngine(),
    private val detector: GeometryFeatureDetector = GeometryFeatureDetector(),
) {
    fun buildRoutePlan(input: GlyphPathInput, entryMode: EntryMode = EntryMode.AUTO): RoutePlan {
        val contours = detector.contoursFromInput(input).filter { it.points.size >= 2 }
        val result = classificationEngine.classify(input)

        if (contours.isEmpty()) {
            val n = RouteNode(0, Vec2(0f, 0f))
            return RoutePlan(listOf(n), n, n, emptyList(), emptyList(),
                RouteMetadata(RoutePatternId.RPL_LINEAR, result.shapeClass, true, true, false, true, true, 0f, 0, "No contours."))
        }

        val pattern = selectPattern(result.shapeClass, result.features)
        val walker = ContourWalker(contours)
        val connector = NearestConnector(contours)
        val segments = buildSegments(pattern, contours, result.features, walker, connector, entryMode)
        val stitched = stitchSegmentsWithConnectors(segments, connector)

        val nodes = stitched.distinctBy { "${"%.4f".format(it.x)}:${"%.4f".format(it.y)}" }
            .mapIndexed { i, p -> RouteNode(i, p) }
        val jumps = detectJumpNodes(nodes)

        val metadata = RouteMetadata(
            patternId = pattern,
            shapeClass = result.shapeClass,
            continuousStripPriority = true,
            cutMidwayAvoided = jumps.size <= maxOf(1, nodes.size / 25),
            nearestReconnectUsed = jumps.isNotEmpty(),
            islandsRespected = !result.features.hasIslands || pattern in setOf(RoutePatternId.RPL_ISLAND_WRAP, RoutePatternId.RPL_DOUBLE_ISLAND),
            avoidedVoidCrossing = true,
            installerFlowScore = installerFlowScore(nodes, jumps),
            segmentCount = segments.size,
            note = "Contour topology traversal for installer fidelity.",
        )

        return RoutePlan(nodes, nodes.first(), nodes.last(), jumps, segments, metadata)
    }

    private fun selectPattern(shapeClass: ShapeClass, features: GeometryFeatures): RoutePatternId = when (shapeClass) {
        ShapeClass.LINEAR -> RoutePatternId.RPL_LINEAR
        ShapeClass.BAR -> RoutePatternId.RPL_BAR_PRIORITY
        ShapeClass.PILLAR, ShapeClass.PILLAR_BRIDGE -> RoutePatternId.RPL_PILLAR_BRIDGE
        ShapeClass.VALLEY -> RoutePatternId.RPL_VALLEY
        ShapeClass.PILLAR_VALLEY_COMBO -> RoutePatternId.RPL_PILLAR_VALLEY_COMBO
        ShapeClass.SPIRAL -> RoutePatternId.RPL_SPIRAL_CLOCKWISE
        ShapeClass.ISLAND -> if (features.islandCount > 1) RoutePatternId.RPL_DOUBLE_ISLAND else RoutePatternId.RPL_ISLAND_WRAP
        ShapeClass.TAIL_EXIT_BOTTOM -> RoutePatternId.RPL_TAIL_EXIT_BOTTOM
        ShapeClass.CROSS -> RoutePatternId.RPL_CROSS_OPTIONAL
        ShapeClass.TAIL, ShapeClass.HYBRID -> RoutePatternId.RPL_LOOP_BRANCH
    }

    private fun buildSegments(
        pattern: RoutePatternId,
        contours: List<Contour>,
        features: GeometryFeatures,
        walker: ContourWalker,
        connector: NearestConnector,
        entryMode: EntryMode,
    ): List<RouteSegment> {
        val order = contours.indices.sortedByDescending { contours[it].areaAbs() }
        return when (pattern) {
            RoutePatternId.RPL_PILLAR_BRIDGE -> buildPillarBridgeSegments(order.first(), contours, walker, connector)
            RoutePatternId.RPL_PILLAR_VALLEY_COMBO -> buildPillarValleyComboSegments(order.first(), contours, walker, connector, entryMode)
            RoutePatternId.RPL_SPIRAL_CLOCKWISE -> order.map { RouteSegment(RouteSegmentType.LOOP, it, walker.walkContour(it, clockwise = true)) }
            RoutePatternId.RPL_ISLAND_WRAP -> buildARoute(order, contours, features, walker, connector)
            RoutePatternId.RPL_TAIL_EXIT_BOTTOM -> buildQRoute(order.first(), contours, walker)
            RoutePatternId.RPL_DOUBLE_ISLAND -> buildBDoubleIslandRoute(order, contours, walker, connector)
            RoutePatternId.RPL_CROSS_OPTIONAL -> buildCrossOptional(order.first(), contours, walker)
            else -> order.map { RouteSegment(RouteSegmentType.LOOP, it, walker.walkContour(it, clockwise = true)) }
        }
    }

    // H: left pillar -> bridge(crossbar) -> right pillar -> return bridge
    private fun buildPillarBridgeSegments(
        outerIdx: Int,
        contours: List<Contour>,
        walker: ContourWalker,
        connector: NearestConnector,
    ): List<RouteSegment> {
        val pts = walker.walkContour(outerIdx, clockwise = true)
        val (left, right) = detectPillars(pts)
        if (left.isEmpty() || right.isEmpty()) return listOf(RouteSegment(RouteSegmentType.LOOP, outerIdx, pts))

        val midY = (pts.maxOf { it.y } + pts.minOf { it.y }) / 2f
        val leftAnchor = left.minByOrNull { abs(it.y - midY) } ?: left.first()
        val rightAnchor = right.minByOrNull { abs(it.y - midY) } ?: right.first()

        val bridgeConn = connector.nearestLegalConnection(leftAnchor, outerIdx)
        val bridge = RouteSegment(RouteSegmentType.BRIDGE, outerIdx, listOf(leftAnchor, rightAnchor))
        val returnBridge = RouteSegment(RouteSegmentType.BRIDGE, outerIdx, listOf(rightAnchor, bridgeConn?.to ?: leftAnchor))

        return listOf(
            RouteSegment(RouteSegmentType.PILLAR, outerIdx, left),
            bridge,
            RouteSegment(RouteSegmentType.PILLAR, outerIdx, right),
            returnBridge,
        )
    }

    // PILLAR_VALLEY_COMBO: pillar -> valley -> valley -> pillar with configurable entry mode.
    private fun buildPillarValleyComboSegments(
        outerIdx: Int,
        contours: List<Contour>,
        walker: ContourWalker,
        connector: NearestConnector,
        entryMode: EntryMode,
    ): List<RouteSegment> {
        val contour = contours[outerIdx]
        val pts = contour.points
        val valleys = detectValleyIndices(pts).take(2)
        if (valleys.size < 2) return listOf(RouteSegment(RouteSegmentType.LOOP, outerIdx, walker.walkContour(outerIdx)))

        val apexDirectionUpward = isValleyApexDirectionUpward(pts, valleys)
        val resolvedMode = when (entryMode) {
            EntryMode.BOTTOM_START -> EntryMode.BOTTOM_START // M style
            EntryMode.TOP_START -> EntryMode.TOP_START       // W style
            EntryMode.AUTO -> if (apexDirectionUpward) EntryMode.BOTTOM_START else EntryMode.TOP_START
        }

        val startIndex = when (resolvedMode) {
            EntryMode.BOTTOM_START -> pts.indices.maxByOrNull { pts[it].y } ?: valleys.first()
            EntryMode.TOP_START -> pts.indices.minByOrNull { pts[it].y } ?: valleys.first()
            EntryMode.AUTO -> valleys.first()
        }

        val path = walker.walkContour(outerIdx, startPointIndex = startIndex, clockwise = true)

        val valleyAIndex = nearestIndex(path, pts[valleys[0]])
        val valleyBIndex = nearestIndex(path, pts[valleys[1]])
        val (vFirst, vSecond) = if (valleyAIndex <= valleyBIndex) valleyAIndex to valleyBIndex else valleyBIndex to valleyAIndex

        val pillar1 = path.subList(0, maxOf(1, vFirst))
        val valley1 = sliceAroundIndex(path, vFirst)
        val valley2 = sliceAroundIndex(path, vSecond)
        val pillar2 = path.subList(minOf(path.size - 1, vSecond + 1), path.size)

        val bridge = connector.nearestLegalConnection(valley2.last(), outerIdx)?.let {
            RouteSegment(RouteSegmentType.BRIDGE, outerIdx, listOf(it.from, it.to))
        }

        return listOfNotNull(
            RouteSegment(RouteSegmentType.PILLAR, outerIdx, if (pillar1.isEmpty()) listOf(path.first()) else pillar1),
            RouteSegment(RouteSegmentType.VALLEY, outerIdx, valley1),
            RouteSegment(RouteSegmentType.VALLEY, outerIdx, valley2),
            bridge,
            RouteSegment(RouteSegmentType.PILLAR, outerIdx, if (pillar2.isEmpty()) listOf(path.last()) else pillar2),
        )
    }

    // A: outer first, bridge from nearest pillar anchor, then inner triangle
    private fun buildARoute(
        order: List<Int>,
        contours: List<Contour>,
        features: GeometryFeatures,
        walker: ContourWalker,
        connector: NearestConnector,
    ): List<RouteSegment> {
        val outer = order.first()
        val outerPts = walker.walkContour(outer, clockwise = true)
        val outerSeg = RouteSegment(RouteSegmentType.LOOP, outer, outerPts)
        if (!features.hasIslands || order.size < 2) return listOf(outerSeg)

        val inner = order[1]
        val (left, right) = detectPillars(outerPts)
        val anchorCandidates = (left + right).ifEmpty { listOf(outerPts.first()) }
        val connect = anchorCandidates.mapNotNull { a -> connector.nearestLegalConnection(a, inner) }
            .minByOrNull { distance(it.from, it.to) }

        val bridge = connect?.let { RouteSegment(RouteSegmentType.BRIDGE, outer, listOf(it.from, it.to)) }
        val innerSeg = RouteSegment(
            RouteSegmentType.ISLAND,
            inner,
            walker.walkContour(inner, startPointIndex = connect?.targetPointIndex ?: 0, clockwise = true),
        )

        return listOfNotNull(outerSeg, bridge, innerSeg)
    }

    // Q: use actual tail geometry from outlier cluster on contour.
    private fun buildQRoute(outerIdx: Int, contours: List<Contour>, walker: ContourWalker): List<RouteSegment> {
        val pts = walker.walkContour(outerIdx, clockwise = true)
        val loop = RouteSegment(RouteSegmentType.LOOP, outerIdx, pts)

        val c = centroid(pts)
        val d = pts.map { distance(it, c) }
        val mean = d.average().toFloat()
        val std = sqrt(d.map { (it - mean).pow(2) }.average()).toFloat()
        val outlierIdx = pts.indices.filter { distance(pts[it], c) > mean + std * 1.1f }
        if (outlierIdx.isEmpty()) return listOf(loop)

        val orderedTail = outlierIdx.sorted().map { pts[it] }
        val lowest = orderedTail.minByOrNull { it.y } ?: orderedTail.last()
        val tailSeg = RouteSegment(RouteSegmentType.TAIL, outerIdx, orderedTail)
        val exitSeg = RouteSegment(RouteSegmentType.BRIDGE, outerIdx, listOf(orderedTail.last(), lowest))
        return listOf(loop, tailSeg, exitSeg)
    }

    // B / 8: trunk -> upper island -> lower island
    private fun buildBDoubleIslandRoute(
        order: List<Int>,
        contours: List<Contour>,
        walker: ContourWalker,
        connector: NearestConnector,
    ): List<RouteSegment> {
        if (order.isEmpty()) return emptyList()
        val outer = order.first()
        val outerPts = walker.walkContour(outer, clockwise = true)
        val trunk = detectPillars(outerPts).first.ifEmpty { outerPts.take(maxOf(2, outerPts.size / 6)) }
        val trunkSeg = RouteSegment(RouteSegmentType.PILLAR, outer, trunk)

        if (order.size < 3) return listOf(trunkSeg, RouteSegment(RouteSegmentType.LOOP, outer, outerPts))

        val innerContours = order.drop(1).take(2).sortedByDescending { centroid(contours[it].points).y }
        val upper = innerContours[0]
        val lower = innerContours[1]

        val cUp = connector.nearestLegalConnection(trunk.last(), upper)
        val upperBridge = cUp?.let { RouteSegment(RouteSegmentType.BRIDGE, outer, listOf(it.from, it.to)) }
        val upperSeg = RouteSegment(RouteSegmentType.ISLAND, upper, walker.walkContour(upper, cUp?.targetPointIndex ?: 0, clockwise = true))

        val cDown = connector.nearestLegalConnection(upperSeg.points.last(), lower)
        val lowerBridge = cDown?.let { RouteSegment(RouteSegmentType.BRIDGE, outer, listOf(it.from, it.to)) }
        val lowerSeg = RouteSegment(RouteSegmentType.ISLAND, lower, walker.walkContour(lower, cDown?.targetPointIndex ?: 0, clockwise = true))

        return listOfNotNull(trunkSeg, upperBridge, upperSeg, lowerBridge, lowerSeg)
    }

    private fun buildCrossOptional(outerIdx: Int, contours: List<Contour>, walker: ContourWalker): List<RouteSegment> {
        val loop = walker.walkContour(outerIdx, clockwise = true)
        val center = centroid(contours[outerIdx].points)
        val radius = loop.map { distance(it, center) }.average().toFloat()
        val centerPath = loop.filter { distance(it, center) < radius * 0.45f }
        return if (centerPath.size > 3) {
            listOf(RouteSegment(RouteSegmentType.LOOP, outerIdx, loop), RouteSegment(RouteSegmentType.BAR, outerIdx, centerPath))
        } else {
            listOf(RouteSegment(RouteSegmentType.LOOP, outerIdx, loop))
        }
    }

    private fun stitchSegmentsWithConnectors(segments: List<RouteSegment>, connector: NearestConnector): List<Vec2> {
        if (segments.isEmpty()) return emptyList()
        val out = mutableListOf<Vec2>()
        segments.forEachIndexed { idx, seg ->
            if (seg.points.isEmpty()) return@forEachIndexed
            if (idx == 0) {
                out += seg.points
            } else {
                val prev = out.last()
                val conn = connector.nearestLegalConnection(prev, seg.contourIndex)
                if (conn != null) {
                    if (!same(prev, conn.to)) out += conn.to
                    out += rotateToStart(seg.points, conn.to)
                } else out += seg.points
            }
        }
        return out
    }

    private fun detectPillars(points: List<Vec2>): Pair<List<Vec2>, List<Vec2>> {
        if (points.isEmpty()) return emptyList<Vec2>() to emptyList()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val w = maxOf(1e-4f, maxX - minX)
        val left = points.filter { it.x <= minX + w * 0.18f }.sortedBy { it.y }
        val right = points.filter { it.x >= maxX - w * 0.18f }.sortedBy { it.y }
        return left to right
    }

    private fun detectValleyIndices(points: List<Vec2>): List<Int> {
        if (points.size < 5) return emptyList()
        val sign = if (signedArea(points) >= 0f) 1 else -1
        return points.indices.mapNotNull { i ->
            val p = points[(i - 1 + points.size) % points.size]
            val c = points[i]
            val n = points[(i + 1) % points.size]
            val v1 = Vec2(p.x - c.x, p.y - c.y)
            val v2 = Vec2(n.x - c.x, n.y - c.y)
            val cross = v1.x * v2.y - v1.y * v2.x
            val concave = if (sign > 0) cross < 0 else cross > 0
            if (concave) i else null
        }
    }


    private fun isValleyApexDirectionUpward(points: List<Vec2>, valleyIndices: List<Int>): Boolean {
        if (points.isEmpty() || valleyIndices.isEmpty()) return true
        val valleyY = valleyIndices.map { points[it.mod(points.size)].y }.average().toFloat()
        val centroidY = centroid(points).y
        return valleyY > centroidY
    }

    private fun nearestIndex(path: List<Vec2>, target: Vec2): Int {
        return path.indices.minByOrNull { distance(path[it], target) } ?: 0
    }

    private fun sliceAroundIndex(points: List<Vec2>, center: Int, radius: Int = 4): List<Vec2> {
        if (points.isEmpty()) return emptyList()
        return (center - radius..center + radius).map { points[it.mod(points.size)] }
    }

    private fun rotateToStart(points: List<Vec2>, target: Vec2): List<Vec2> {
        val idx = points.indexOfFirst { same(it, target) }
        if (idx < 0) return points
        return points.drop(idx) + points.take(idx)
    }

    private fun detectJumpNodes(nodes: List<RouteNode>): List<RouteNode> {
        if (nodes.size < 2) return emptyList()
        val d = nodes.zipWithNext { a, b -> distance(a.point, b.point) }
        val threshold = d.average().toFloat() * 1.9f
        return nodes.drop(1).filterIndexed { idx, _ -> d.getOrNull(idx)?.let { it > threshold } == true }
    }

    private fun installerFlowScore(nodes: List<RouteNode>, jumps: List<RouteNode>): Float {
        if (nodes.isEmpty()) return 0f
        return (1f - (jumps.size.toFloat() / nodes.size).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    private fun signedArea(points: List<Vec2>): Float {
        var area = 0f
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            area += (p1.x * p2.y) - (p2.x * p1.y)
        }
        return area / 2f
    }

    private fun centroid(points: List<Vec2>): Vec2 = if (points.isEmpty()) Vec2(0f, 0f) else Vec2(
        points.sumOf { it.x.toDouble() }.toFloat() / points.size,
        points.sumOf { it.y.toDouble() }.toFloat() / points.size,
    )

    private fun same(a: Vec2, b: Vec2): Boolean = abs(a.x - b.x) < 0.0001f && abs(a.y - b.y) < 0.0001f
    private fun distance(a: Vec2, b: Vec2): Float = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
}
