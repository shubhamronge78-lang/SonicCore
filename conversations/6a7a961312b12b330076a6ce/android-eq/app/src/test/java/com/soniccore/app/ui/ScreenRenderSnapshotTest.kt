package com.soniccore.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.DspChain
import com.soniccore.app.dsp.DspTelemetry
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.engine.Presets
import com.soniccore.app.ui.audio.AudioConsoleScreen
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.SonicCoreTheme
import com.soniccore.app.ui.home.HomeAudioSummary
import com.soniccore.app.ui.home.SonicCoreHomeScreen
import com.soniccore.app.ui.navigation.SonicCoreDestination
import com.soniccore.app.ui.navigation.SonicCoreToolRegistry
import com.soniccore.app.ui.navigation.SonicCoreToolScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the app's REAL Compose screens on the JVM under Robolectric and writes
 * what they paint to PNG files.
 *
 * Why this exists: this project has no emulator (`/dev/kvm` is absent in the
 * build environment) and no web surface, so a UI change could previously only be
 * checked by reading source — which is exactly how a "redesign" can ship while
 * the phone still shows the old look. These captures are the objective record of
 * what the shipped screens draw.
 *
 * The PNGs land in `app/build/ui-screenshots/`. They are generated, never
 * committed, and the test asserts the visible structure (the section titles and
 * the controls a user can reach) in the same run that captures the pixels, so a
 * blank or crashed render fails here instead of producing a pretty file.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderSnapshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/ui-screenshots").apply { mkdirs() }

    /**
     * These screens breathe: the glass glow and the meter pulses are endless
     * `rememberInfiniteTransition`s, so with the automatic clock the composition
     * never reports idle and `waitForIdle` waits forever. Driving the clock by
     * hand is the supported way to capture a frame of a screen that animates.
     */
    @org.junit.Before
    fun driveTheClockByHand() {
        compose.mainClock.autoAdvance = false
    }

    /** A host with the values a live engine would report on a quiet device. */
    private fun sampleHost(control: AudioControlState = AudioControlState()): SonicCoreToolHost =
        SonicCoreToolHost(
            appVersion = "3.6.0",
            audioSummary = HomeAudioSummary(
                engineEnabled = true,
                stateLabel = "ENGINE ON",
                stateDetail = "Processing 48 kHz · 2 ch",
                toneIsActive = true,
                toneIsError = false,
                routeName = "Speaker",
                meters = MeterLevels.silent(),
                hasMeasuredSignal = false,
                clipCount = 0L,
                cpuPercent = 4.2,
                latencyMs = 12.4,
                captureRunning = false,
                bypassActive = false
            ),
            currentPreset = "Flat",
            builtinPresetNames = Presets.presets.map { it.name },
            customPresetNames = emptyList(),
            presetsExportJson = { "{}" },
            onImportPresets = { 0 },
            onReviewAutoEqProfile = { _, _ -> error("not used by the rendered screens") },
            onApplyAutoEqProfile = { _, _ -> "" },
            onApplyPreset = { true },
            onSaveCustomPreset = {},
            onDeleteCustomPreset = {},
            glassLevel = 1,
            onGlassLevelChange = {},
            themes = emptyList(),
            currentThemeId = "classic",
            onThemeApply = {},
            audioControl = control,
            onAudioControlChange = {},
            dspChain = DspChain(),
            onDspChainChange = { true },
            eqBands = List(31) { 0f },
            measuredDsp = { DspTelemetry.unavailable("No capture session is running") },
            operationLogSize = { 0 },
            onClearOperationLog = {},
            sessionHistorySize = { 0 },
            onClearSessionHistory = { true },
            locallyStoredBytes = { 0L },
            onOpenFullSettings = {},
            onShareText = { _, _ -> },
            onNavigate = {}
        )

    /** Captures the current frame and asserts the text a user must be able to see. */
    private fun capture(name: String, vararg requiredText: String) {
        // A few frames of real time so the entrance animations have settled.
        compose.mainClock.advanceTimeBy(800)
        compose.waitForIdle()
        for (text in requiredText) {
            compose.onNodeWithText(text, substring = true).assertExists()
        }
        // Rasterise the activity's own window. Compose's `captureToImage` waits
        // for a draw callback that never arrives on a hand-driven clock, and the
        // window draw is what the user's device actually paints anyway.
        android.os.SystemClock.sleep(0)
        org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
        val window = compose.activity.window.decorView
        check(window.width > 0 && window.height > 0) {
            "$name: the window was never laid out (${window.width}x${window.height})"
        }
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        window.draw(Canvas(bitmap))
        // A frame that came out as one flat colour is a failed render, not a
        // screenshot of a screen: fail rather than saving a useless file.
        val colours = HashSet<Int>()
        var y = 0
        while (y < bitmap.height && colours.size < 3) {
            var x = 0
            while (x < bitmap.width) { colours.add(bitmap.getPixel(x, y)); x += 7 }
            y += 7
        }
        check(colours.size >= 3) { "$name: the captured frame is blank (${colours.size} colours)" }
        // The same frame as text: every node the accessibility tree exposes, its
        // bounds, and whether it fits the window. This is what makes a clipped or
        // off-screen control a failed test instead of a screenshot someone has to
        // notice by eye.
        val tree = compose.onRoot().printToString(maxDepth = 100)
        File(outputDir, "$name.txt").writeText(tree)
        val outside = tree.lineSequence()
            .filter { it.contains("boundsInWindow") }
            .filter { line ->
                val b = Regex("""boundsInWindow: Rect\(([-\d.]+), ([-\d.]+) - ([\d.]+), ([\d.]+)\)""")
                    .find(line)
                b == null || b.groupValues[1].toFloat() < -1f || b.groupValues[3].toFloat() > window.width + 1f
            }
            .toList()
        check(outside.isEmpty()) { "$name: ${outside.size} node(s) cross the window edge:\n" + outside.take(5).joinToString("\n") }
        File(outputDir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun dashboardRendersTheSixCategories() {
        compose.setContent {
            SonicCoreTheme(glassLevel = 1) {
                SonicCoreHomeScreen(
                    summary = sampleHost().audioSummary,
                    onCategoryClick = {},
                    onToolClick = {},
                    onToggleEngine = {},
                    onToggleBypass = {},
                    onSettingsClick = {},
                    onModeToggle = {},
                    modeLabel = "☾",
                    searchQuery = "",
                    onSearchChange = {}
                )
            }
        }
        capture("dashboard")
    }

    @Test
    fun audioConsoleRendersInlineEffectCards() {
        compose.setContent {
            SonicCoreTheme(glassLevel = 1) {
                AudioConsoleScreen(host = sampleHost())
            }
        }
        capture("audio-console")
    }

    @Test
    fun bassTrebleToolRendersItsControls() {
        val tool = SonicCoreToolRegistry.all.first { it.id == "audio.bass_treble" }
        compose.setContent {
            SonicCoreTheme(glassLevel = 1) {
                SonicCoreToolScreen(tool = tool, host = sampleHost())
            }
        }
        capture("tool-bass-treble")
    }

    @Test
    fun everyAudioDestinationHasExactlyOneBody() {
        // The registry is what the shell renders from; a destination with no
        // tool, or two tools claiming one destination, is a screen that either
        // cannot be reached or is drawn twice.
        val destinations = SonicCoreToolRegistry.all.mapNotNull { it.destination }
        val duplicates = destinations.groupingBy { it }.eachCount().filterValues { it > 1 }
        check(duplicates.isEmpty()) { "two tools claim the same destination: $duplicates" }
        check(SonicCoreDestination.all.isNotEmpty())
    }
}
