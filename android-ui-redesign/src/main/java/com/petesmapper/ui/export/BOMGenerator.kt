package com.petesmapper.ui.export

import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.Vec2
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

data class StripRequirement(
    val totalStripLength: Float,
    val totalPixelCount: Int,
    val stripRollLength: Float,
    val stripRollCount: Int,
)

data class JumperWireRequirement(
    val jumperCount: Int,
    val totalJumperWireLength: Float,
)

data class PowerInjectionRequirement(
    val injectionPointCount: Int,
    val injectionWireLength: Float,
    val recommendedWireGauge: String,
)

data class PowerSupplyRequirement(
    val totalCurrent: Float,
    val totalPowerWatts: Float,
    val recommendedSmpsWattage: Float,
    val smpsCount: Int,
)

data class ControllerRequirement(
    val controllerType: ControllerType,
    val controllerCount: Int,
    val portUsage: String,
)

data class ConnectorRequirement(
    val connectorCount: Int,
)

data class MountingRequirement(
    val mountingClipCount: Int,
    val mountingTapeLength: Float,
)

data class BillOfMaterials(
    val stripRequirement: StripRequirement,
    val jumperRequirement: JumperWireRequirement,
    val powerInjectionRequirement: PowerInjectionRequirement,
    val powerSupplyRequirement: PowerSupplyRequirement,
    val controllerRequirement: ControllerRequirement,
    val connectorRequirement: ConnectorRequirement,
    val mountingRequirement: MountingRequirement,
    val totalEstimatedMaterialUnits: Int,
) {
    fun exportAsJson(): String {
        return """
        {
          "stripRequirement": {
            "totalStripLength": ${fmt(stripRequirement.totalStripLength)},
            "totalPixelCount": ${stripRequirement.totalPixelCount},
            "stripRollLength": ${fmt(stripRequirement.stripRollLength)},
            "stripRollCount": ${stripRequirement.stripRollCount}
          },
          "jumperRequirement": {
            "jumperCount": ${jumperRequirement.jumperCount},
            "totalJumperWireLength": ${fmt(jumperRequirement.totalJumperWireLength)}
          },
          "powerInjectionRequirement": {
            "injectionPointCount": ${powerInjectionRequirement.injectionPointCount},
            "injectionWireLength": ${fmt(powerInjectionRequirement.injectionWireLength)},
            "recommendedWireGauge": "${powerInjectionRequirement.recommendedWireGauge}"
          },
          "powerSupplyRequirement": {
            "totalCurrent": ${fmt(powerSupplyRequirement.totalCurrent)},
            "totalPowerWatts": ${fmt(powerSupplyRequirement.totalPowerWatts)},
            "recommendedSmpsWattage": ${fmt(powerSupplyRequirement.recommendedSmpsWattage)},
            "smpsCount": ${powerSupplyRequirement.smpsCount}
          },
          "controllerRequirement": {
            "controllerType": "${controllerRequirement.controllerType}",
            "controllerCount": ${controllerRequirement.controllerCount},
            "portUsage": "${controllerRequirement.portUsage}"
          },
          "connectorRequirement": {
            "connectorCount": ${connectorRequirement.connectorCount}
          },
          "mountingRequirement": {
            "mountingClipCount": ${mountingRequirement.mountingClipCount},
            "mountingTapeLength": ${fmt(mountingRequirement.mountingTapeLength)}
          },
          "totalEstimatedMaterialUnits": $totalEstimatedMaterialUnits
        }
        """.trimIndent()
    }

    fun exportAsCsv(): String {
        return buildString {
            appendLine("category,field,value")
            appendLine("strip,totalStripLength,${fmt(stripRequirement.totalStripLength)}")
            appendLine("strip,totalPixelCount,${stripRequirement.totalPixelCount}")
            appendLine("strip,stripRollLength,${fmt(stripRequirement.stripRollLength)}")
            appendLine("strip,stripRollCount,${stripRequirement.stripRollCount}")
            appendLine("jumper,jumperCount,${jumperRequirement.jumperCount}")
            appendLine("jumper,totalJumperWireLength,${fmt(jumperRequirement.totalJumperWireLength)}")
            appendLine("injection,injectionPointCount,${powerInjectionRequirement.injectionPointCount}")
            appendLine("injection,injectionWireLength,${fmt(powerInjectionRequirement.injectionWireLength)}")
            appendLine("injection,recommendedWireGauge,${powerInjectionRequirement.recommendedWireGauge}")
            appendLine("smps,totalCurrent,${fmt(powerSupplyRequirement.totalCurrent)}")
            appendLine("smps,totalPowerWatts,${fmt(powerSupplyRequirement.totalPowerWatts)}")
            appendLine("smps,recommendedSmpsWattage,${fmt(powerSupplyRequirement.recommendedSmpsWattage)}")
            appendLine("smps,smpsCount,${powerSupplyRequirement.smpsCount}")
            appendLine("controller,type,${controllerRequirement.controllerType}")
            appendLine("controller,count,${controllerRequirement.controllerCount}")
            appendLine("controller,portUsage,${controllerRequirement.portUsage}")
            appendLine("connector,connectorCount,${connectorRequirement.connectorCount}")
            appendLine("mounting,mountingClipCount,${mountingRequirement.mountingClipCount}")
            appendLine("mounting,mountingTapeLength,${fmt(mountingRequirement.mountingTapeLength)}")
            appendLine("total,totalEstimatedMaterialUnits,$totalEstimatedMaterialUnits")
        }
    }

    fun exportAsTxt(): String {
        return buildString {
            appendLine("BILL OF MATERIALS")
            appendLine("Strip: length=${fmt(stripRequirement.totalStripLength)}m pixels=${stripRequirement.totalPixelCount} rolls=${stripRequirement.stripRollCount}")
            appendLine("Jumpers: count=${jumperRequirement.jumperCount} length=${fmt(jumperRequirement.totalJumperWireLength)}m")
            appendLine("Injection: points=${powerInjectionRequirement.injectionPointCount} length=${fmt(powerInjectionRequirement.injectionWireLength)}m wire=${powerInjectionRequirement.recommendedWireGauge}")
            appendLine("Power: I=${fmt(powerSupplyRequirement.totalCurrent)}A P=${fmt(powerSupplyRequirement.totalPowerWatts)}W SMPS=${powerSupplyRequirement.smpsCount} x ${fmt(powerSupplyRequirement.recommendedSmpsWattage)}W")
            appendLine("Controller: ${controllerRequirement.controllerType} x ${controllerRequirement.controllerCount}, ports=${controllerRequirement.portUsage}")
            appendLine("Connectors: ${connectorRequirement.connectorCount}")
            appendLine("Mounting: clips=${mountingRequirement.mountingClipCount}, tape=${fmt(mountingRequirement.mountingTapeLength)}m")
            appendLine("Total material units: $totalEstimatedMaterialUnits")
        }
    }
}

