package com.neon.eq.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SonicCoreNavigationTest {
    @Test
    fun navigationOffersExactlyTheSixRequestedCategories() {
        assertEquals(
            listOf(
                "Audio",
                "Device Center",
                "Shizuku Center",
                "Customization",
                "Permissions & Privacy",
                "Settings"
            ),
            SonicCoreCategory.ALL.map { it.title }
        )
    }

    @Test
    fun everyDestinationBelongsToExactlyOneCategory() {
        val listed = SonicCoreCategory.allDestinations
        assertEquals(SonicCoreDestination.values().size, listed.size)
        assertEquals(SonicCoreDestination.values().toSet(), listed.toSet())
        // No destination is listed twice, so the dock can never show a duplicate.
        assertEquals(listed.size, listed.toSet().size)
    }

    @Test
    fun everyPlannedPlatformCenterIsReachableFromItsCategory() {
        listOf(
            SonicCoreDestination.DASHBOARD,
            SonicCoreDestination.EQUALIZER,
            SonicCoreDestination.DEVICE_TOOLS,
            SonicCoreDestination.SHIZUKU,
            SonicCoreDestination.CUSTOMIZATION,
            SonicCoreDestination.PERMISSIONS,
            SonicCoreDestination.DIAGNOSTICS,
            SonicCoreDestination.SETTINGS
        ).forEach { required ->
            val owner = SonicCoreCategory.forDestination(required)
            assertTrue(
                "$required must be reachable from its category",
                owner.destinations.contains(required)
            )
        }
    }

    @Test
    fun requiredCentersOwnTheirOwnCategory() {
        assertEquals(SonicCoreCategory.SHIZUKU, SonicCoreCategory.forDestination(SonicCoreDestination.SHIZUKU))
        assertEquals(
            SonicCoreCategory.CUSTOMIZATION,
            SonicCoreCategory.forDestination(SonicCoreDestination.CUSTOMIZATION)
        )
        assertEquals(SonicCoreCategory.PRIVACY, SonicCoreCategory.forDestination(SonicCoreDestination.PERMISSIONS))
        assertEquals(SonicCoreCategory.SETTINGS, SonicCoreCategory.forDestination(SonicCoreDestination.SETTINGS))
        assertEquals(SonicCoreCategory.DEVICE, SonicCoreCategory.forDestination(SonicCoreDestination.DIAGNOSTICS))
    }

    @Test
    fun eachCategoryTabLandsOnADestinationItOwns() {
        SonicCoreCategory.ALL.forEach { category ->
            assertTrue(category.destinations.isNotEmpty())
            assertTrue(
                "${category.title} must land inside itself",
                category.destinations.contains(category.defaultDestination)
            )
        }
    }

    @Test
    fun audioCategoryKeepsThePrimaryDestinations() {
        assertTrue(SonicCoreCategory.AUDIO.destinations.containsAll(SonicCoreDestination.primaryDestinations))
        assertTrue(SonicCoreDestination.primaryDestinations.all { it.isPrimary })
    }

    @Test
    fun secondaryDestinationsAreNotPrimary() {
        listOf(
            SonicCoreDestination.SIGNAL_PATH,
            SonicCoreDestination.SYSTEM_CAPTURE,
            SonicCoreDestination.SESSIONS,
            SonicCoreDestination.PRO_FX,
            SonicCoreDestination.DIAGNOSTICS,
            SonicCoreDestination.CAPABILITIES,
            SonicCoreDestination.DEVICE_TOOLS,
            SonicCoreDestination.APP_MANAGER,
            SonicCoreDestination.SHIZUKU,
            SonicCoreDestination.CUSTOMIZATION,
            SonicCoreDestination.PERMISSIONS,
            SonicCoreDestination.SETTINGS
        ).forEach { assertFalse(it.isPrimary) }
    }

    @Test
    fun everyDestinationHasUsableLabelsForTheDock() {
        SonicCoreDestination.values().forEach { destination ->
            assertTrue(destination.title.isNotBlank())
            assertTrue(destination.subtitle.isNotBlank())
            assertTrue(destination.icon.isNotBlank())
            // The contextual chip is 78dp wide: a long label would be clipped.
            assertTrue(
                "${destination.label} is too long for the destination chip",
                destination.label.length <= 8
            )
        }
        SonicCoreCategory.ALL.forEach { category ->
            assertTrue(category.title.isNotBlank())
            assertTrue(category.icon.isNotBlank())
            // Six categories share the width of the dock, so labels stay short.
            assertTrue(
                "${category.label} is too long for the category tab",
                category.label.length <= 8
            )
        }
    }
}
