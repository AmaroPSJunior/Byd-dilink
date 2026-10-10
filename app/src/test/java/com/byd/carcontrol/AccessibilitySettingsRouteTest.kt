package com.byd.carcontrol

import org.junit.Assert.*
import org.junit.Test

class AccessibilitySettingsRouteTest {
    private val standard = AccessibilitySettingsRoute.Target("com.android.settings", "Settings", true, true, true)
    private val oem = standard.copy(packageName = AccessibilitySettingsRoute.OEM_PACKAGE, name = AccessibilitySettingsRoute.OEM_ACTIVITY)
    @Test fun absentActivitiesDoNotLaunch() {
        assertFalse(AccessibilitySettingsRoute.open({ null }, { _, _ -> fail("Must not launch") }))
    }
    @Test fun standardPreferredAndOemFallback() {
        val launches = mutableListOf<String>()
        assertTrue(AccessibilitySettingsRoute.open({ standard }, { action, _ -> launches.add(action) }))
        assertEquals(listOf(AccessibilitySettingsRoute.STANDARD), launches)
        launches.clear()
        assertTrue(AccessibilitySettingsRoute.open({ if (it == AccessibilitySettingsRoute.OEM) oem else null }, { action, _ -> launches.add(action) }))
        assertEquals(listOf(AccessibilitySettingsRoute.OEM), launches)
    }
    @Test fun launchRacesAndSecurityDenialsAreHandled() {
        for (error in listOf(AccessibilitySettingsRoute.Unavailable(), SecurityException())) {
            assertTrue(AccessibilitySettingsRoute.open({ if (it == AccessibilitySettingsRoute.STANDARD) standard else oem }, { action, _ ->
                if (action == AccessibilitySettingsRoute.STANDARD) throw error
            }))
            assertFalse(AccessibilitySettingsRoute.open({ oem }, { _, _ -> throw error }))
        }
        assertFalse(AccessibilitySettingsRoute.open({ throw SecurityException() }, { _, _ -> fail("denied") }))
    }
    @Test fun unsafeOemTargetsAreRejected() {
        for (target in listOf(oem.copy(exported=false), oem.copy(enabled=false), oem.copy(permitted=false),
            oem.copy(packageName="other"), oem.copy(name="OtherActivity"))) {
            assertFalse(AccessibilitySettingsRoute.open({ if (it == AccessibilitySettingsRoute.OEM) target else null }, { _, _ -> fail("unsafe target") }))
        }
    }
}