class BOMGenerator {
    fun generate(
        routePlan: RoutePlan,
        controllerMapExport: ControllerMapExport,
        pixelVoltage: PixelVoltage,
        pixelCurrent: Float,
        pixelSpacing: Float,
        controllerType: ControllerType,
    ): BillOfMaterials {
        require(pixelCurrent > 0f)
        require(pixelSpacing > 0f)

        val routeLengthMeters = computeRouteLength(routePlan)
        val totalPixels = controllerMapExport.totalPixelCount
        val stripRollLength = 5f
        val stripRollCount = ceil(routeLengthMeters / stripRollLength).toInt().coerceAtLeast(1)

        val jumperCount = routePlan.jumpNodes.size
        val jumperLength = computeJumperLength(routePlan)

        val injectionPointCount = controllerMapExport.powerInjectionZones.size.coerceAtLeast(1)
        val injectionWireLength = estimateInjectionWireLength(controllerMapExport, pixelSpacing)
        val recommendedGauge = recommendGauge(totalPixels * pixelCurrent)

        val totalCurrent = totalPixels * pixelCurrent
        val totalPower = totalCurrent * pixelVoltage.volts
        val smpsWattage = recommendSmps(totalPower)
        val smpsCount = ceil((totalPower * 1.2f) / smpsWattage).toInt().coerceAtLeast(1)

        val profile = profileFor(controllerType)
        val controllerCount = ceil(totalPixels / profile.totalCapacity.toFloat()).toInt().coerceAtLeast(1)
        val usedPorts = ceil(totalPixels / profile.pixelsPerPort.toFloat()).toInt().coerceAtMost(profile.portCount)
        val portUsage = "$usedPorts/${profile.portCount}"

        val connectorCount = jumperCount + injectionPointCount + usedPorts
        val clipCount = ceil((routeLengthMeters * 1000f) / 100f).toInt().coerceAtLeast(1)
        val tapeLength = routeLengthMeters

        val stripReq = StripRequirement(routeLengthMeters, totalPixels, stripRollLength, stripRollCount)
        val jumperReq = JumperWireRequirement(jumperCount, jumperLength)
        val injectionReq = PowerInjectionRequirement(injectionPointCount, injectionWireLength, recommendedGauge)
        val powerReq = PowerSupplyRequirement(totalCurrent, totalPower, smpsWattage, smpsCount)
        val controllerReq = ControllerRequirement(controllerType, controllerCount, portUsage)
        val connectorReq = ConnectorRequirement(connectorCount)
        val mountingReq = MountingRequirement(clipCount, tapeLength)

        val totalUnits = stripRollCount + jumperCount + injectionPointCount + smpsCount + controllerCount + connectorCount + clipCount

        return BillOfMaterials(
            stripRequirement = stripReq,
            jumperRequirement = jumperReq,
            powerInjectionRequirement = injectionReq,
            powerSupplyRequirement = powerReq,
            controllerRequirement = controllerReq,
            connectorRequirement = connectorReq,
            mountingRequirement = mountingReq,
            totalEstimatedMaterialUnits = totalUnits,
        )
    }

