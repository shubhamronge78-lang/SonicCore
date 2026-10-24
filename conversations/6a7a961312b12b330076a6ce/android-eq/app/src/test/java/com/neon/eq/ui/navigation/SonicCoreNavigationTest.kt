package com.neon.eq.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the SonicCore category navigation.
 *
 * These assertions are the reason a new destination or a new screen cannot be
 * added half-wired: a tool with no screen, two tools claiming the same
 * destination, or a screen nobody can reach all fail here instead of shipping as
 * a dead end in the app.
 */
class SonicCoreNavigationTest {

    // ── Home: exactly six categories ──

    @Test
    fun homeExposesExactlyTheSixRequestedCategoriesInOrder() {
        assertEquals(
            listOf("Audio", "Device Center", "Shizuku Center", "Customization",
                "Permissions & Privacy", "Settings"),
            SonicCoreCategory.entries.map { it.title }
        )
    }

    @Test
    fun everyCategoryHasUsableDockLabels() {
        SonicCoreCategory.entries.forEach { category ->
            assertTrue(category.subtitle.isNotBlank())
            assertTrue(category.icon.isNotBlank())
            // The dock chip is narrow: a long label would be clipped.
            assertTrue(
                "${category.label} is too long for the dock chip",
                category.label.length <= 8
            )
        }
    }

    // ── Tools belong to a category and are reachable exactly once ──

    @Test
    fun everyCategoryHasAtLeastOneTool() {
        SonicCoreCategory.entries.forEach { category ->
            assertTrue(
                "${category.title} has no tools",
                SonicCoreToolRegistry.countFor(category) > 0
            )
        }
        assertEquals(
            SonicCoreToolRegistry.all.size,
            SonicCoreCategory.entries.sumOf { SonicCoreToolRegistry.countFor(it) }
        )
    }

