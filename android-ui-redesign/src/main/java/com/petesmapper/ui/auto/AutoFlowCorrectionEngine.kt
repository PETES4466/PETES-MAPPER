package com.petesmapper.ui.auto

enum class FlowLockState { UNLOCKED, LOCKED }

data class CorrectedFlowModel(
    val startNode: PixelNode?,
    val endNode: PixelNode?,
    val flowDirection: FlowDirection,
    val confidence: Float,
    val locked: Boolean,
)

class AutoFlowCorrectionEngine {

    fun setStartNode(
        current: CorrectedFlowModel,
        pixelNodeMap: PixelNodeMap,
        startIndex: Int,
    ): CorrectedFlowModel {
        val start = pixelNodeMap.nodes.getOrNull(startIndex) ?: return current
        val updatedDirection = inferDirection(start, current.endNode, current.flowDirection)
        return current.copy(startNode = start, flowDirection = updatedDirection, confidence = recalcConfidence(start, current.endNode, pixelNodeMap))
    }

    fun setEndNode(
        current: CorrectedFlowModel,
        pixelNodeMap: PixelNodeMap,
        endIndex: Int,
    ): CorrectedFlowModel {
        val end = pixelNodeMap.nodes.getOrNull(endIndex) ?: return current
        val updatedDirection = inferDirection(current.startNode, end, current.flowDirection)
        return current.copy(endNode = end, flowDirection = updatedDirection, confidence = recalcConfidence(current.startNode, end, pixelNodeMap))
    }

    fun reverseFlow(current: CorrectedFlowModel): CorrectedFlowModel {
        val reversed = when (current.flowDirection) {
            FlowDirection.FORWARD -> FlowDirection.REVERSE
            FlowDirection.REVERSE -> FlowDirection.FORWARD
            FlowDirection.UNKNOWN -> FlowDirection.UNKNOWN
        }
        return current.copy(flowDirection = reversed, confidence = (current.confidence * 0.95f).coerceIn(0f, 1f))
    }

    fun swapStartEnd(current: CorrectedFlowModel): CorrectedFlowModel {
        val swappedDirection = when (current.flowDirection) {
            FlowDirection.FORWARD -> FlowDirection.REVERSE
            FlowDirection.REVERSE -> FlowDirection.FORWARD
            FlowDirection.UNKNOWN -> FlowDirection.UNKNOWN
        }
        return current.copy(
            startNode = current.endNode,
            endNode = current.startNode,
            flowDirection = swappedDirection,
            confidence = current.confidence,
        )
    }

    fun lockFlow(current: CorrectedFlowModel, locked: Boolean = true): CorrectedFlowModel {
        return current.copy(locked = locked)
    }

    fun fromDetected(model: FlowDirectionModel): CorrectedFlowModel {
        return CorrectedFlowModel(
            startNode = model.startNode,
            endNode = model.endNode,
            flowDirection = model.flowDirection,
            confidence = model.confidence,
            locked = false,
        )
    }

    private fun inferDirection(start: PixelNode?, end: PixelNode?, fallback: FlowDirection): FlowDirection {
        if (start == null || end == null) return fallback
        return when {
            start.index < end.index -> FlowDirection.FORWARD
            start.index > end.index -> FlowDirection.REVERSE
            else -> FlowDirection.UNKNOWN
        }
    }

    private fun recalcConfidence(start: PixelNode?, end: PixelNode?, pixelNodeMap: PixelNodeMap): Float {
        if (start == null || end == null) return 0f
        val span = kotlin.math.abs(end.index - start.index).toFloat()
        val coverage = if (pixelNodeMap.totalPixels <= 1) 0f else span / (pixelNodeMap.totalPixels - 1).toFloat()
        val brightnessScore = ((start.brightness + end.brightness) / 2f / 255f).coerceIn(0f, 1f)
        return (0.5f * coverage + 0.5f * brightnessScore).coerceIn(0f, 1f)
    }
}