    private fun computeRouteLength(routePlan: RoutePlan): Float {
        var length = 0f
        routePlan.routeSegments.forEach { seg ->
            seg.points.zipWithNext().forEach { (a, b) -> length += distance(a, b) }
        }
        return length
    }

    private fun computeJumperLength(routePlan: RoutePlan): Float {
        return routePlan.effectiveJumpConnections().sumOf { it.wireLength.toDouble() }.toFloat()
    }

    private fun estimateInjectionWireLength(map: ControllerMapExport, spacing: Float): Float {
        return map.powerInjectionZones.sumOf { zone ->
            ((zone.pixelCount * spacing) / 2f).toDouble()
        }.toFloat()
    }

    private fun recommendGauge(current: Float): String = when {
        current <= 5f -> "18 AWG"
        current <= 10f -> "16 AWG"
        current <= 20f -> "14 AWG"
        else -> "12 AWG"
    }

    private fun recommendSmps(totalPower: Float): Float = when {
        totalPower <= 100f -> 150f
        totalPower <= 200f -> 250f
        totalPower <= 350f -> 400f
        totalPower <= 500f -> 600f
        else -> 800f
    }

    private fun profileFor(type: ControllerType): ControllerProfile = when (type) {
        ControllerType.T1000 -> ControllerProfile(type, 1, 2048, 2048)
        ControllerType.T8000 -> ControllerProfile(type, 8, 1024, 8192)
    }

    private fun distance(a: Vec2, b: Vec2): Float = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    private fun fmt(v: Float): String = "%.3f".format(v)
}
