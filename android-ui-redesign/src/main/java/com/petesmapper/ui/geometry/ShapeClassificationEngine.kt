package com.petesmapper.ui.geometry

class ShapeClassificationEngine(
    private val detector: GeometryFeatureDetector = GeometryFeatureDetector(),
) {

    fun classify(input: GlyphPathInput): ShapeClassificationResult {
        val features = detector.detect(input)
        val (shape, confidence) = classifyFromFeatures(features)
        return ShapeClassificationResult(shapeClass = shape, confidence = confidence, features = features)
    }

    fun classifyFromFeatures(features: GeometryFeatures): Pair<ShapeClass, Float> {
        // Priority order: structural combos > crossings > strong single signatures > open/linear fallback.
        if (features.hasPillars && features.hasBars) return ShapeClass.PILLAR_BRIDGE to 0.92f
        if (features.hasPillars && features.hasValleys) return ShapeClass.PILLAR_VALLEY_COMBO to 0.9f
        if (features.hasIslands && features.hasTail && features.tailPosition == TailPosition.BOTTOM) {
            return ShapeClass.TAIL_EXIT_BOTTOM to 0.91f
        }

        // Crossings should classify CROSS even without bars.
        if (features.hasCrossings && features.crossingCount > 0) return ShapeClass.CROSS to 0.89f

        if (features.hasPillars && features.pillarCount >= 2) return ShapeClass.PILLAR to 0.86f
        if (features.hasValleys && features.valleyDepthAverage > 0.35f) return ShapeClass.VALLEY to 0.84f
        if (features.hasIslands && features.loopCount >= 2) return ShapeClass.ISLAND to 0.87f
        if (features.hasBars) return ShapeClass.BAR to 0.8f
        if (features.hasTail) return ShapeClass.TAIL to 0.78f
        if (features.hasCurves && features.loopCount >= 1) return ShapeClass.SPIRAL to 0.7f

        val trunk = features.mainTrunk
        if (features.openContour) {
            val trunkLikelyLinear = trunk != null && trunk.length > 0f
            return if (trunkLikelyLinear) ShapeClass.LINEAR to 0.72f else ShapeClass.HYBRID to 0.55f
        }

        val complexSignals = listOf(
            features.hasPillars,
            features.hasIslands,
            features.hasValleys,
            features.hasTail,
            features.hasCrossings,
            features.hasBars,
            features.hasCurves,
            features.loopCount > 1,
            features.mainTrunk != null,
        ).count { it }

        return if (complexSignals >= 3) ShapeClass.HYBRID to 0.7f else ShapeClass.LINEAR to 0.58f
    }
}
