package com.petesmapper.ui.auto

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.sqrt

enum class StripSegmentType { STRAIGHT, CURVE, JUMP, TURN }

data class StripSegment(
    val points: List<Pair<Float, Float>>,
    val length: Float,
    val type: StripSegmentType,
)

data class StripPathModel(
    val segments: List<StripSegment>,
    val totalLength: Float,
    val estimatedStripCount: Int,
)

class StripDetectionEngine {

    fun detectStripLines(image: NormalizedImage): List<StripSegment> {
        val pathPoints = sampleBrightPathPoints(image.workingBitmap)
        if (pathPoints.size < 2) return emptyList()

        val chunks = pathPoints.chunked(18)
        return chunks.map { chunk ->
            val type = classifySegment(chunk)
            StripSegment(
                points = chunk,
                length = polylineLength(chunk),
                type = type,
            )
        }
    }

    fun detectTurns(segments: List<StripSegment>): List<StripSegment> {
        return segments.filter { seg ->
            seg.type == StripSegmentType.TURN || turnScore(seg.points) > 40f
        }.map { it.copy(type = StripSegmentType.TURN) }
    }

    fun detectJumpWires(segments: List<StripSegment>): List<StripSegment> {
        return segments.filter { seg ->
            val span = lineSpan(seg.points)
            seg.length > span * 1.35f || seg.type == StripSegmentType.JUMP
        }.map { it.copy(type = StripSegmentType.JUMP) }
    }

    fun mergeSegments(segments: List<StripSegment>): List<StripSegment> {
        if (segments.isEmpty()) return emptyList()
        val merged = mutableListOf<StripSegment>()
        var current = segments.first()

        for (next in segments.drop(1)) {
            val connectable = isConnectable(current, next)
            if (connectable && current.type == next.type) {
                val joinedPoints = current.points + next.points.drop(1)
                current = StripSegment(joinedPoints, polylineLength(joinedPoints), current.type)
            } else {
                merged += current
                current = next
            }
        }
        merged += current
        return merged
    }

    fun calculateStripLength(segments: List<StripSegment>): Float = segments.sumOf { it.length.toDouble() }.toFloat()

    fun buildPathModel(image: NormalizedImage): StripPathModel {
        val raw = detectStripLines(image)
        val withTurns = applyTypeOverrides(raw, detectTurns(raw))
        val withJumps = applyTypeOverrides(withTurns, detectJumpWires(withTurns))
        val merged = mergeSegments(withJumps)
        val totalLength = calculateStripLength(merged)
        val estimatedStripCount = estimateStripCount(merged, defaultRollLength = 5f)
        return StripPathModel(
            segments = merged,
            totalLength = totalLength,
            estimatedStripCount = estimatedStripCount,
        )
    }

    private fun applyTypeOverrides(base: List<StripSegment>, overrides: List<StripSegment>): List<StripSegment> {
        if (overrides.isEmpty()) return base
        val overrideKeys = overrides.associateBy { keyFor(it.points) }
        return base.map { seg -> overrideKeys[keyFor(seg.points)] ?: seg }
    }

    private fun sampleBrightPathPoints(bitmap: Bitmap): List<Pair<Float, Float>> {
        val points = mutableListOf<SamplePoint>()
        val step = (minOf(bitmap.width, bitmap.height) / 180).coerceAtLeast(1)
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val pixel = bitmap.getPixel(x, y)
                val lum = luminance(pixel)
                if (lum > 190f) points += SamplePoint(x.toFloat(), y.toFloat(), lum)
            }
        }
        if (points.isEmpty()) return emptyList()
        return chainNearestNeighbors(points)
    }

    private fun chainNearestNeighbors(points: List<SamplePoint>): List<Pair<Float, Float>> {
        val remaining = points.toMutableList()
        // Start from strongest brightness cluster representative (highest luminance sample).
        var current = remaining.maxByOrNull { it.luminance } ?: return emptyList()
        val ordered = ArrayList<Pair<Float, Float>>(remaining.size)
        ordered += current.x to current.y
        remaining.remove(current)

        while (remaining.isNotEmpty()) {
            val next = remaining.minByOrNull { candidate ->
                val dx = candidate.x - current.x
                val dy = candidate.y - current.y
                (dx * dx) + (dy * dy)
            } ?: break
            ordered += next.x to next.y
            remaining.remove(next)
            current = next
        }
        return ordered
    }

    private fun classifySegment(points: List<Pair<Float, Float>>): StripSegmentType {
        val score = turnScore(points)
        val span = lineSpan(points)
        val length = polylineLength(points)
        return when {
            length > span * 1.35f -> StripSegmentType.CURVE
            score > 55f -> StripSegmentType.TURN
            else -> StripSegmentType.STRAIGHT
        }
    }

    private fun turnScore(points: List<Pair<Float, Float>>): Float {
        if (points.size < 3) return 0f
        var score = 0f
        points.windowed(3).forEach { trio ->
            val a = trio[0]; val b = trio[1]; val c = trio[2]
            val angle = angleDeg(a, b, c)
            score += (180f - angle).coerceAtLeast(0f)
        }
        return score / (points.size - 2)
    }

    private fun angleDeg(a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Float {
        val v1x = a.first - b.first
        val v1y = a.second - b.second
        val v2x = c.first - b.first
        val v2y = c.second - b.second
        val dot = v1x * v2x + v1y * v2y
        val m1 = sqrt(v1x.pow(2) + v1y.pow(2)).coerceAtLeast(1e-6f)
        val m2 = sqrt(v2x.pow(2) + v2y.pow(2)).coerceAtLeast(1e-6f)
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat()
    }

    private fun lineSpan(points: List<Pair<Float, Float>>): Float {
        val first = points.first()
        val last = points.last()
        return dist(first, last)
    }

    private fun polylineLength(points: List<Pair<Float, Float>>): Float {
        return points.zipWithNext().sumOf { (a, b) -> dist(a, b).toDouble() }.toFloat()
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        return sqrt((a.first - b.first).pow(2) + (a.second - b.second).pow(2))
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126f * r) + (0.7152f * g) + (0.0722f * b)
    }

    private fun keyFor(points: List<Pair<Float, Float>>): String {
        if (points.isEmpty()) return "empty"
        val s = points.first(); val e = points.last()
        return "${s.first.toInt()},${s.second.toInt()}-${e.first.toInt()},${e.second.toInt()}-${points.size}"
    }

    private fun estimateStripCount(segments: List<StripSegment>, defaultRollLength: Float): Int {
        val total = calculateStripLength(segments)
        return kotlin.math.ceil((total / defaultRollLength).toDouble()).toInt().coerceAtLeast(1)
    }

    private data class SamplePoint(
        val x: Float,
        val y: Float,
        val luminance: Float,
    )
}
