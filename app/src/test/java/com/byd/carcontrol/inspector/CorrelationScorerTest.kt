package com.byd.carcontrol.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrelationScorerTest {
    @Test
    fun closeBrydLightStateChangeRanksStrongly() {
        val score = CorrelationScorer.score(
            distanceMs = 250,
            stateChanged = true,
            bydRelated = true,
            halOrBinder = true,
            domainKeyword = true,
            repeatedAcrossMarkers = 2,
            inverseTransition = true
        )
        assertEquals(100, score)
    }

    @Test
    fun inverseNumericCallbackOutranksOneOffNoise() {
        val noise = CorrelationScorer.score(4_500, false, false, false, false)
        val callback = CorrelationScorer.score(300, false, true, true, true, repeatedAcrossMarkers = 2, inverseNumericAction = true)
        assertTrue(callback > noise)
    }

    @Test
    fun scoreIsBoundedAndUsesTemporalDistance() {
        assertEquals(25, CorrelationScorer.score(5_001, false, false, false, false))
        assertEquals(100, CorrelationScorer.score(0, true, true, true, true, repeatedAcrossMarkers = 20, inverseTransition = true, inverseNumericAction = true))
        assertTrue(CorrelationScorer.score(-5_000, false, false, false, false) > CorrelationScorer.score(-5_001, false, false, false, false))
    }
}
