package com.soniccore.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression contract for the scroll-ownership rule (responsive audit RD-01, RD-14).
 *
 * The shell owns exactly one vertical scroll for the whole body, and no screen
 * may nest a second same-axis scroll inside it — Compose rejects a scrollable
 * measured with its parent's infinite maximum height, which is what broke eight
 * tool bodies. The one exception is a screen whose body *is* a lazy list: it
 * declares `ScrollOwner.SCREEN`, and the shell gives it the remaining height
 * instead of a scroll container.
 *
 * These assertions pin which destinations are allowed to make that claim, so a
 * second screen cannot start scrolling itself by accident.
 */
class ScrollOwnershipTest {

    private val registry = SonicCoreToolRegistry.all

    @Test
    fun everyToolLeavesTheShellInChargeByDefault() {
        val screenOwned = registry.filter { it.scrollOwner == ScrollOwner.SCREEN }
        assertEquals(
            "only the lazy catalogue may own its own scroll",
            listOf(SonicCoreToolRoutes.CUSTOM_REPOSITORIES),
            screenOwned.map { it.id }
        )
    }

    @Test
    fun homeAndCategoryHubsAlwaysUseTheShellScroll() {
        val home = SonicCoreNavState()
        assertTrue(home.isHome)
        assertEquals(ScrollOwner.SHELL, home.scrollOwner)
        assertFalse(home.screenOwnsScroll)

        SonicCoreCategory.entries.forEach { category ->
            val hub = SonicCoreNavState().selectCategory(category)
            assertTrue("${category.title} hub is not a hub", hub.onCategoryHub)
            assertEquals("${category.title} hub must not own a scroll", ScrollOwner.SHELL, hub.scrollOwner)
        }
    }

    @Test
    fun theRepositoryManagerFrameOwnsItsScrollAndEveryOtherFrameDoesNot() {
        val repositoryTool = registry.first { it.id == SonicCoreToolRoutes.CUSTOM_REPOSITORIES }
        val repositoryFrame = SonicCoreNavState().openTool(repositoryTool)
        assertEquals(repositoryTool, repositoryFrame.tool)
        assertTrue(repositoryFrame.screenOwnsScroll)

        registry.filter { it.destination == null && it.id != SonicCoreToolRoutes.CUSTOM_REPOSITORIES }
            .forEach { tool ->
                val frame = SonicCoreNavState().openTool(tool)
                assertFalse(
                    "${tool.id} would nest a scroll inside the shell's container",
                    frame.screenOwnsScroll
                )
            }
    }

    @Test
    fun theShellsScrollIsKeyedPerDestinationSoEveryScreenOpensAtTheTop() {
        // The shell remembers its scroll container on `scrollKey`, so:
        //  * two different destinations never share a scroll position,
        val home = SonicCoreNavState()
        val audioHub = SonicCoreNavState().selectCategory(SonicCoreCategory.AUDIO)
        val presetsTool = registry.first { it.id == SonicCoreToolRoutes.AUDIO_PRESETS }
        val presets = SonicCoreNavState().openTool(presetsTool)
        assertEquals("AUDIO/audio.presets", presets.scrollKey)
        assertEquals("AUDIO/-", audioHub.scrollKey)
        assertEquals("-/-", home.scrollKey)
        assertEquals(
            "two destinations share one scroll key",
            3,
            setOf(home.scrollKey, audioHub.scrollKey, presets.scrollKey).size
        )

        //  * the same destination reached the same way gets the same key back, so
        //    returning to it reuses one container instead of stacking another,
        assertEquals(presets.scrollKey, SonicCoreNavState().openTool(presetsTool).scrollKey)

        //  * and a hub is a different destination from the tools inside it, even
        //    though they share a category.
        assertNotEquals(audioHub.scrollKey, presets.scrollKey)

        // Every tool in the registry has its own key, so none can be handed
        // another screen's scroll offset.
        assertEquals(
            registry.size,
            registry.map { SonicCoreNavState().openTool(it).scrollKey }.toSet().size
        )
    }

    @Test
    fun theRepositoryToolIsStillRoutedToItsOwnScreen() {
        // A tool with a destination renders a full-screen body instead, which
        // would leave the declared scroll owner meaningless.
        val repositoryTool = registry.first { it.id == SonicCoreToolRoutes.CUSTOM_REPOSITORIES }
        assertNull(repositoryTool.destination)
        assertTrue(SonicCoreToolRoutes.ids.contains(SonicCoreToolRoutes.CUSTOM_REPOSITORIES))
    }
}
