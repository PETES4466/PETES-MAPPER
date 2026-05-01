package com.petesmapper.ui.export

import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.RouteSegmentType
import com.petesmapper.ui.geometry.Vec2
import kotlin.math.ceil

enum class PixelVoltage(val volts: Float) { V5(5f), V12(12f), V24(24f) }

data class ControllerProfile(
    val controllerType: ControllerType,
    val portCount: Int,
    val pixelsPerPort: Int,
    val totalCapacity: Int,
)

data class ControllerMapEntry(
    val pixelIndex: Int,
    val x: Float,
    val y: Float,
    val controllerChannel: Int,
    val dataDirection: String,
    val segmentType: RouteSegmentType,
    val isJumpWire: Boolean,
    val powerInjectionGroup: Int,
)

data class ControllerChannelGroup(
    val channel: Int,
    val startPixelIndex: Int,
    val endPixelIndex: Int,
    val pixelCount: Int,
    val direction: String,
)

data class PowerInjectionZone(
    val zoneId: Int,
    val startPixelIndex: Int,
    val endPixelIndex: Int,
    val pixelCount: Int,
    val estimatedCurrentAmps: Float,
)

data class ControllerMapExport(
    val controllerType: ControllerType,
    val pixelVoltage: PixelVoltage,
    val pixelCurrentAmps: Float,
    val pixelSpacing: Float,
    val entries: List<ControllerMapEntry>,
    val channelGroups: List<ControllerChannelGroup>,
    val powerInjectionZones: List<PowerInjectionZone>,
    val totalPixelCount: Int,
    val totalCurrentAmps: Float,
    val totalPowerWatts: Float,
) {
    fun exportAsJson(): String {
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        val entriesJson = entries.joinToString(",") {
            """{"pixelIndex":${it.pixelIndex},"x":${fmt(it.x)},"y":${fmt(it.y)},"controllerChannel":${it.controllerChannel},"dataDirection":"${esc(it.dataDirection)}","segmentType":"${it.segmentType}","isJumpWire":${it.isJumpWire},"powerInjectionGroup":${it.powerInjectionGroup}}"""
        }
        val channelJson = channelGroups.joinToString(",") {
            """{"channel":${it.channel},"startPixelIndex":${it.startPixelIndex},"endPixelIndex":${it.endPixelIndex},"pixelCount":${it.pixelCount},"direction":"${esc(it.direction)}"}"""
        }
        val zoneJson = powerInjectionZones.joinToString(",") {
            """{"zoneId":${it.zoneId},"startPixelIndex":${it.startPixelIndex},"endPixelIndex":${it.endPixelIndex},"pixelCount":${it.pixelCount},"estimatedCurrentAmps":${fmt(it.estimatedCurrentAmps)}}"""
        }
        return """{"controllerType":"$controllerType","pixelVoltage":"$pixelVoltage","pixelCurrentAmps":${fmt(pixelCurrentAmps)},"pixelSpacing":${fmt(pixelSpacing)},"totalPixelCount":$totalPixelCount,"totalCurrentAmps":${fmt(totalCurrentAmps)},"totalPowerWatts":${fmt(totalPowerWatts)},"entries":[${entriesJson}],"channelGroups":[${channelJson}],"powerInjectionZones":[${zoneJson}]}"""
    }

    fun exportAsCsv(): String {
        val header = "pixel_index,x,y,channel,data_direction,segment_type,is_jump_wire,power_injection_group"
        val rows = entries.joinToString("\n") {
            "${it.pixelIndex},${fmt(it.x)},${fmt(it.y)},${it.controllerChannel},${it.dataDirection},${it.segmentType},${it.isJumpWire},${it.powerInjectionGroup}"
        }
        return "$header\n$rows"
    }

    fun exportAsTxt(): String {
        val builder = StringBuilder()
        builder.appendLine("Controller: $controllerType")
        builder.appendLine("Voltage: $pixelVoltage")
        builder.appendLine("Pixel current (A): ${fmt(pixelCurrentAmps)}")
        builder.appendLine("Pixel spacing: ${fmt(pixelSpacing)}")
        builder.appendLine("Total pixels: $totalPixelCount")
        builder.appendLine("Total current (A): ${fmt(totalCurrentAmps)}")
        builder.appendLine("Total power (W): ${fmt(totalPowerWatts)}")
        builder.appendLine("Channels:")
        channelGroups.forEach { group ->
            builder.appendLine("  CH${group.channel}: ${group.startPixelIndex}-${group.endPixelIndex} (${group.pixelCount}) dir=${group.direction}")
        }
        builder.appendLine("Injection Zones:")
        powerInjectionZones.forEach { zone ->
            builder.appendLine("  Z${zone.zoneId}: ${zone.startPixelIndex}-${zone.endPixelIndex} (${zone.pixelCount}) I=${fmt(zone.estimatedCurrentAmps)}A")
        }
        builder.appendLine("Entries:")
        entries.forEach {
            builder.appendLine("  #${it.pixelIndex} (${fmt(it.x)}, ${fmt(it.y)}) ch=${it.controllerChannel} ${it.dataDirection} ${it.segmentType} jump=${it.isJumpWire} zone=${it.powerInjectionGroup}")
        }
        return builder.toString()
    }
}

class ControllerMapExportEngine {

