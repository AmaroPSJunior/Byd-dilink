package com.byd.carcontrol

import org.junit.Assert.*
import org.junit.Test

class SunshadeAutomationPolicyTest {
    private val p = SunshadeAutomationPolicy
    @Test fun safetyFailsClosed() {
        assertTrue(p.safe(0.0, 3, 0, 1))
        assertTrue(p.safe(0.5, 3, 0, 1))
        assertTrue(p.safe(-0.5, 3, 0, 1))
        for (speed in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 0.51, -0.51))
            assertFalse(p.safe(speed, 3, 0, 1))
        for (gear in listOf(null, 0, 1, 2, -1)) assertFalse(p.safe(0.0, gear, 0, 1))
        for (permit in listOf(null, 1, -1, 2)) assertFalse(p.safe(0.0, 3, permit, 1))
        for (init in listOf(null, 0, 2, -1)) assertFalse(p.safe(0.0, 3, 0, init))
    }
    @Test fun requiresExactNodeNotJustLabel() {
        for (a in SunshadeAutomationPolicy.Action.values()) {
            val c = SunshadeAutomationPolicy.Control(p.PACKAGE, a.viewId, a.label, true, true, true)
            assertTrue(p.matches(c, a))
            assertFalse(p.matches(c.copy(id="another"), a))
            assertFalse(p.matches(c.copy(packageName="other"), a))
            assertFalse(p.matches(c.copy(label="Parar"), a))
            assertFalse(p.matches(c.copy(enabled=false), a))
            assertFalse(p.matches(c.copy(visible=false), a))
            assertFalse(p.matches(c.copy(clickable=false), a))
        }
    }
    @Test fun oneShotTimeoutAndAmbiguity() {
        val r = SunshadeAutomationPolicy.Request(SunshadeAutomationPolicy.Action.OPEN, 100)
        assertFalse(r.consume(101, false, true, 1))
        assertFalse(r.consume(101, true, false, 1))
        assertFalse(r.consume(101, true, true, 2))
        assertFalse(r.consume(101, true, true, 0))
        assertTrue(r.consume(102, true, true, 1))
        assertFalse(r.consume(103, true, true, 1))
        val expired = SunshadeAutomationPolicy.Request(SunshadeAutomationPolicy.Action.CLOSE, 100)
        assertFalse(expired.consume(10100, true, true, 1))
        assertFalse(expired.consume(99, true, true, 1))
    }
}
