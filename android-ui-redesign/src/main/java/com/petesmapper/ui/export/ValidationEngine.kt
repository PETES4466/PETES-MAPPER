package com.petesmapper.ui.export

import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegmentType
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class ValidationSeverity { INFO, WARNING, CRITICAL }

data class ValidationIssue(
    val code: String,
    val severity: ValidationSeverity,
    val message: String,
)

data class ValidationReport(
    val issues: List<ValidationIssue>,
    val canExport: Boolean,
) {
    fun hasCritical(): Boolean = issues.any { it.severity == ValidationSeverity.CRITICAL }
}

class ValidationEngine {

    fun validate(
        routePlan: RoutePlan,
        controllerMapExport: ControllerMapExport,
        billOfMaterials: BillOfMaterials,
    ): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        validateStartEnd(routePlan, issues)
        validateRouteContinuity(routePlan, issues)
        validateJumpIntegrity(routePlan, issues)
        validateControllerCapacity(controllerMapExport, routePlan, issues)
        validatePowerOverload(controllerMapExport, billOfMaterials, issues)
        validateVoltageDropRisk(controllerMapExport, billOfMaterials, issues)
        validateBendRadius(routePlan, issues)
        validateOrphanNodes(routePlan, issues)
        validateDuplicateNodes(routePlan, issues)
        validateOpenContourWarnings(routePlan, issues)

