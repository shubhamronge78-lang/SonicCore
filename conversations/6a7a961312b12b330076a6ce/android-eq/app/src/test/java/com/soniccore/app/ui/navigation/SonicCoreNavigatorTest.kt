package com.soniccore.app.ui.navigation

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The navigator is what a screen reads to know where it is and what it calls to
 * move.
 *
 * The regression these tests lock: the frame must be read LIVE. The launcher
 * kept a copy of the frame (and of the navigator) from the first composition, so
 * a tap moved the stack while the screen kept rendering Home — the classic
 * "visible but dead to the touch" build. A navigator captured before a move must
 * therefore report the frame AFTER the move, and must move the CURRENT frame.
 */
class SonicCoreNavigatorTest {

    /** Hosts the frame the way `SonicCoreNavHost` does: one live Compose state. */
    private class Host {
        val frame = mutableStateOf(SonicCoreNavState())
        val navigator: SonicCoreNavigator =
            SonicCoreNavigator(frame) { next -> frame.value = next }
    }

    @Test
    fun aNavigatorCapturedBeforeAMoveStillReadsTheCurrentFrame() {
        val host = Host()
        val captured = host.navigator            // captured by an earlier composition
        assertTrue(captured.isHome)

        host.navigator.openCategory(SonicCoreCategory.AUDIO)

        // No stale copy anywhere: the captured handle reports the moved frame.
        assertFalse(captured.isHome)
        assertEquals(SonicCoreCategory.AUDIO, captured.category)
        assertTrue(captured.state.onCategoryHub)
    }

    @Test
    fun backPopsTheCurrentFrameNotTheFrameTheScreenStartedOn() {
        val host = Host()
        val captured = host.navigator
        val equalizer = SonicCoreToolRegistry.find("audio.equalizer")!!

        captured.openTool(equalizer)
        assertEquals(equalizer, host.frame.value.tool)

        // Walked back through the SAME captured handle: tool → hub → Home.
        captured.back()
        assertEquals(SonicCoreCategory.AUDIO, host.frame.value.category)
        assertTrue(host.frame.value.onCategoryHub)

        captured.back()
        assertTrue(host.frame.value.isHome)
    }

    @Test
    fun everyCategoryMoveLandsOnItsOwnHub() {
        SonicCoreCategory.entries.forEach { category ->
            val host = Host()
            host.navigator.openCategory(category)

            val frame = host.frame.value
            assertFalse("${category.title} did not leave Home", frame.isHome)
            assertTrue(frame.onCategoryHub)
            assertEquals(category, frame.category)
        }
    }

    @Test
    fun everyDestinationMoveLandsOnTheToolThatRendersIt() {
        SonicCoreDestination.all.forEach { destination ->
            val host = Host()
            assertTrue(
                "$destination has no tool, so tapping it would do nothing",
                host.navigator.openDestination(destination)
            )
            assertEquals(destination, host.frame.value.tool?.destination)
        }
    }

    @Test
    fun homeResetsAStackOpenedThroughTheNavigator() {
        val host = Host()
        host.navigator.openTool(SonicCoreToolRegistry.find("device.memory")!!)
        host.navigator.home()
        assertTrue(host.frame.value.isHome)
    }
}
