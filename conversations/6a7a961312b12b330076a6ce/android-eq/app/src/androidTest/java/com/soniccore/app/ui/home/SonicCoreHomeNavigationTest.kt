package com.soniccore.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.ui.design.SonicCoreTheme
import com.soniccore.app.ui.navigation.SonicCoreCategory
import com.soniccore.app.ui.navigation.SonicCoreNavState
import com.soniccore.app.ui.navigation.SonicCoreNavigator
import com.soniccore.app.ui.navigation.SonicCoreToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the launcher's taps.
 *
 * This is the only test in the project that presses a real Home card: it drives
 * the production [SonicCoreHomeScreen] with the production [SonicCoreNavigator]
 * and asserts that the tapped card moves the frame AND that what is rendered
 * follows it (the failure mode fixed here was a screen that kept re-rendering
 * the frame it started on, so taps looked dead).
 *
 * Requires a connected device or emulator:
 * `./gradlew :app:connectedDebugAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class SonicCoreHomeNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    private val summary = HomeAudioSummary(
        engineEnabled = false,
        stateLabel = "READY",
        stateDetail = "No capture session — nothing is being processed",
        toneIsActive = false,
        toneIsError = false,
        routeName = "Not detected",
        meters = MeterLevels.silent(),
        hasMeasuredSignal = false,
        clipCount = 0L,
        cpuPercent = null,
        latencyMs = null,
        captureRunning = false
    )

    /** The three reads the launcher body dispatches on. */
    private fun screenOf(state: SonicCoreNavState): String {
        val tool = state.tool
        return when {
            state.isHome -> "HOME"
            tool != null -> "TOOL ${tool.id}"
            else -> "HUB ${state.category?.name}"
        }
    }

    @Test
    fun everyHomeCardMovesTheFrameAndTheRenderedScreen() {
        val frame = mutableStateOf(SonicCoreNavState())
        val navigator = SonicCoreNavigator(frame) { next -> frame.value = next }

        rule.setContent {
            SonicCoreTheme(glassLevel = 1) {
                val state = frame.value
                // The launcher body renders its screens inside a vertically
                // scrollable column (`mainContent` in MainActivity.kt), and the
                // cards sit below the fold on a real phone — so the test scrolls
                // them into view exactly like the app does.
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (state.isHome) {
                        SonicCoreHomeScreen(
                            summary = summary,
                            onCategoryClick = { navigator.openCategory(it) },
                            onToolClick = { navigator.openTool(it) },
                            onToggleEngine = {},
                            onToggleBypass = {},
                            onSettingsClick = {},
                            onModeToggle = {},
                            modeLabel = "☾",
                            searchQuery = "",
                            onSearchChange = {}
                        )
                    } else {
                        Text(screenOf(state))
                    }
                }
            }
        }

        rule.onNodeWithText("HOME").assertIsDisplayed()

        SonicCoreCategory.entries.forEach { category ->
            rule.onNodeWithText(category.title).performScrollTo().performClick()

            // The tap moved the stack...
            assertEquals(category, frame.value.category)
            assertFalse("${category.title} did not leave Home", frame.value.isHome)
            // ...and the visible screen followed it.
            rule.onNodeWithText("HUB ${category.name}").assertIsDisplayed()
            assertTrue(
                "the dock chip label for ${category.title} is too long",
                SonicCoreToolRegistry.countFor(category) > 0
            )

            // Back returns to Home, through the same screen body.
            navigator.back()
            rule.onNodeWithText("HOME").assertIsDisplayed()
            assertTrue(frame.value.isHome)
        }
    }
}