        return ValidationReport(
            issues = issues,
            canExport = issues.none { it.severity == ValidationSeverity.CRITICAL },
        )
    }

    private fun validateStartEnd(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        if (routePlan.orderedRouteNodes.isEmpty()) {
            issues += ValidationIssue("START_END_MISSING", ValidationSeverity.CRITICAL, "Route has no nodes.")
            return
        }
        if (routePlan.startNode.id !in routePlan.orderedRouteNodes.map { it.id }.toSet()) {
            issues += ValidationIssue("START_NODE_MISSING", ValidationSeverity.CRITICAL, "Start node not found in route nodes.")
        }
        if (routePlan.endNode.id !in routePlan.orderedRouteNodes.map { it.id }.toSet()) {
            issues += ValidationIssue("END_NODE_MISSING", ValidationSeverity.CRITICAL, "End node not found in route nodes.")
        }
    }

    private fun validateRouteContinuity(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        if (routePlan.routeSegments.isEmpty()) {
            issues += ValidationIssue("ROUTE_SEGMENTS_EMPTY", ValidationSeverity.CRITICAL, "Route has no segments.")
            return
        }
        routePlan.routeSegments.zipWithNext().forEachIndexed { index, (a, b) ->
            val aEnd = a.points.lastOrNull()
            val bStart = b.points.firstOrNull()
            if (aEnd == null || bStart == null) {
                issues += ValidationIssue("SEGMENT_EMPTY_$index", ValidationSeverity.CRITICAL, "Segment contains no geometry.")
                return@forEachIndexed
            }
            val d = distance(aEnd.x, aEnd.y, bStart.x, bStart.y)
            if (d > 0.75f) {
                issues += ValidationIssue("ROUTE_GAP_$index", ValidationSeverity.WARNING, "Gap detected between segment $index and ${index + 1}: ${fmt(d)}")
            }
        }
    }

    private fun validateJumpIntegrity(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        val nodeIds = routePlan.orderedRouteNodes.map { it.id }.toSet()
        routePlan.effectiveJumpConnections().forEachIndexed { i, jump ->
            if (jump.fromNodeId !in nodeIds || jump.toNodeId !in nodeIds) {
                issues += ValidationIssue("JUMP_NODE_MISSING_$i", ValidationSeverity.CRITICAL, "Jump connection references missing nodes.")
            }
            if (jump.wireLength <= 0f) {
                issues += ValidationIssue("JUMP_WIRE_LENGTH_$i", ValidationSeverity.WARNING, "Jump connection has non-positive wire length.")
            }
        }
    }

    private fun validateControllerCapacity(
        controllerMapExport: ControllerMapExport,
        routePlan: RoutePlan,
        issues: MutableList<ValidationIssue>,
    ) {
        val profile = when (controllerMapExport.controllerType) {
            ControllerType.T1000 -> ControllerProfile(ControllerType.T1000, 1, 2048, 2048)
            ControllerType.T8000 -> ControllerProfile(ControllerType.T8000, 8, 1024, 8192)
        }
        if (controllerMapExport.totalPixelCount > profile.totalCapacity) {
            issues += ValidationIssue("CONTROLLER_OVERFLOW", ValidationSeverity.CRITICAL, "Pixel count ${controllerMapExport.totalPixelCount} exceeds ${profile.controllerType} capacity ${profile.totalCapacity}.")
        }
        if (controllerMapExport.entries.size != routePlan.routeSegments.sumOf { it.points.size }) {
            issues += ValidationIssue("PIXEL_ROUTE_MISMATCH", ValidationSeverity.WARNING, "Controller map entry count differs from route point count.")
        }
    }

    private fun validatePowerOverload(
        controllerMapExport: ControllerMapExport,
        billOfMaterials: BillOfMaterials,
        issues: MutableList<ValidationIssue>,
    ) {
        val supplyCapacity = billOfMaterials.powerSupplyRequirement.smpsCount * billOfMaterials.powerSupplyRequirement.recommendedSmpsWattage
        if (controllerMapExport.totalPowerWatts > supplyCapacity) {
            issues += ValidationIssue("POWER_OVERLOAD", ValidationSeverity.CRITICAL, "Total power exceeds configured SMPS capacity.")
        }
    }

    private fun validateVoltageDropRisk(
        controllerMapExport: ControllerMapExport,
        billOfMaterials: BillOfMaterials,
        issues: MutableList<ValidationIssue>,
    ) {
        val longestZone = billOfMaterials.powerInjectionRequirement.injectionWireLength /
            billOfMaterials.powerInjectionRequirement.injectionPointCount.coerceAtLeast(1)
        val currentPerZone = controllerMapExport.totalCurrentAmps /
            billOfMaterials.powerInjectionRequirement.injectionPointCount.coerceAtLeast(1)
        if (longestZone > 5f && currentPerZone > 8f) {
            issues += ValidationIssue("VOLTAGE_DROP_RISK", ValidationSeverity.WARNING, "Potential voltage drop risk detected; consider more injection points.")
        }
    }

    private fun validateBendRadius(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        routePlan.routeSegments.forEachIndexed { sIdx, seg ->
            seg.points.windowed(3).forEachIndexed { pIdx, trio ->
                val a = trio[0]; val b = trio[1]; val c = trio[2]
                val angle = angleDeg(a.x, a.y, b.x, b.y, c.x, c.y)
                if (angle < 45f && seg.type != RouteSegmentType.BRIDGE) {
                    issues += ValidationIssue("SHARP_BEND_${sIdx}_$pIdx", ValidationSeverity.WARNING, "Sharp bend (<45°) at segment $sIdx point $pIdx.")
                }
            }
        }
    }

    private fun validateOrphanNodes(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        val used = routePlan.routeSegments.flatMap { it.points }.toSet()
        val orphan = routePlan.orderedRouteNodes.count { it.point !in used }
        if (orphan > 0) {
            issues += ValidationIssue("ORPHAN_NODES", ValidationSeverity.WARNING, "$orphan route nodes are not referenced by any segment point.")
        }
    }

    private fun validateDuplicateNodes(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        val dupes = routePlan.orderedRouteNodes.groupBy { "${fmt(it.point.x)}:${fmt(it.point.y)}" }.filter { it.value.size > 1 }
        if (dupes.isNotEmpty()) {
            issues += ValidationIssue("DUPLICATE_NODES", ValidationSeverity.WARNING, "Duplicate node coordinates detected: ${dupes.size}")
        }
    }

    private fun validateOpenContourWarnings(routePlan: RoutePlan, issues: MutableList<ValidationIssue>) {
        routePlan.routeSegments.forEachIndexed { idx, seg ->
            val first = seg.points.firstOrNull() ?: return@forEachIndexed
            val last = seg.points.lastOrNull() ?: return@forEachIndexed
            if (seg.type == RouteSegmentType.LOOP) {
                val d = distance(first.x, first.y, last.x, last.y)
                if (d > 0.1f) {
                    issues += ValidationIssue("OPEN_LOOP_$idx", ValidationSeverity.WARNING, "Loop segment $idx appears open.")
                }
            }
        }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = sqrt((ax - bx).pow(2) + (ay - by).pow(2))

    private fun angleDeg(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        val v1x = ax - bx; val v1y = ay - by
        val v2x = cx - bx; val v2y = cy - by
        val dot = (v1x * v2x) + (v1y * v2y)
        val m1 = sqrt(v1x.pow(2) + v1y.pow(2)).coerceAtLeast(1e-6f)
        val m2 = sqrt(v2x.pow(2) + v2y.pow(2)).coerceAtLeast(1e-6f)
        val cos = (dot / (m1 * m2)).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(cos).toDouble()).toFloat()
    }

    private fun fmt(v: Float): String = "%.3f".format(v)
}
