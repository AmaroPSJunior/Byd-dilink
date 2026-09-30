package com.byd.carcontrol.inspector

/** Transparent heuristic weights; this score is not a probability of causation. */
object CorrelationScorer {
    fun score(
        distanceMs: Long,
        stateChanged: Boolean,
        bydRelated: Boolean,
        halOrBinder: Boolean,
        domainKeyword: Boolean,
        repeatedAcrossMarkers: Int = 0,
        inverseTransition: Boolean = false,
        inverseNumericAction: Boolean = false
    ): Int {
        var score = 25
        score += when {
            kotlin.math.abs(distanceMs) <= 1_000 -> 20
            kotlin.math.abs(distanceMs) <= 3_000 -> 14
            kotlin.math.abs(distanceMs) <= 5_000 -> 8
            else -> 0
        }
        if (stateChanged) score += 15
        if (bydRelated) score += 15
        if (halOrBinder) score += 10
        if (domainKeyword) score += 15
        if (repeatedAcrossMarkers > 0) score += (repeatedAcrossMarkers * 8).coerceAtMost(24)
        if (inverseTransition) score += 20
        if (inverseNumericAction) score += 10
        return score.coerceIn(0, 100)
    }
}
