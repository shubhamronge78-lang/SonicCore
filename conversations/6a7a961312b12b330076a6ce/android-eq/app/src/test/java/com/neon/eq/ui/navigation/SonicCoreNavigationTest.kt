package com.neon.eq.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonicCoreNavigationTest {
    @Test
    fun primaryNavigationContainsOnlyTheFourRequestedDestinations() {
        assertEquals(
            listOf(
                SonicCoreDestination.DASHBOARD,
                SonicCoreDestination.EQUALIZER,
                SonicCoreDestination.PLAYER,
                SonicCoreDestination.DSP_FX
            ),
            SonicCoreDestination.primaryDestinations
        )
        assertTrue(SonicCoreDestination.primaryDestinations.all { it.isPrimary })
    }

    @Test
    fun settingsAndSessionScreensAreNotPrimaryDestinations() {
        assertFalse(SonicCoreDestination.SIGNAL_PATH.isPrimary)
        assertFalse(SonicCoreDestination.SYSTEM_CAPTURE.isPrimary)
        assertFalse(SonicCoreDestination.SESSIONS.isPrimary)
        assertFalse(SonicCoreDestination.PRO_FX.isPrimary)
        assertFalse(SonicCoreDestination.DIAGNOSTICS.isPrimary)
        assertFalse(SonicCoreDestination.CAPABILITIES.isPrimary)
        assertFalse(SonicCoreDestination.DEVICE_TOOLS.isPrimary)
        assertFalse(SonicCoreDestination.APP_MANAGER.isPrimary)
    }

    @Test
    fun consoleNavigationContainsDestinationsInTheRequestedOrder() {
        assertEquals(
            listOf(
                SonicCoreDestination.DASHBOARD,
                SonicCoreDestination.EQUALIZER,
                SonicCoreDestination.PLAYER,
                SonicCoreDestination.DSP_FX,
                SonicCoreDestination.SYSTEM_CAPTURE,
                SonicCoreDestination.SESSIONS,
                SonicCoreDestination.SIGNAL_PATH,
                SonicCoreDestination.PRO_FX,
                SonicCoreDestination.DIAGNOSTICS,
                SonicCoreDestination.CAPABILITIES,
                SonicCoreDestination.DEVICE_TOOLS,
                SonicCoreDestination.APP_MANAGER
            ),
            SonicCoreDestination.consoleDestinations
        )
        assertEquals(12, SonicCoreDestination.consoleDestinations.size)
        assertEquals("Pro FX", SonicCoreDestination.PRO_FX.title)
        assertEquals("Diagnostics", SonicCoreDestination.DIAGNOSTICS.title)
        assertEquals("Capabilities", SonicCoreDestination.CAPABILITIES.title)
        assertEquals("Device Tools", SonicCoreDestination.DEVICE_TOOLS.title)
        assertEquals("App Manager", SonicCoreDestination.APP_MANAGER.title)
    }
}
