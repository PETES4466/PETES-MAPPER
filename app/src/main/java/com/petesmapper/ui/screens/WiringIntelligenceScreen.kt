package com.petesmapper.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.petesmapper.ui.components.IndustrialPanel
import com.petesmapper.ui.components.StateIndicator
import com.petesmapper.ui.geometry.ComparedRoute
import com.petesmapper.ui.geometry.Contour
import com.petesmapper.ui.geometry.GlyphPathInput
import com.petesmapper.ui.geometry.RouteCompareEngine
import com.petesmapper.ui.geometry.RouteNode
import com.petesmapper.ui.geometry.RouteOptionId
import com.petesmapper.ui.geometry.RoutePatternLibrary
import com.petesmapper.ui.geometry.RoutePlan
import com.petesmapper.ui.geometry.RouteSegment
import com.petesmapper.ui.geometry.RouteSegmentType
import com.petesmapper.ui.geometry.Vec2
import com.petesmapper.ui.theme.AccentAmber
import com.petesmapper.ui.theme.AccentCyan
import com.petesmapper.ui.theme.AccentGreen
import com.petesmapper.ui.theme.AccentRed
import com.petesmapper.ui.theme.GridLine
import com.petesmapper.ui.viewmodel.ProjectViewModel
import com.petesmapper.ui.viewmodel.WiringViewModel

private enum class RoutingWorkflowMode(val label: String, val purpose: String, val actionLabel: String) {
    AUTO_GENERATE("AUTO GENERATE", "Generate full route automatically.", "Run Auto Generate"),
    MANUAL_BUILD("MANUAL BUILD", "Build route manually from blank plan.", "Open Manual Builder"),
    AUTO_EDIT("AUTO + EDIT", "Auto-generate then open manual corrections.", "Generate + Open Editor"),
    LOCK_PATTERN("LOCK PATTERN", "Force one route pattern, no auto override.", "Force Selected Pattern"),
    COMPARE_ROUTES("COMPARE ROUTES", "Generate and compare Route A/B/C options.", "Compare A / B / C"),
}

private val lockablePatterns = listOf("Spiral", "Pillar Bridge", "Island Wrap", "Valley")

