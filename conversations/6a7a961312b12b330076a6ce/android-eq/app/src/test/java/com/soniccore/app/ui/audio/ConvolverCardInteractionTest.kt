package com.soniccore.app.ui.audio

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.ChainStage
import com.soniccore.app.dsp.DspChain
import com.soniccore.app.dsp.DspTelemetry
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.engine.Presets
import com.soniccore.app.engine.StoredImpulse
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.SonicCoreTheme
import com.soniccore.app.ui.home.HomeAudioSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Presses the Convolver card instead of only rendering it.
 *
 * The Convolver was the one stage with no input at all; it now has a real one.
 * A card that *looked* right while its switch changed nothing would be exactly
 * the defect this project keeps closing, so the two behaviours that matter are
 * driven here with a real tap:
 *
 *  - with a response loaded, pressing the switch writes a chain the host
 *    receives, with the stage un-bypassed (the same flag the engine reads);
 *  - with nothing loaded, pressing it changes nothing and the screen says why,
 *    rather than pretending to process.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConvolverCardInteractionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private class Recorder(start: DspChain, val response: StoredImpulse?) {
        val applied = mutableListOf<DspChain>()
        var chain = start
    }

    private fun stored() = StoredImpulse(
        name = "hall.wav",
        left = FloatArray(128) { if (it == 0) 1f else 0f },
        right = FloatArray(128) { if (it == 0) 1f else 0f },
        sourceSampleRate = 48000,
        sourceFrames = 128,
        peak = 1f
    )

    private fun hostFor(recorder: Recorder): SonicCoreToolHost = SonicCoreToolHost(
        appVersion = "3.6.0",
        audioSummary = HomeAudioSummary(
            engineEnabled = true,
            stateLabel = "READY",
            stateDetail = "No capture session",
            toneIsActive = false,
            toneIsError = false,
            routeName = "Speaker",
            meters = MeterLevels.silent(),
            hasMeasuredSignal = false,
            clipCount = 0L,
            cpuPercent = null,
            latencyMs = null,
            captureRunning = false,
            bypassActive = false
        ),
        currentPreset = "Flat",
        builtinPresetNames = Presets.presets.map { it.name },
        customPresetNames = emptyList(),
        presetsExportJson = { "{}" },
        onImportPresets = { 0 },
        onReviewAutoEqProfile = { _, _ -> error("not used by this test") },
        onApplyAutoEqProfile = { _, _ -> "" },
        onApplyPreset = { true },
        onSaveCustomPreset = {},
        onDeleteCustomPreset = {},
        glassLevel = 1,
        onGlassLevelChange = {},
        themes = emptyList(),
        currentThemeId = "classic",
        onThemeApply = {},
        audioControl = AudioControlState(),
        onAudioControlChange = {},
        dspChain = recorder.chain,
        onDspChainChange = { next -> recorder.applied.add(next); recorder.chain = next; true },
        eqBands = List(31) { 0f },
        measuredDsp = { DspTelemetry.unavailable("No capture session is running") },
        impulseResponse = recorder.response,
        onLoadImpulseResponse = { _, _ -> "not used by this test" },
        onClearImpulseResponse = { "Impulse response removed — the Convolver stage passes audio through unchanged." },
        operationLogSize = { 0 },
        onClearOperationLog = {},
        sessionHistorySize = { 0 },
        onClearSessionHistory = { true },
        locallyStoredBytes = { 0L },
        onOpenFullSettings = {},
        onShareText = { _, _ -> },
        onNavigate = {}
    )

    private fun render(recorder: Recorder) {
        compose.setContent {
            SonicCoreTheme(glassLevel = 1) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AudioConsoleScreen(
                        host = hostFor(recorder),
                        modifier = Modifier
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    /**
     * Presses the card's switch.
     *
     * The press goes through the node's own `OnClick` semantics action — the same
     * action a tap dispatches — rather than a screen coordinate: the console is
     * longer than the test window, so this card sits below the fold with zero
     * visible bounds, and a coordinate tap would land on whatever is on screen
     * instead. That false pass is exactly what this project has been burned by.
     */
    private fun tapConvolverSwitch() {
        compose.onNodeWithContentDescription("Enable the convolver stage")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun theCardShowsTheLoadedResponseAndItsSwitchWritesTheChain() {
        val start = DspChain.default().withBypass(ChainStage.CONVOLVER, true)
        val recorder = Recorder(start, stored())
        render(recorder)

        // The card states what is loaded — name, taps and source rate, from the
        // stored response itself rather than a label the screen invented.
        compose.onNodeWithText("hall.wav loaded · stage bypassed", substring = true).assertExists()
        compose.onNodeWithText("LOAD .WAV", substring = true).assertExists()
        compose.onNodeWithText("CLEAR", substring = true).assertExists()

        assertTrue("the stage starts bypassed in this fixture", start.isBypassed(ChainStage.CONVOLVER))
        tapConvolverSwitch()

        assertTrue("the switch must write a chain", recorder.applied.isNotEmpty())
        val written = recorder.applied.last()
        assertEquals(
            "the switch must un-bypass the convolver stage the engine reads",
            false,
            written.isBypassed(ChainStage.CONVOLVER)
        )
        assertTrue(
            "the rest of the chain must be untouched",
            written.order == DspChain.default().order
        )
    }

    @Test
    fun withNothingLoadedTheSwitchChangesNothingAndSaysWhy() {
        val recorder = Recorder(DspChain.default(), response = null)
        render(recorder)

        compose.onNodeWithText("No impulse response loaded · pass-through", substring = true).assertExists()

        tapConvolverSwitch()

        assertTrue("no chain may be written with nothing loaded", recorder.applied.isEmpty())
        // The console shows the card's sentence in its limit note, so the reason
        // has to be readable on the screen itself.
        compose.onNodeWithText("Load an impulse response first", substring = true).assertExists()
    }
}
