package com.petesmapper.ui.auto

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.sqrt

data class PixelNode(
    val x: Float,
    val y: Float,
    val brightness: Float,
    val index: Int,
)

data class PixelNodeMap(
    val nodes: List<PixelNode>,
    val totalPixels: Int,
    val averageSpacing: Float,
)

class PixelDetectionEngine {

    fun detectPixelNodes(image: NormalizedImage, stripPathModel: StripPathModel): PixelNodeMap {
        val raw = mutableListOf<PixelNode>()
        var idx = 0
        stripPathModel.segments.forEach { segment ->
            segment.points.forEach { p ->
                val x = p.first.toInt().coerceIn(0, image.width - 1)
                val y = p.second.toInt().coerceIn(0, image.height - 1)
                val brightness = luminance(image.workingBitmap.getPixel(x, y))
                if (brightness >= 150f) {
                    raw += PixelNode(x.toFloat(), y.toFloat(), brightness, idx++)
                }
            }
        }

        val deduped = removeDuplicatePixels(raw)
        val ordered = orderPixelNodes(deduped, stripPathModel)
        val reindexed = ordered.mapIndexed { i, n -> n.copy(index = i) }
        val spacing = calculatePixelSpacing(reindexed)

        return PixelNodeMap(
            nodes = reindexed,
            totalPixels = reindexed.size,
            averageSpacing = spacing,
        )
    }

    fun calculatePixelSpacing(nodes: List<PixelNode>): Float {
        if (nodes.size < 2) return 0f
        return nodes.zipWithNext().map { (a, b) -> dist(a.x, a.y, b.x, b.y) }.average().toFloat()
    }

    fun removeDuplicatePixels(nodes: List<PixelNode>, tolerancePx: Float = 2.25f): List<PixelNode> {
        if (nodes.isEmpty()) return emptyList()
        val kept = mutableListOf<PixelNode>()
        nodes.forEach { node ->
            val existing = kept.firstOrNull { dist(it.x, it.y, node.x, node.y) <= tolerancePx }
            if (existing == null) {
                kept += node
            } else if (node.brightness > existing.brightness) {
                kept[kept.indexOf(existing)] = node
            }
        }
        return kept
    }

    fun orderPixelNodes(nodes: List<PixelNode>, stripPathModel: StripPathModel): List<PixelNode> {
        if (nodes.size <= 1) return nodes
        if (stripPathModel.segments.isEmpty()) return nodes.sortedByDescending { it.brightness }

        val ordered = mutableListOf<PixelNode>()
        val remaining = nodes.toMutableList()
        stripPathModel.segments.forEach { segment ->
            segment.points.forEach { point ->
                val nearest = remaining.minByOrNull { n -> dist(n.x, n.y, point.first, point.second) } ?: return@forEach
                if (dist(nearest.x, nearest.y, point.first, point.second) <= 6f) {
                    ordered += nearest
                    remaining.remove(nearest)
                }
            }
        }
        // Keep branch leftovers ordered by nearest-neighbor from last placed point.
        while (remaining.isNotEmpty()) {
            val last = ordered.lastOrNull()
            val next = if (last == null) remaining.maxByOrNull { it.brightness } else {
                remaining.minByOrNull { n -> dist(last.x, last.y, n.x, n.y) }
            } ?: break
            ordered += next
            remaining.remove(next)
        }
        return ordered
    }

    private fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126f * r) + (0.7152f * g) + (0.0722f * b)
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return sqrt((ax - bx).pow(2) + (ay - by).pow(2))
    }
}
