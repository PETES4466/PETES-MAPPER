package com.petesmapper.ui.geometry

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

enum class RouteOptionId { ROUTE_A, ROUTE_B, ROUTE_C }

data class RouteMetrics(
    val totalStripLength: Float,
    val jumpCount: Int,
    val turnCount: Int,
    val estimatedCurrent: Float,
    val powerInjectionPoints: Int,
    val difficultyScore: Float,
    val installerFlowScore: Float,
)

data class ComparedRoute(
    val id: RouteOptionId,
    val plan: RoutePlan,
    val metrics: RouteMetrics,
)

data class RouteComparisonResult(
    val routeA: ComparedRoute,
    val routeB: ComparedRoute,
    val routeC: ComparedRoute,
    val selectedBest: ComparedRoute,
)

class RouteCompareEngine {

    fun compare(basePlan: RoutePlan): RouteComparisonResult {
        val routeAPlan = basePlan
        val routeBPlan = basePlan.copy(
            orderedRouteNodes = basePlan.orderedRouteNodes.reversed().mapIndexed { i, n -> RouteNode(i, n.point) },
            startNode = basePlan.endNode,
            endNode = basePlan.startNode,
        )
        val routeCPlan = optimizeJumpOrder(basePlan)

        val routeA = ComparedRoute(RouteOptionId.ROUTE_A, routeAPlan, calculateMetrics(routeAPlan))
        val routeB = ComparedRoute(RouteOptionId.ROUTE_B, routeBPlan, calculateMetrics(routeBPlan))
        val routeC = ComparedRoute(RouteOptionId.ROUTE_C, routeCPlan, calculateMetrics(routeCPlan))

        val best = selectBestRoute(listOf(routeA, routeB, routeC))
        return RouteComparisonResult(routeA, routeB, routeC, best)
    }

    fun selectBestRoute(routes: List<ComparedRoute>): ComparedRoute {
        return routes.minByOrNull { route ->
            val m = route.metrics
            (m.totalStripLength * 0.22f) +
                (m.jumpCount * 8f) +
                (m.turnCount * 0.9f) +
                (m.difficultyScore * 2.4f) +
                (m.powerInjectionPoints * 4f) -
                (m.installerFlowScore * 10f)
        } ?: routes.first()
    }

    private fun calculateMetrics(plan: RoutePlan): RouteMetrics {
        val nodes = plan.orderedRouteNodes
        val totalStripLength = nodes.zipWithNext { a, b -> distance(a.point, b.point) }.sum()
        val jumpCount = plan.jumpNodes.size
        val turnCount = countTurns(nodes)

        // 0.06A per unit length baseline + jump penalty.
        val estimatedCurrent = (totalStripLength * 0.06f) + (jumpCount * 0.35f)
        val powerInjectionPoints = maxOf(1, (estimatedCurrent / 10f).toInt() + if (jumpCount > 4) 1 else 0)

        val difficultyScore = (
            (jumpCount * 1.5f) +
                (turnCount * 0.35f) +
                (powerInjectionPoints * 1.2f)
            ).coerceAtLeast(0f)

        val installerFlowScore = (
            (plan.metadata.installerFlowScore * 0.7f) +
                ((1f - (jumpCount / (nodes.size.coerceAtLeast(1).toFloat()))).coerceIn(0f, 1f) * 0.3f)
            ).coerceIn(0f, 1f)

        return RouteMetrics(
            totalStripLength = totalStripLength,
            jumpCount = jumpCount,
            turnCount = turnCount,
            estimatedCurrent = estimatedCurrent,
            powerInjectionPoints = powerInjectionPoints,
            difficultyScore = difficultyScore,
            installerFlowScore = installerFlowScore,
        )
    }

    private fun optimizeJumpOrder(plan: RoutePlan): RoutePlan {
        val nodes = plan.orderedRouteNodes
        if (nodes.size < 4) return plan

        val start = nodes.first()
        val remaining = nodes.drop(1).toMutableList()
        val ordered = mutableListOf(start)

        while (remaining.isNotEmpty()) {
            val prev = ordered.last()
            val next = remaining.minByOrNull { distance(prev.point, it.point) } ?: remaining.first()
            ordered += next
            remaining.remove(next)
        }

        val remapped = ordered.mapIndexed { idx, node -> RouteNode(idx, node.point) }
        return plan.copy(
            orderedRouteNodes = remapped,
            startNode = remapped.first(),
            endNode = remapped.last(),
        )
    }

    private fun countTurns(nodes: List<RouteNode>): Int {
        if (nodes.size < 3) return 0
        var turns = 0
        for (i in 1 until nodes.lastIndex) {
            val a = nodes[i - 1].point
            val b = nodes[i].point
            val c = nodes[i + 1].point
            val v1 = Vec2(b.x - a.x, b.y - a.y)
            val v2 = Vec2(c.x - b.x, c.y - b.y)
            val dot = (v1.x * v2.x) + (v1.y * v2.y)
            val len = maxOf(0.0001f, distance(Vec2(0f, 0f), v1) * distance(Vec2(0f, 0f), v2))
            val cos = (dot / len).coerceIn(-1f, 1f)
            if (abs(cos) < 0.94f) turns++
        }
        return turns
    }

    private fun distance(a: Vec2, b: Vec2): Float = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
}
