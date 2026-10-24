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
        assertFalse(SonicCoreDestination.SHIZUKU.isPrimary)
        assertFalse(SonicCoreDestination.CUSTOMIZATION.isPrimary)
        assertFalse(SonicCoreDestination.PERMISSIONS.isPrimary)
        assertFalse(SonicCoreDestination.SETTINGS.isPrimary)
    }

    @Test
    fun everyPlannedPlatformCenterIsReachableFromNavigation() {
        val destinations = SonicCoreDestination.consoleDestinations
        // The seven centers named by the platform specification must each be a
        // real, selectable destination — not a decorative card somewhere.
        listOf(
            SonicCoreDestination.DASHBOARD,
            SonicCoreDestination.EQUALIZER,
            SonicCoreDestination.DEVICE_TOOLS,
            SonicCoreDestination.SHIZUKU,
            SonicCoreDestination.CUSTOMIZATION,
            SonicCoreDestination.DIAGNOSTICS,
            SonicCoreDestination.SETTINGS
        ).forEach { required ->
            assertTrue("$required must be in the console navigation", destinations.contains(required))
        }
    }

    @Test
    fun everyDestinationHasUsableLabelsForTheDock() {
        SonicCoreDestination.consoleDestinations.forEach { destination ->
            assertTrue(destination.title.isNotBlank())
            assertTrue(destination.subtitle.isNotBlank())
            assertTrue(destination.icon.isNotBlank())
            // The dock chip is 72dp wide at 10sp: a long label would be clipped.
            assertTrue(
                "${destination.label} is too long for the dock chip",
                destination.label.length <= 8
            )
        }
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
                SonicCoreDestination.DEVICE_TOOLS,
                SonicCoreDestination.SHIZUKU,
                SonicCoreDestination.CUSTOMIZATION,
                SonicCoreDestination.PERMISSIONS,
                SonicCoreDestination.DIAGNOSTICS,
                SonicCoreDestination.CAPABILITIES,
                SonicCoreDestination.APP_MANAGER,
                SonicCoreDestination.SETTINGS
            ),
            SonicCoreDestination.consoleDestinations
        )
        assertEquals(16, SonicCoreDestination.consoleDestinations.size)
        assertEquals("Pro FX", SonicCoreDestination.PRO_FX.title)
        assertEquals("Diagnostics", SonicCoreDestination.DIAGNOSTICS.title)
        assertEquals("Capabilities", SonicCoreDestination.CAPABILITIES.title)
        assertEquals("Device Tools", SonicCoreDestination.DEVICE_TOOLS.title)
        assertEquals("App Manager", SonicCoreDestination.APP_MANAGER.title)
    }
}
