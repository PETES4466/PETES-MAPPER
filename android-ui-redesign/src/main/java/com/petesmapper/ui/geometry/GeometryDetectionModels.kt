package com.petesmapper.ui.geometry

import kotlin.math.abs

data class Vec2(val x: Float, val y: Float)

data class Contour(
    val points: List<Vec2>,
    val closed: Boolean = true,
) {
    fun areaSigned(): Float {
        if (points.size < 3) return 0f
        var area = 0f
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            area += (p1.x * p2.y) - (p2.x * p1.y)
        }
        return area / 2f
    }

    fun areaAbs(): Float = abs(areaSigned())
}

sealed class GlyphPathInput {
    data class SvgPath(val data: String) : GlyphPathInput()
    data class InternalContours(val contours: List<Contour>) : GlyphPathInput()
}

enum class TailPosition {
    NONE,
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
}

data class MainTrunk(
    val start: Vec2,
    val end: Vec2,
    val length: Float,
    val angleDegrees: Float,
)

data class GeometryFeatures(
    val hasPillars: Boolean,
    val pillarCount: Int,
    val hasIslands: Boolean,
    val islandCount: Int,
    val hasValleys: Boolean,
    val valleyCount: Int,
    val valleyDepthAverage: Float,
    val hasTail: Boolean,
    val tailPosition: TailPosition,
    val hasCrossings: Boolean,
    val crossingCount: Int,
    val hasBars: Boolean,
    val barCount: Int,
    val hasCurves: Boolean,
    val openContour: Boolean,
    val loopCount: Int,
    val mainTrunk: MainTrunk?,
)

enum class ShapeClass {
    LINEAR,
    BAR,
    PILLAR,
    VALLEY,
    ISLAND,
    SPIRAL,
    TAIL,
    CROSS,
    PILLAR_BRIDGE,
    PILLAR_VALLEY_COMBO,
    TAIL_EXIT_BOTTOM,
    HYBRID,
}

data class ShapeClassificationResult(
    val shapeClass: ShapeClass,
    val confidence: Float,
    val features: GeometryFeatures,
)