    @Test
    fun toolIdsAreUnique() {
        val ids = SonicCoreToolRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyToolHasExactlyOneRenderingPath() {
        SonicCoreToolRegistry.all.forEach { tool ->
            val reusesFullScreen = tool.destination != null
            val hasOwnScreen = SonicCoreToolRoutes.ids.contains(tool.id)
            assertTrue(
                "${tool.id} has ${if (reusesFullScreen) 1 else 0} full-screen bodies and " +
                    "${if (hasOwnScreen) 1 else 0} own screens — it must have exactly one",
                reusesFullScreen != hasOwnScreen
            )
        }
    }

    @Test
    fun noTwoToolsShareAFullScreenDestination() {
        SonicCoreToolRegistry.all
            .mapNotNull { tool -> tool.destination?.let { it to tool } }
            .forEach { (destination, tool) ->
                assertEquals(
                    "$destination is claimed by more than one tool",
                    tool,
                    SonicCoreToolRegistry.firstFor(destination)
                )
            }
    }

    @Test
    fun everyOwnScreenIsRegisteredForARealTool() {
        SonicCoreToolRoutes.ids.forEach { id ->
            val tool = SonicCoreToolRegistry.find(id)
            assertNotNull("$id is routed to a screen but is not a tool", tool)
            assertNull("$id has a screen and a full-screen body", tool!!.destination)
        }
    }

    @Test
    fun openDestinationResolvesEveryDestinationItIsGiven() {
        SonicCoreDestination.all.forEach { destination ->
            assertNotNull(
                "$destination has no tool that opens it",
                SonicCoreToolRegistry.firstFor(destination)
            )
        }
    }

    @Test
    fun searchFindsToolsByKeywordAndIgnoresBlankQueries() {
        assertEquals(emptyList<SonicCoreTool>(), SonicCoreToolRegistry.search(""))
        assertEquals(emptyList<SonicCoreTool>(), SonicCoreToolRegistry.search("   "))
        assertTrue(
            SonicCoreToolRegistry.search("equalizer").any {
                it.destination == SonicCoreDestination.EQUALIZER
            }
        )
    }

    // ── Back stack: tool → hub → Home ──

    @Test
    fun theStackStartsOnHome() {
        val state = SonicCoreNavState()
        assertTrue(state.isHome)
        assertNull(state.category)
        assertNull(state.tool)
        assertFalse(state.onCategoryHub)
    }

    @Test
    fun openingAToolPushesItsCategoryFirstSoBackAlwaysWalksHome() {
        val home = SonicCoreNavState()
        val tool = SonicCoreToolRegistry.find("device.memory")!!

        val hub = home.selectCategory(tool.category)
        assertTrue(hub.onCategoryHub)
        assertEquals(tool.category, hub.category)
        assertFalse(hub.isHome)
        assertEquals(2, hub.depth)

        val open = hub.openTool(tool)
        assertEquals(tool, open.tool)
        // Home → hub → tool: the hub stays in between, so back is tool → hub → Home.
        assertEquals(3, open.depth)

        val backToHub = open.pop()
        assertEquals(hub, backToHub)

        assertTrue(backToHub.pop().isHome)
    }

    @Test
    fun openingAToolFromHomeStillKeepsTheHubBetweenThem() {
        val tool = SonicCoreToolRegistry.find("settings.about")!!
        val open = SonicCoreNavState().openTool(tool)

        assertEquals(3, open.depth)
        assertEquals(tool.category, open.category)
        assertEquals(tool, open.tool)
        assertTrue(open.pop().onCategoryHub)
    }

    @Test
    fun theOpenToolsWorkspaceStaysHighlightedInTheDock() {
        val memory = SonicCoreToolRegistry.find("device.memory")!!
        val state = SonicCoreNavState().openTool(memory)

        assertEquals(memory.category, state.category)
        // A tool screen is not a hub — only a hub lists the category's tools.
        assertFalse(state.onCategoryHub)
    }

    @Test
    fun openingASecondToolOfTheSameCategoryDoesNotDuplicateTheHub() {
        val equalizer = SonicCoreToolRegistry.find("audio.equalizer")!!
        val presets = SonicCoreToolRegistry.find("audio.presets")!!
        assertEquals(equalizer.category, presets.category)

        val state = SonicCoreNavState().openTool(equalizer).openTool(presets)

        // Home → AUDIO → Equalizer → Presets: no second AUDIO hub.
        assertEquals(4, state.depth)
        assertEquals(presets, state.tool)

        val backToEqualizer = state.pop()
        assertEquals(equalizer, backToEqualizer.tool)
        val backToHub = backToEqualizer.pop()
        assertTrue(backToHub.onCategoryHub)
        assertTrue(backToHub.pop().isHome)
    }

    @Test
    fun homeIsTheFloorAndNeverPopsAway() {
        val home = SonicCoreNavState()
        assertEquals(home, home.pop())
        assertEquals(home, SonicCoreNavState().pop().pop())
    }

    @Test
    fun tappingACategoryInTheDockAlwaysLandsOnItsHub() {
        val memory = SonicCoreToolRegistry.find("device.memory")!!

        // Same category: the open tool is dropped, not buried under its own hub.
        val sameCategory = SonicCoreNavState().openTool(memory).selectCategory(memory.category)
        assertTrue(sameCategory.onCategoryHub)
        assertEquals(memory.category, sameCategory.category)
        assertEquals(2, sameCategory.depth)
        assertTrue(sameCategory.pop().isHome)

        // Different category: the previous workspace is left behind entirely.
        val about = SonicCoreToolRegistry.find("settings.about")!!
        val switched = SonicCoreNavState().openTool(memory).selectCategory(about.category)
        assertTrue(switched.onCategoryHub)
        assertEquals(about.category, switched.category)
        assertNull(switched.tool)
        assertTrue(switched.pop().isHome)
    }

    @Test
    fun theStackIsBounded() {
        var state = SonicCoreNavState()
        // Opening every tool in turn is the deepest the app can actually go.
        SonicCoreToolRegistry.all.forEach { state = state.openTool(it) }
        assertTrue(state.depth <= SonicCoreNavState.MAX_DEPTH)
    }

    @Test
    fun homeResetsTheStackInOneStep() {
        val deep = SonicCoreNavState()
            .openTool(SonicCoreToolRegistry.find("device.memory")!!)
        assertTrue(deep.resetToHome().isHome)
    }

    // ── Surviving process death ──

    @Test
    fun theStackRoundTripsThroughOneEncodedString() {
        val state = SonicCoreNavState()
            .openTool(SonicCoreToolRegistry.find("audio.equalizer")!!)
        assertEquals(state, SonicCoreNavState.decode(state.encode()))
    }

    @Test
    fun decodingDropsTokensThisBuildNoLongerKnows() {
        assertEquals(
            SonicCoreNavState(listOf(SonicCoreRoutes.HOME, "cat:AUDIO")),
            SonicCoreNavState.decode("home|cat:GONE|tool:removed.tool|cat:AUDIO")
        )
    }

    @Test
    fun decodingAnEmptyOrNullStateLandsOnHome() {
        assertTrue(SonicCoreNavState.decode(null).isHome)
        assertTrue(SonicCoreNavState.decode("").isHome)
        assertTrue(SonicCoreNavState.decode("|nonsense|").isHome)
    }

    @Test
    fun everyRouteTokenTheAppCanBuildIsRecognised() {
        assertTrue(SonicCoreRoutes.isKnown(SonicCoreRoutes.HOME))
        SonicCoreCategory.entries.forEach { category ->
            val token = SonicCoreRoutes.category(category)
            assertTrue(SonicCoreRoutes.isKnown(token))
            assertEquals(category, SonicCoreRoutes.categoryOf(token))
            assertNull(SonicCoreRoutes.toolOf(token))
        }
        SonicCoreToolRegistry.all.forEach { tool ->
            val token = SonicCoreRoutes.tool(tool)
            assertTrue(SonicCoreRoutes.isKnown(token))
            assertEquals(tool, SonicCoreRoutes.toolOf(token))
            assertNull(SonicCoreRoutes.categoryOf(token))
        }
        assertFalse(SonicCoreRoutes.isKnown("tool:not.a.tool"))
    }
}
