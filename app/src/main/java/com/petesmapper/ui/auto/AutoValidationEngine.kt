package com.petesmapper.ui.auto

import com.petesmapper.ui.geometry.RoutePlan
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class AutoValidationSeverity { INFO, WARNING, CRITICAL }

data class AutoValidationIssue(
    val code: String,
    val severity: AutoValidationSeverity,
    val message: String,
)

data class AutoValidationReport(
    val issues: List<AutoValidationIssue>,
    val canExport: Boolean,
)

class AutoValidationEngine {

    fun validate(
        stripPathModel: StripPathModel,
        pixelNodeMap: PixelNodeMap,
        correctedFlowModel: CorrectedFlowModel,
        routePlan: RoutePlan,
    ): AutoValidationReport {
        val issues = mutableListOf<AutoValidationIssue>()

        validatePixelContinuity(pixelNodeMap, issues)
        validatePixelSpacing(pixelNodeMap, issues)
        validateDisconnectedSegments(stripPathModel, issues)
        validateSuspiciousJumpWires(stripPathModel, routePlan, issues)
        validateFlowConfidence(correctedFlowModel, issues)
        validatePossibleReverseRoute(pixelNodeMap, correctedFlowModel, issues)
        validateLowPixelDensity(stripPathModel, pixelNodeMap, issues)
        validateMissingRouteNodes(routePlan, pixelNodeMap, issues)

        return AutoValidationReport(
            issues = issues,
            canExport = issues.none { it.severity == AutoValidationSeverity.CRITICAL },
        )
    }

    private fun validatePixelContinuity(pixelNodeMap: PixelNodeMap, issues: MutableList<AutoValidationIssue>) {
        val nodes = pixelNodeMap.nodes
        if (nodes.isEmpty()) {
            issues += AutoValidationIssue("PIXELS_EMPTY", AutoValidationSeverity.CRITICAL, "No pixels detected.")
            return
        }
        val gaps = nodes.zipWithNext().count { (a, b) ->
            abs((b.index - a.index) - 1) > 0
        }
        if (gaps > 0) {
            issues += AutoValidationIssue("PIXEL_INDEX_GAPS", AutoValidationSeverity.WARNING, "Detected $gaps index gaps in pixel sequence.")
        }
    }

    private fun validatePixelSpacing(pixelNodeMap: PixelNodeMap, issues: MutableList<AutoValidationIssue>) {
        if (pixelNodeMap.nodes.size < 3) return
        val distances = pixelNodeMap.nodes.zipWithNext().map { (a, b) -> dist(a.x, a.y, b.x, b.y) }
        val avg = distances.average().toFloat().coerceAtLeast(1e-6f)
        val varianceRatio = distances.maxOrNull()!!.toFloat() / avg
        if (varianceRatio > 2.8f) {
            issues += AutoValidationIssue("ABNORMAL_SPACING", AutoValidationSeverity.WARNING, "Abnormal pixel spacing detected (max/avg=${fmt(varianceRatio)}).")
        }
    }

    private fun validateDisconnectedSegments(stripPathModel: StripPathModel, issues: MutableList<AutoValidationIssue>) {
        val segments = stripPathModel.segments
        if (segments.isEmpty()) {
            issues += AutoValidationIssue("STRIP_SEGMENTS_EMPTY", AutoValidationSeverity.CRITICAL, "No strip segments detected.")
            return
        }
        segments.zipWithNext().forEachIndexed { idx, (a, b) ->
            val aEnd = a.points.lastOrNull() ?: return@forEachIndexed
            val bStart = b.points.firstOrNull() ?: return@forEachIndexed
            val d = dist(aEnd.first, aEnd.second, bStart.first, bStart.second)
            if (d > 40f) {
                issues += AutoValidationIssue("DISCONNECTED_SEG_$idx", AutoValidationSeverity.WARNING, "Strip segment discontinuity detected near index $idx.")
            }
        }
    }