    fun buildExport(
        routePlan: RoutePlan,
        controllerType: ControllerType,
        pixelVoltage: PixelVoltage,
        pixelCurrent: Float,
        pixelSpacing: Float,
        lockedSegments: Set<Int> = emptySet(),
    ): ControllerMapExport {
        require(controllerType == ControllerType.T1000 || controllerType == ControllerType.T8000) { "Unsupported controller type: $controllerType" }
        require(pixelCurrent > 0f) { "pixelCurrent must be > 0" }
        require(pixelSpacing > 0f) { "pixelSpacing must be > 0" }

        val orderedPixels = orderedPointsByFinalRoute(routePlan)
        val profile = controllerProfile(controllerType)
        require(orderedPixels.size <= profile.totalCapacity) {
            "Route pixel count ${orderedPixels.size} exceeds ${profile.controllerType} capacity ${profile.totalCapacity}"
        }

        val channelCapacity = profile.pixelsPerPort
        val zoneSize = estimateZoneSize(pixelSpacing)

        val entries = mutableListOf<ControllerMapEntry>()
        orderedPixels.forEachIndexed { index, pointInfo ->
            val pixelIdx = index + 1
            val channel = ((pixelIdx - 1) / channelCapacity) + 1
            val zone = ((pixelIdx - 1) / zoneSize) + 1
            val direction = if (channel % 2 == 0) "Reverse" else "Forward"
            entries += ControllerMapEntry(
                pixelIndex = pixelIdx,
                x = pointInfo.point.x,
                y = pointInfo.point.y,
                controllerChannel = channel,
                dataDirection = direction,
                segmentType = pointInfo.segmentType,
                isJumpWire = pointInfo.isJumpWire,
                powerInjectionGroup = zone,
            )
        }

        val channelGroups = entries.groupBy { it.controllerChannel }.toSortedMap().map { (channel, chEntries) ->
            ControllerChannelGroup(
                channel = channel,
                startPixelIndex = chEntries.first().pixelIndex,
                endPixelIndex = chEntries.last().pixelIndex,
                pixelCount = chEntries.size,
                direction = chEntries.first().dataDirection,
            )
        }

        val zones = entries.groupBy { it.powerInjectionGroup }.toSortedMap().map { (zoneId, zEntries) ->
            PowerInjectionZone(
                zoneId = zoneId,
                startPixelIndex = zEntries.first().pixelIndex,
                endPixelIndex = zEntries.last().pixelIndex,
                pixelCount = zEntries.size,
                estimatedCurrentAmps = zEntries.size * pixelCurrent,
            )
        }

        val totalPixels = entries.size
        val totalCurrent = totalPixels * pixelCurrent
        val totalPower = totalCurrent * pixelVoltage.volts

        val lockedSet = lockedSegments
        val adjustedEntries = if (lockedSet.isEmpty()) entries else entries.map { e ->
            val locked = orderedPixels[e.pixelIndex - 1].segmentIndex in lockedSet
            if (locked) e.copy(dataDirection = "Forward") else e
        }

        return ControllerMapExport(
            controllerType = controllerType,
            pixelVoltage = pixelVoltage,
            pixelCurrentAmps = pixelCurrent,
            pixelSpacing = pixelSpacing,
            entries = adjustedEntries,
            channelGroups = channelGroups,
            powerInjectionZones = zones,
            totalPixelCount = totalPixels,
            totalCurrentAmps = totalCurrent,
            totalPowerWatts = totalPower,
        )
    }

    private fun orderedPointsByFinalRoute(routePlan: RoutePlan): List<PointInfo> {
        if (routePlan.routeSegments.isEmpty()) {
            return routePlan.orderedRouteNodes.map { PointInfo(it.point, RouteSegmentType.LOOP, false, -1) }
        }
        val nodeById = routePlan.orderedRouteNodes.associateBy { it.id }
        val jumpPoints = routePlan.effectiveJumpConnections().flatMap { jc ->
            listOfNotNull(nodeById[jc.fromNodeId]?.point, nodeById[jc.toNodeId]?.point)
        }
        val pointInfos = mutableListOf<PointInfo>()
        routePlan.routeSegments.forEachIndexed { segIndex, segment ->
            appendSegmentPoints(segment, segIndex, jumpPoints, pointInfos)
        }
        return pointInfos
    }

    private fun appendSegmentPoints(
        segment: RouteSegment,
        segmentIndex: Int,
        jumpPoints: List<Vec2>,
        out: MutableList<PointInfo>,
    ) {
        segment.points.forEach { p ->
            val isJump = jumpPoints.any { jp ->
                kotlin.math.abs(jp.x - p.x) < 1e-4f && kotlin.math.abs(jp.y - p.y) < 1e-4f
            }
            out += PointInfo(p, segment.type, isJump, segmentIndex)
        }
    }

    private fun estimateZoneSize(pixelSpacing: Float): Int {
        val base = if (pixelSpacing <= 0.35f) 75 else if (pixelSpacing <= 0.75f) 100 else 125
        return ceil(base.toDouble()).toInt()
    }

    private fun controllerProfile(controllerType: ControllerType): ControllerProfile = when (controllerType) {
        ControllerType.T1000 -> ControllerProfile(
            controllerType = ControllerType.T1000,
            portCount = 1,
            pixelsPerPort = 2048,
            totalCapacity = 2048,
        )
        ControllerType.T8000 -> ControllerProfile(
            controllerType = ControllerType.T8000,
            portCount = 8,
            pixelsPerPort = 1024,
            totalCapacity = 8192,
        )
    }

    private fun fmt(v: Float): String = "%.6f".format(v)

    private data class PointInfo(
        val point: Vec2,
        val segmentType: RouteSegmentType,
        val isJumpWire: Boolean,
        val segmentIndex: Int,
    )
}
