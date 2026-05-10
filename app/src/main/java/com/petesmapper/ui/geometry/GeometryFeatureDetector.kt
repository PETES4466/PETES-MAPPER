package com.petesmapper.ui.geometry

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class GeometryFeatureDetector {


    fun contoursFromInput(input: GlyphPathInput): List<Contour> {
        return when (input) {
            is GlyphPathInput.InternalContours -> input.contours
            is GlyphPathInput.SvgPath -> parseSvgPath(input.data)
        }
    }

    fun detect(input: GlyphPathInput): GeometryFeatures {
        val contours = contoursFromInput(input).filter { it.points.size >= 2 }

        if (contours.isEmpty()) {
            return GeometryFeatures(
                hasPillars = false,
                pillarCount = 0,
                hasIslands = false,
                islandCount = 0,
                hasValleys = false,
                valleyCount = 0,
                valleyDepthAverage = 0f,
                hasTail = false,
                tailPosition = TailPosition.NONE,
                hasCrossings = false,
                crossingCount = 0,
                hasBars = false,
                barCount = 0,
                hasCurves = false,
                openContour = true,
                loopCount = 0,
                mainTrunk = null,
            )
        }

        val openContour = contours.any { !it.closed || !isClosed(it.points) }
        val islands = detectIslands(contours)
        val valleys = detectValleys(contours)
        val pillarCount = detectPillars(contours)
        val barCount = detectBars(contours)
        val crossingCount = detectCrossings(contours)
        val hasCurves = detectCurves(contours)
        val tailPosition = detectTailPosition(contours)
        val loopCount = detectLoopCount(contours)
        val mainTrunk = detectMainTrunk(contours)

        return GeometryFeatures(
            hasPillars = pillarCount > 0,
            pillarCount = pillarCount,
            hasIslands = islands > 0,
            islandCount = islands,
            hasValleys = valleys.first > 0,
            valleyCount = valleys.first,
            valleyDepthAverage = valleys.second,
            hasTail = tailPosition != TailPosition.NONE,
            tailPosition = tailPosition,
            hasCrossings = crossingCount > 0,
            crossingCount = crossingCount,
            hasBars = barCount > 0,
            barCount = barCount,
            hasCurves = hasCurves,
            openContour = openContour,
            loopCount = loopCount,
            mainTrunk = mainTrunk,
        )
    }

    fun detectMainTrunk(contours: List<Contour>): MainTrunk? {
        val segments = contours.flatMap { segments(it.points) }
        if (segments.isEmpty()) return null

        val longest = segments.maxByOrNull { distance(it.first, it.second) } ?: return null
        val length = distance(longest.first, longest.second)
        val angle = atan2(longest.second.y - longest.first.y, longest.second.x - longest.first.x) * 180f / Math.PI.toFloat()

        return MainTrunk(
            start = longest.first,
            end = longest.second,
            length = length,
            angleDegrees = angle,
        )
    }

    fun detectLoopCount(contours: List<Contour>): Int {
        return contours.count { it.closed || isClosed(it.points) }
    }

    private fun detectIslands(contours: List<Contour>): Int {
        val outers = contours.sortedByDescending { it.areaAbs() }
        var islands = 0
        for (i in outers.indices) {
            val c = outers[i]
            val centroid = centroid(c.points)
            val isInsideLarger = (0 until i).any { j -> pointInPolygon(centroid, outers[j].points) }
            if (isInsideLarger) islands++
        }
        return islands
    }

    private fun detectValleys(contours: List<Contour>): Pair<Int, Float> {
        var valleys = 0
        val valleyDepths = mutableListOf<Float>()

        for (c in contours) {
            if (c.points.size < 4) continue
            val sign = if (c.areaSigned() >= 0f) 1 else -1
            for (i in c.points.indices) {
                val prev = c.points[(i - 1 + c.points.size) % c.points.size]
                val curr = c.points[i]
                val next = c.points[(i + 1) % c.points.size]

                val v1 = Vec2(prev.x - curr.x, prev.y - curr.y)
                val v2 = Vec2(next.x - curr.x, next.y - curr.y)
                val angle = angleBetween(v1, v2)
                val cross = cross(v1, v2)
                val concave = if (sign > 0) cross < 0 else cross > 0

                if (concave && angle < 70f) {
                    valleys++
                    valleyDepths += ((70f - angle) / 70f).coerceIn(0f, 1f)
                }
            }
        }

        val avgDepth = if (valleyDepths.isEmpty()) 0f else valleyDepths.average().toFloat()
        return valleys to avgDepth
    }

    private fun detectPillars(contours: List<Contour>): Int {
        var count = 0
        contours.forEach { contour ->
            val verticalSegments = segments(contour.points).filter { seg ->
                val dx = abs(seg.first.x - seg.second.x)
                val dy = abs(seg.first.y - seg.second.y)
                dy > dx * 2.5f
            }
            val groups = verticalSegments.groupBy { ((it.first.x + it.second.x) / 2f * 10).toInt() }
            groups.values.forEach { group -> if (group.size >= 2) count++ }
        }
        return count
    }

    private fun detectBars(contours: List<Contour>): Int {
        var bars = 0
        contours.forEach { contour ->
            segments(contour.points).forEach { (a, b) ->
                val dx = abs(a.x - b.x)
                val dy = abs(a.y - b.y)
                if (dx > dy * 2.5f) bars++
            }
        }
        return bars / 2
    }

    private fun detectCrossings(contours: List<Contour>): Int {
        val allPoints = contours.flatMap { it.points }
        if (allPoints.isEmpty()) return 0
        val c = centroid(allPoints)

        var horizontalHits = 0
        var verticalHits = 0
        contours.forEach { contour ->
            segments(contour.points).forEach { (a, b) ->
                if ((a.y - c.y) * (b.y - c.y) <= 0 && min(a.x, b.x) <= c.x && max(a.x, b.x) >= c.x) horizontalHits++
                if ((a.x - c.x) * (b.x - c.x) <= 0 && min(a.y, b.y) <= c.y && max(a.y, b.y) >= c.y) verticalHits++
            }
        }
        return if (horizontalHits >= 2 && verticalHits >= 2) 1 else 0
    }

    private fun detectCurves(contours: List<Contour>): Boolean {
        var curvedSegments = 0
        var totalSegments = 0
        contours.forEach { contour ->
            segments(contour.points).forEach { (a, b) ->
                totalSegments++
                val angle = atan2((b.y - a.y), (b.x - a.x))
                val snapped = when {
                    near(angle, 0f) || near(abs(angle), Math.PI.toFloat()) -> true
                    near(abs(angle), (Math.PI / 2f).toFloat()) -> true
                    else -> false
                }
                if (!snapped) curvedSegments++
            }
        }
        return totalSegments > 0 && curvedSegments.toFloat() / totalSegments.toFloat() > 0.35f
    }

    private fun detectTailPosition(contours: List<Contour>): TailPosition {
        val points = contours.flatMap { it.points }
        if (points.size < 4) return TailPosition.NONE

        val c = centroid(points)
        val distances = points.map { distance(it, c) }
        val mean = distances.average().toFloat()
        val std = sqrt(distances.map { (it - mean).pow(2) }.average()).toFloat()
        val outliers = points.filter { distance(it, c) > mean + std * 1.2f }
        if (outliers.isEmpty()) return TailPosition.NONE

        val avg = centroid(outliers)
        val dx = avg.x - c.x
        val dy = avg.y - c.y
        return if (abs(dx) > abs(dy)) {
            if (dx > 0) TailPosition.RIGHT else TailPosition.LEFT
        } else {
            if (dy > 0) TailPosition.TOP else TailPosition.BOTTOM
        }
    }

    private fun parseSvgPath(pathData: String): List<Contour> {
        val tokens = pathData
            .replace(",", " ")
            .replace(Regex("([A-Za-z])"), " $1 ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val contours = mutableListOf<Contour>()
        val current = mutableListOf<Vec2>()
        var cursor = Vec2(0f, 0f)
        var start = Vec2(0f, 0f)
        var i = 0
        var cmd = "M"

        fun readFloat(): Float = tokens[i++].toFloatOrNull() ?: 0f

        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t
                i++
                if (cmd.equals("Z", true)) {
                    if (current.isNotEmpty()) {
                        contours += Contour(points = current.toList(), closed = true)
                        current.clear()
                    }
                    cursor = start
                }
                continue
            }

            when (cmd) {
                "M", "m" -> {
                    val x = readFloat(); val y = readFloat()
                    cursor = if (cmd == "m") Vec2(cursor.x + x, cursor.y + y) else Vec2(x, y)
                    start = cursor
                    if (current.isNotEmpty()) {
                        contours += Contour(points = current.toList(), closed = false)
                        current.clear()
                    }
                    current += cursor
                    cmd = if (cmd == "m") "l" else "L"
                }
                "L", "l" -> {
                    val x = readFloat(); val y = readFloat()
                    cursor = if (cmd == "l") Vec2(cursor.x + x, cursor.y + y) else Vec2(x, y)
                    current += cursor
                }
                "H", "h" -> {
                    val x = readFloat()
                    cursor = if (cmd == "h") Vec2(cursor.x + x, cursor.y) else Vec2(x, cursor.y)
                    current += cursor
                }
                "V", "v" -> {
                    val y = readFloat()
                    cursor = if (cmd == "v") Vec2(cursor.x, cursor.y + y) else Vec2(cursor.x, y)
                    current += cursor
                }
                "Q", "q" -> {
                    val x1 = readFloat(); val y1 = readFloat(); val x = readFloat(); val y = readFloat()
                    val c1 = if (cmd == "q") Vec2(cursor.x + x1, cursor.y + y1) else Vec2(x1, y1)
                    val end = if (cmd == "q") Vec2(cursor.x + x, cursor.y + y) else Vec2(x, y)
                    sampleQuadratic(cursor, c1, end).drop(1).forEach { current += it }
                    cursor = end
                }
                "C", "c" -> {
                    val x1 = readFloat(); val y1 = readFloat(); val x2 = readFloat(); val y2 = readFloat(); val x = readFloat(); val y = readFloat()
                    val c1 = if (cmd == "c") Vec2(cursor.x + x1, cursor.y + y1) else Vec2(x1, y1)
                    val c2 = if (cmd == "c") Vec2(cursor.x + x2, cursor.y + y2) else Vec2(x2, y2)
                    val end = if (cmd == "c") Vec2(cursor.x + x, cursor.y + y) else Vec2(x, y)
                    sampleCubic(cursor, c1, c2, end).drop(1).forEach { current += it }
                    cursor = end
                }
                else -> i++
            }
        }

        if (current.isNotEmpty()) contours += Contour(points = current.toList(), closed = false)
        return contours
    }

    private fun sampleQuadratic(p0: Vec2, p1: Vec2, p2: Vec2, steps: Int = 10): List<Vec2> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val mt = 1f - t
            Vec2(
                x = mt * mt * p0.x + 2f * mt * t * p1.x + t * t * p2.x,
                y = mt * mt * p0.y + 2f * mt * t * p1.y + t * t * p2.y,
            )
        }

    private fun sampleCubic(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, steps: Int = 12): List<Vec2> =
        (0..steps).map { i ->
            val t = i / steps.toFloat()
            val mt = 1f - t
            Vec2(
                x = mt.pow(3) * p0.x + 3f * mt.pow(2) * t * p1.x + 3f * mt * t.pow(2) * p2.x + t.pow(3) * p3.x,
                y = mt.pow(3) * p0.y + 3f * mt.pow(2) * t * p1.y + 3f * mt * t.pow(2) * p2.y + t.pow(3) * p3.y,
            )
        }

    private fun near(a: Float, b: Float, eps: Float = 0.22f): Boolean = abs(a - b) < eps

    private fun isClosed(points: List<Vec2>): Boolean {
        if (points.size < 3) return false
        return distance(points.first(), points.last()) < 0.001f
    }

    private fun centroid(points: List<Vec2>): Vec2 {
        if (points.isEmpty()) return Vec2(0f, 0f)
        val sx = points.sumOf { it.x.toDouble() }.toFloat()
        val sy = points.sumOf { it.y.toDouble() }.toFloat()
        return Vec2(sx / points.size, sy / points.size)
    }

    private fun segments(points: List<Vec2>): List<Pair<Vec2, Vec2>> {
        if (points.size < 2) return emptyList()
        return points.indices.mapNotNull { i -> if (i == points.lastIndex) null else points[i] to points[i + 1] }
    }

    private fun distance(a: Vec2, b: Vec2): Float = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    private fun cross(a: Vec2, b: Vec2): Float = a.x * b.y - a.y * b.x

    private fun angleBetween(a: Vec2, b: Vec2): Float {
        val dot = (a.x * b.x) + (a.y * b.y)
        val len = max(distance(a, Vec2(0f, 0f)) * distance(b, Vec2(0f, 0f)), 1e-6f)
        return (acos((dot / len).coerceIn(-1f, 1f)) * 180f / Math.PI.toFloat())
    }

    private fun pointInPolygon(point: Vec2, polygon: List<Vec2>): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val denom = (yj - yi).takeIf { abs(it) > 1e-6f } ?: 1e-6f
            val intersect = ((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / denom + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }
}
