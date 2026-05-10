package com.petesmapper.ui.auto

import kotlin.math.abs

enum class FlowDirection {
    FORWARD,
    REVERSE,
    UNKNOWN,
}

data class FlowDirectionModel(
    val startNode: PixelNode?,
    val endNode: PixelNode?,
    val flowDirection: FlowDirection,
    val confidence: Float,
)

class FlowDirectionEngine {

    fun detectStartNode(pixelNodeMap: PixelNodeMap, stripPathModel: StripPathModel): PixelNode? {
        val nodes = pixelNodeMap.nodes
        if (nodes.isEmpty()) return null
        val firstPath = stripPathModel.segments.firstOrNull()?.points?.firstOrNull()
        if (firstPath != null) {
            return nodes.minByOrNull { n -> dist(n.x, n.y, firstPath.first, firstPath.second) }
        }
        return nodes.minByOrNull { it.index }
    }

    fun detectEndNode(pixelNodeMap: PixelNodeMap, stripPathModel: StripPathModel): PixelNode? {
        val nodes = pixelNodeMap.nodes
        if (nodes.isEmpty()) return null
        val lastPath = stripPathModel.segments.lastOrNull()?.points?.lastOrNull()
        if (lastPath != null) {
            return nodes.minByOrNull { n -> dist(n.x, n.y, lastPath.first, lastPath.second) }
        }
        return nodes.maxByOrNull { it.index }
    }

    fun detectFlowDirection(pixelNodeMap: PixelNodeMap, stripPathModel: StripPathModel): FlowDirection {
        val nodes = pixelNodeMap.nodes
        if (nodes.size < 2 || stripPathModel.segments.isEmpty()) return FlowDirection.UNKNOWN

        val pathStart = stripPathModel.segments.first().points.firstOrNull() ?: return FlowDirection.UNKNOWN
        val pathEnd = stripPathModel.segments.last().points.lastOrNull() ?: return FlowDirection.UNKNOWN

        val firstNode = nodes.minByOrNull { it.index } ?: return FlowDirection.UNKNOWN
        val lastNode = nodes.maxByOrNull { it.index } ?: return FlowDirection.UNKNOWN

        val forwardScore =
            dist(firstNode.x, firstNode.y, pathStart.first, pathStart.second) +
                dist(lastNode.x, lastNode.y, pathEnd.first, pathEnd.second)

        val reverseScore =
            dist(firstNode.x, firstNode.y, pathEnd.first, pathEnd.second) +
                dist(lastNode.x, lastNode.y, pathStart.first, pathStart.second)

        return when {
            abs(forwardScore - reverseScore) < 1e-3f -> FlowDirection.UNKNOWN
            forwardScore < reverseScore -> FlowDirection.FORWARD
            else -> FlowDirection.REVERSE
        }
    }

    fun calculateConfidence(
        startNode: PixelNode?,
        endNode: PixelNode?,
        flowDirection: FlowDirection,
        pixelNodeMap: PixelNodeMap,
        stripPathModel: StripPathModel,
    ): Float {
        if (startNode == null || endNode == null || flowDirection == FlowDirection.UNKNOWN) return 0f
        val pathStart = stripPathModel.segments.firstOrNull()?.points?.firstOrNull() ?: return 0f
        val pathEnd = stripPathModel.segments.lastOrNull()?.points?.lastOrNull() ?: return 0f

        val startDist = dist(startNode.x, startNode.y, pathStart.first, pathStart.second)
        val endDist = dist(endNode.x, endNode.y, pathEnd.first, pathEnd.second)
        val spacing = pixelNodeMap.averageSpacing.coerceAtLeast(1f)
        val alignmentPenalty = ((startDist + endDist) / (spacing * 6f)).coerceIn(0f, 1f)

        val densityBonus = (pixelNodeMap.totalPixels / 100f).coerceIn(0f, 0.35f)
        val base = 0.8f - alignmentPenalty + densityBonus
        return base.coerceIn(0f, 1f)
    }

    fun detect(pixelNodeMap: PixelNodeMap, stripPathModel: StripPathModel): FlowDirectionModel {
        val start = detectStartNode(pixelNodeMap, stripPathModel)
        val end = detectEndNode(pixelNodeMap, stripPathModel)
        val direction = detectFlowDirection(pixelNodeMap, stripPathModel)
        val confidence = calculateConfidence(start, end, direction, pixelNodeMap, stripPathModel)
        return FlowDirectionModel(
            startNode = start,
            endNode = end,
            flowDirection = direction,
            confidence = confidence,
        )
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
