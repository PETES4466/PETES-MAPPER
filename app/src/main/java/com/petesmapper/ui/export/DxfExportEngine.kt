package com.petesmapper.ui.export

import com.petesmapper.ui.geometry.RouteNode
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.Vec2
import kotlin.math.absoluteValue

data class LetterGeometry(
    val contours: List<List<Vec2>>,
)

enum class ControllerType { T1000, T8000 }

data class PowerInjectionMarker(
    val id: String,
    val point: Vec2,
)

data class DxfExportRequest(
    val routePlan: RoutePlan,
    val letterGeometry: LetterGeometry,
    val controllerType: ControllerType,
    val lockedSegmentIndices: Set<Int> = emptySet(),
    val powerInjectionMarkers: List<PowerInjectionMarker> = emptyList(),
    val routeLabelsEnabled: Boolean = true,
)

class DxfExportEngine {

    fun export(request: DxfExportRequest): String {
        require(request.controllerType == ControllerType.T1000 || request.controllerType == ControllerType.T8000) {
            "Unsupported controller type: ${request.controllerType}"
        }

        val out = DxfBuilder()
        out.start()

        exportLetterOutline(out, request.letterGeometry)
        exportPixelRoute(out, request.routePlan)
        exportPixelPoints(out, request.routePlan)
        exportJumpWires(out, request.routePlan)
        exportPowerInjection(out, request.powerInjectionMarkers)
        exportStartEnd(out, request.routePlan)
        exportLockedSegments(out, request.routePlan, request.lockedSegmentIndices)
        if (request.routeLabelsEnabled) exportRouteLabels(out, request.routePlan)

        out.end()
        return out.build()
    }

    private fun exportLetterOutline(out: DxfBuilder, geometry: LetterGeometry) {
        geometry.contours.forEach { contour ->
            if (contour.size >= 2) out.lwPolyline(layer = "LETTER_OUTLINE", points = closeIfNeeded(contour))
        }
    }

    private fun exportPixelRoute(out: DxfBuilder, plan: RoutePlan) {
        plan.routeSegments.forEachIndexed { i, segment ->
            if (segment.points.size >= 2) {
                out.lwPolyline(layer = "PIXEL_ROUTE", points = segment.points)
                out.text(layer = "ROUTE_LABELS", point = segment.points.first(), text = "SEG_$i:${segment.type}")
            }
        }
    }

    private fun exportPixelPoints(out: DxfBuilder, plan: RoutePlan) {
        plan.orderedRouteNodes.forEach { node ->
            out.point(layer = "PIXEL_POINTS", point = node.point)
            out.circle(layer = "PIXEL_POINTS", center = node.point, radius = 0.02)
        }
    }

    private fun exportJumpWires(out: DxfBuilder, plan: RoutePlan) {
        val index = plan.orderedRouteNodes.associateBy { it.id }
        plan.effectiveJumpConnections().forEach { jump ->
            val from = index[jump.fromNodeId]?.point ?: return@forEach
            val to = index[jump.toNodeId]?.point ?: return@forEach
            out.line(layer = "JUMP_WIRES", a = from, b = to)
        }
    }

    private fun exportPowerInjection(out: DxfBuilder, markers: List<PowerInjectionMarker>) {
        markers.forEach { marker ->
            out.circle(layer = "POWER_INJECTION", center = marker.point, radius = 0.06)
            out.text(layer = "POWER_INJECTION", point = marker.point, text = "PWR:${marker.id}")
        }
    }

    private fun exportStartEnd(out: DxfBuilder, plan: RoutePlan) {
        marker(out, "START_END_MARKERS", plan.startNode, "START")
        marker(out, "START_END_MARKERS", plan.endNode, "END")
    }

    private fun exportLockedSegments(out: DxfBuilder, plan: RoutePlan, locked: Set<Int>) {
        locked.sorted().forEach { idx ->
            val segment = plan.routeSegments.getOrNull(idx) ?: return@forEach
            if (segment.points.isNotEmpty()) {
                out.lwPolyline(layer = "ROUTE_LABELS", points = segment.points)
                out.text(layer = "ROUTE_LABELS", point = segment.points.first(), text = "LOCKED_SEG_$idx")
            }
        }
    }

    private fun exportRouteLabels(out: DxfBuilder, plan: RoutePlan) {
        plan.orderedRouteNodes.forEachIndexed { index, node ->
            out.text(layer = "ROUTE_LABELS", point = node.point, text = "N$index")
        }
    }

    private fun marker(out: DxfBuilder, layer: String, node: RouteNode, label: String) {
        out.circle(layer = layer, center = node.point, radius = 0.08)
        out.text(layer = layer, point = node.point, text = label)
    }

    private fun closeIfNeeded(points: List<Vec2>): List<Vec2> {
        if (points.isEmpty()) return points
        val first = points.first()
        val last = points.last()
        return if ((first.x - last.x).absoluteValue < 1e-6f && (first.y - last.y).absoluteValue < 1e-6f) points else points + first
    }
}

private class DxfBuilder {
    private val sb = StringBuilder()

    fun start() {
        pair(0, "SECTION"); pair(2, "HEADER")
        pair(9, "\$ACADVER"); pair(1, "AC1015")
        pair(0, "ENDSEC")
        pair(0, "SECTION"); pair(2, "ENTITIES")
    }

    fun end() {
        pair(0, "ENDSEC")
        pair(0, "EOF")
    }

    fun lwPolyline(layer: String, points: List<Vec2>) {
        pair(0, "LWPOLYLINE")
        pair(8, layer)
        pair(90, points.size.toString())
        pair(70, "0")
        points.forEach {
            pair(10, fmt(it.x))
            pair(20, fmt(it.y))
        }
    }

    fun line(layer: String, a: Vec2, b: Vec2) {
        pair(0, "LINE")
        pair(8, layer)
        pair(10, fmt(a.x)); pair(20, fmt(a.y))
        pair(11, fmt(b.x)); pair(21, fmt(b.y))
    }

    fun point(layer: String, point: Vec2) {
        pair(0, "POINT")
        pair(8, layer)
        pair(10, fmt(point.x)); pair(20, fmt(point.y))
    }

    fun circle(layer: String, center: Vec2, radius: Double) {
        pair(0, "CIRCLE")
        pair(8, layer)
        pair(10, fmt(center.x)); pair(20, fmt(center.y))
        pair(40, "%.6f".format(radius))
    }

    fun text(layer: String, point: Vec2, text: String) {
        pair(0, "TEXT")
        pair(8, layer)
        pair(10, fmt(point.x)); pair(20, fmt(point.y))
        pair(40, "0.12")
        pair(1, text)
    }

    fun build(): String = sb.toString()

    private fun fmt(v: Float): String = "%.6f".format(v)

    private fun pair(code: Int, value: String) {
        sb.append(code).append('\n').append(value).append('\n')
    }
}
