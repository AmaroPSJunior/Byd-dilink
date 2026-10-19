package com.byd.carcontrol

import org.junit.Assert.*
import org.junit.Test

class HvacPowerPolicyTest {
    @Test fun unavailableDoesNotCallEitherControl() {
        val result = HvacPowerPolicy.execute(false, false,
            direct = { error("must not write") }, accessible = { error("must not automate") })
        assertFalse(result.accepted)
        assertFalse(result.confirmed)
        assertTrue(result.manualInteractionRequired)
        assertEquals(409, result.httpStatus)
    }

    @Test fun directAcceptanceNeverConfirmsState() {
        var writes = 0
        val result = HvacPowerPolicy.execute(false, true,
            direct = { writes++; "aceito; raw=1" }, accessible = { error("must not automate") })
        assertEquals(1, writes)
        assertTrue(result.accepted)
        assertFalse(result.confirmed)
        assertEquals(202, result.httpStatus)
    }

    @Test fun deniedOrUncertainWriteIsNeverRetried() {
        var writes = 0
        val result = HvacPowerPolicy.execute(false, true,
            direct = { writes++; throw SecurityException("backend denied") },
            accessible = { error("must not try another control") })
        assertEquals(1, writes)
        assertFalse(result.confirmed)
        assertTrue(result.manualInteractionRequired)
    }

    @Test fun existingAuthorizedServiceHasPriorityWithoutDirectWrite() {
        val expected = HvacPowerPolicy.Result(true, true, "oem_accessibility", "confirmed by state")
        val result = HvacPowerPolicy.execute(true, true,
            direct = { error("must not write") }, accessible = { expected })
        assertEquals(expected, result)
        assertEquals(200, result.httpStatus)
    }

    @Test fun unconfirmedAccessibilityResultIsNotSuccess() {
        val result = HvacPowerPolicy.execute(true, false, direct = { error("must not write") },
            accessible = { HvacPowerPolicy.Result(true, false, "oem_accessibility", "waiting") })
        assertEquals(202, result.httpStatus)
        assertFalse(result.confirmed)
    }
}