    private fun validateSuspiciousJumpWires(
        stripPathModel: StripPathModel,
        routePlan: RoutePlan,
        issues: MutableList<AutoValidationIssue>,
    ) {
        val jumpSegCount = stripPathModel.segments.count { it.type == StripSegmentType.JUMP }
        val jumpConnCount = routePlan.jumpConnections.size
        if (jumpSegCount != jumpConnCount) {
            issues += AutoValidationIssue("JUMP_MISMATCH", AutoValidationSeverity.WARNING, "Jump detection mismatch between AUTO and RoutePlan.")
        }
        routePlan.jumpConnections.forEachIndexed { idx, jump ->
            if (jump.wireLength <= 0f) {
                issues += AutoValidationIssue("JUMP_LENGTH_$idx", AutoValidationSeverity.CRITICAL, "Jump connection has invalid wire length.")
            }
        }
    }

    private fun validateFlowConfidence(correctedFlowModel: CorrectedFlowModel, issues: MutableList<AutoValidationIssue>) {
        if (correctedFlowModel.confidence < 0.35f) {
            issues += AutoValidationIssue("WEAK_FLOW_CONFIDENCE", AutoValidationSeverity.WARNING, "Flow confidence is weak (${fmt(correctedFlowModel.confidence)}).")
        }
        if (correctedFlowModel.locked && correctedFlowModel.confidence < 0.2f) {
            issues += AutoValidationIssue("LOCKED_LOW_CONFIDENCE", AutoValidationSeverity.INFO, "Flow is locked despite low confidence.")
        }
    }

    private fun validatePossibleReverseRoute(
        pixelNodeMap: PixelNodeMap,
        correctedFlowModel: CorrectedFlowModel,
        issues: MutableList<AutoValidationIssue>,
    ) {
        val start = correctedFlowModel.startNode ?: return
        val end = correctedFlowModel.endNode ?: return
        val implied = if (start.index <= end.index) FlowDirection.FORWARD else FlowDirection.REVERSE
        if (correctedFlowModel.flowDirection != FlowDirection.UNKNOWN && correctedFlowModel.flowDirection != implied) {
            issues += AutoValidationIssue("POSSIBLE_REVERSED_ROUTE", AutoValidationSeverity.WARNING, "Flow direction may be reversed relative to selected start/end nodes.")
        }
        if (pixelNodeMap.nodes.size > 1 && start.index == end.index) {
            issues += AutoValidationIssue("START_END_SAME", AutoValidationSeverity.CRITICAL, "Start and end nodes resolve to same index.")
        }
    }

    private fun validateLowPixelDensity(
        stripPathModel: StripPathModel,
        pixelNodeMap: PixelNodeMap,
        issues: MutableList<AutoValidationIssue>,
    ) {
        val totalLength = stripPathModel.totalLength.coerceAtLeast(1f)
        val density = pixelNodeMap.totalPixels / totalLength
        if (density < 0.08f) {
            issues += AutoValidationIssue("LOW_PIXEL_DENSITY", AutoValidationSeverity.WARNING, "Pixel density appears low for detected strip length.")
        }
    }

    private fun validateMissingRouteNodes(
        routePlan: RoutePlan,
        pixelNodeMap: PixelNodeMap,
        issues: MutableList<AutoValidationIssue>,
    ) {
        if (routePlan.orderedRouteNodes.isEmpty()) {
            issues += AutoValidationIssue("ROUTE_NODES_EMPTY", AutoValidationSeverity.CRITICAL, "Route plan has no ordered route nodes.")
            return
        }
        if (routePlan.orderedRouteNodes.size < pixelNodeMap.totalPixels / 2) {
            issues += AutoValidationIssue("MISSING_ROUTE_NODES", AutoValidationSeverity.WARNING, "Route plan node count is unexpectedly lower than detected pixel count.")
        }
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return sqrt((ax - bx).pow(2) + (ay - by).pow(2))
    }

    private fun fmt(v: Float): String = "%.3f".format(v)
}