@Composable
fun WiringIntelligenceScreen(
    projectViewModel: ProjectViewModel,
    wiringViewModel: WiringViewModel,
    onGenerated: () -> Unit,
) {
    val selectedWiring by wiringViewModel.selectedOption.collectAsState()
    val projectState by projectViewModel.uiState.collectAsState()

    val routePatternLibrary = remember { RoutePatternLibrary() }
    val routeCompareEngine = remember { RouteCompareEngine() }

    var selectedMode by remember { mutableStateOf(RoutingWorkflowMode.AUTO_GENERATE) }
    var forcedPattern by remember { mutableStateOf(lockablePatterns.first()) }
    var activePlan by remember { mutableStateOf<RoutePlan?>(null) }
    var showManualEditor by remember { mutableStateOf(false) }
    var comparisonRoutes by remember { mutableStateOf<List<ComparedRoute>>(emptyList()) }

    fun runRouteGeneration(): RoutePlan {
        val input = toGlyphInput(projectState.letterGeometry.contours)
        return routePatternLibrary.buildRoutePlan(input)
    }

    fun performModeAction() {
        when (selectedMode) {
            RoutingWorkflowMode.AUTO_GENERATE -> {
                activePlan = runRouteGeneration()
                comparisonRoutes = emptyList()
                showManualEditor = false
                onGenerated()
            }

            RoutingWorkflowMode.MANUAL_BUILD -> {
                activePlan = emptyRoutePlan()
                comparisonRoutes = emptyList()
                showManualEditor = true
            }

            RoutingWorkflowMode.AUTO_EDIT -> {
                activePlan = runRouteGeneration()
                comparisonRoutes = emptyList()
                showManualEditor = true
                onGenerated()
            }

            RoutingWorkflowMode.LOCK_PATTERN -> {
                val generated = runRouteGeneration()
                activePlan = generated.copy(
                    metadata = generated.metadata.copy(note = "Locked Pattern: $forcedPattern"),
                )
                comparisonRoutes = emptyList()
                showManualEditor = false
                onGenerated()
            }

            RoutingWorkflowMode.COMPARE_ROUTES -> {
                val result = routeCompareEngine.compare(runRouteGeneration())
                comparisonRoutes = listOf(result.routeA, result.routeB, result.routeC)
                activePlan = result.selectedBest.plan
                showManualEditor = false
                onGenerated()
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            IndustrialPanel("Routing Workflow Mode") {
                RoutingModeCards(selected = selectedMode, onSelect = { selectedMode = it })
                if (selectedMode == RoutingWorkflowMode.LOCK_PATTERN) {
                    PatternPicker(selected = forcedPattern, onSelect = { forcedPattern = it })
                }
                Button(onClick = { performModeAction() }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedMode.actionLabel)
                }
            }
        }

        item {
            IndustrialPanel("Main Workspace") {
                Text("Input: ${projectState.letterGeometrySummary} • ${projectState.controllerType} • ${projectState.voltageType}")
                RouteRenderer(
                    routePlan = activePlan ?: emptyRoutePlan(),
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                )
                StateIndicator(AccentGreen, "Start Point")
                StateIndicator(AccentRed, "End Point")
                StateIndicator(AccentCyan, "Direction Arrows")
                StateIndicator(AccentAmber, "Jump Wires / Segments")
            }
        }

        if (comparisonRoutes.isNotEmpty()) {
            item { Text("Route Comparison", style = MaterialTheme.typography.titleMedium) }
            items(comparisonRoutes) { route ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (route.id) {
                                RouteOptionId.ROUTE_A -> "Route A"
                                RouteOptionId.ROUTE_B -> "Route B"
                                RouteOptionId.ROUTE_C -> "Route C"
                            },
                        )
                        Text("Length ${"%.2f".format(route.metrics.totalStripLength)} • Jumps ${route.metrics.jumpCount} • Turns ${route.metrics.turnCount}")
                        Text("Current ${"%.2f".format(route.metrics.estimatedCurrent)}A • PI ${route.metrics.powerInjectionPoints} • Difficulty ${"%.2f".format(route.metrics.difficultyScore)}")
                    }
                }
            }
        }

        if (showManualEditor) {
            item {
                IndustrialPanel("Manual Route Editor") {
                    ManualRouteEditor(initialPlan = activePlan ?: emptyRoutePlan())
                }
            }
        }

        if (activePlan != null && !showManualEditor) {
            item {
                IndustrialPanel("Active Route") {
                    Text("Nodes: ${activePlan!!.orderedRouteNodes.size}")
                    Text("Segments: ${activePlan!!.routeSegments.size}")
                    Text(activePlan!!.metadata.note)
                }
            }
        }

        item { Text("Wiring Options", style = MaterialTheme.typography.titleMedium) }
        items(wiringViewModel.options) { option ->
            val active = selectedWiring == option.label
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
                onClick = {
                    wiringViewModel.selectOption(option.label)
                    projectViewModel.setWiringPattern(option.label)
                },
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(option.label, style = MaterialTheme.typography.titleMedium)
                    Text("Len ${option.estimatedLengthFt}ft • Jumps ${option.jumpWireCount} • Segments ${option.stripSegments} • PI ${option.powerInjections}")
                }
            }
        }
    }
}

private fun toGlyphInput(contours: List<List<Pair<Float, Float>>>): GlyphPathInput {
    val parsed = contours.map { contour ->
        Contour(points = contour.map { Vec2(it.first, it.second) }, closed = true)
    }
    return GlyphPathInput.InternalContours(parsed)
}

private fun emptyRoutePlan(): RoutePlan {
    val node = RouteNode(0, Vec2(0f, 0f))
    return RoutePlan(
        orderedRouteNodes = listOf(node),
        startNode = node,
        endNode = node,
        jumpNodes = emptyList(),
        routeSegments = listOf(RouteSegment(RouteSegmentType.LOOP, contourIndex = 0, points = listOf(node.point))),
        metadata = com.petesmapper.ui.geometry.RouteMetadata(
            patternId = com.petesmapper.ui.geometry.RoutePatternId.RPL_LINEAR,
            shapeClass = com.petesmapper.ui.geometry.ShapeClass.LINEAR,
            continuousStripPriority = true,
            cutMidwayAvoided = true,
            nearestReconnectUsed = false,
            islandsRespected = true,
            avoidedVoidCrossing = true,
            installerFlowScore = 1f,
            segmentCount = 1,
            note = "Manual blank route",
        ),
    )
}

@Composable
private fun PatternPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Pattern Picker")
        lockablePatterns.forEach { pattern ->
            val isSelected = pattern == selected
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(pattern) },
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else GridLine),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(pattern, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

@Composable
private fun RoutingModeCards(selected: RoutingWorkflowMode, onSelect: (RoutingWorkflowMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RoutingWorkflowMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) },
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else GridLine),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(mode.label, style = MaterialTheme.typography.labelLarge)
                        Text(mode.purpose, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(mode.actionLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
