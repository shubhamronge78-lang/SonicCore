package com.soniccore.app.ui.audio

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.ChainStage
import com.soniccore.app.dsp.DspChain
import com.soniccore.app.dsp.DspChainRules
import com.soniccore.app.dsp.DspTelemetry
import com.soniccore.app.dsp.MeterLevels
import com.soniccore.app.engine.Presets
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.design.SonicCoreTheme
import com.soniccore.app.ui.home.HomeAudioSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Presses the controls of the shipped chain editor instead of only looking at
 * them.
 *
 * The render tests prove a screen *looks* redesigned; they cannot tell a working
 * switch from a decorative one. This test drives the two controls the redesign
 * added — the per-stage enable switch and the reorder buttons — and asserts the
 * chain that leaves the screen, which is the chain `MainActivity` hands to the
 * engine. It runs against [SignalChainEditor] itself: the one editor both the
 * Audio Console and the DSP Studio render.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChainEditorInteractionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * What the screen sent out. `chain` holds the value the host accepted — the
     * chain the app persists and pushes to the native stage flags.
     */
    private class Recorder(start: DspChain) {
        val chain = mutableStateOf(start)
        val limiterOn = mutableStateOf(true)
        val status = mutableStateOf<String?>(null)
        val applied = mutableListOf<DspChain>()
        var engineAccepts = true
    }

    /**
     * These screens breathe (the glass glow is an endless transition), so the
     * clock is driven by hand, the same way the render tests do it.
     */
    @org.junit.Before
    fun driveTheClockByHand() {
        compose.mainClock.autoAdvance = false
    }

    private fun render(start: DspChain = DspChain.default()): Recorder {
        val recorder = Recorder(start)
        compose.setContent {
            SonicCoreTheme {
                SignalChainEditor(
                    chain = recorder.chain.value,
                    limiterOn = recorder.limiterOn.value,
                    onChainChange = { next ->
                        recorder.applied.add(next)
                        if (recorder.engineAccepts) recorder.chain.value = next
                        recorder.engineAccepts
                    },
                    onStatus = { message -> recorder.status.value = message }
                )
            }
        }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        return recorder
    }

    /** One press on a stage's switch, delivered as a real tap on that node. */
    private fun tapSwitch(label: String) {
        compose.onNodeWithContentDescription(label).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(256)
        compose.waitForIdle()
    }

    private fun tapButton(label: String) {
        compose.onNodeWithContentDescription(label).performClick()
        compose.mainClock.advanceTimeBy(256)
        compose.waitForIdle()
    }

    /** The host snapshot a quiet device would give the console. */
    private fun consoleHost(onChain: (DspChain) -> Boolean): SonicCoreToolHost = SonicCoreToolHost(
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
        dspChain = DspChain.default(),
        onDspChainChange = onChain,
        eqBands = List(31) { 0f },
        measuredDsp = { DspTelemetry.unavailable("No capture session is running") },
        impulseResponse = null,
        onLoadImpulseResponse = { _, _ -> "" },
        onClearImpulseResponse = { "" },
        operationLogSize = { 0 },
        onClearOperationLog = {},
        sessionHistorySize = { 0 },
        onClearSessionHistory = { true },
        locallyStoredBytes = { 0L },
        onOpenFullSettings = {},
        onShareText = { _, _ -> },
        onNavigate = {}
    )

    @Test
    fun aStageSwitchBypassesOnlyTheStageItNames() {
        val recorder = render()

        tapSwitch("Enable Preamp")
        assertEquals(1, recorder.applied.size)
        assertTrue(
            "the preamp switch did not reach the chain",
            recorder.chain.value.isBypassed(ChainStage.PREAMP),
        )
        assertEquals(
            "a bypass must not reorder the chain",
            DspChainRules.DEFAULT_ORDER,
            recorder.chain.value.order,
        )
        assertEquals(
            "a bypass must not touch any other stage",
            setOf(ChainStage.PREAMP),
            recorder.chain.value.bypassed,
        )

        // A second, independent switch: one press changes one stage, not all of them.
        tapSwitch("Enable Compressor")
        assertEquals(
            setOf(ChainStage.PREAMP, ChainStage.COMPRESSOR),
            recorder.chain.value.bypassed,
        )

        // And it goes back the same way, leaving the other stage alone.
        tapSwitch("Enable Preamp")
        assertFalse(recorder.chain.value.isBypassed(ChainStage.PREAMP))
        assertEquals(setOf(ChainStage.COMPRESSOR), recorder.chain.value.bypassed)
    }

    @Test
    fun theReorderButtonsMoveOneStageAndKeepTheLimiterLast() {
        val recorder = render()

        tapButton("Move Preamp later")
        val order = recorder.chain.value.order
        assertEquals(ChainStage.PARAMETRIC_EQ, order[0])
        assertEquals(ChainStage.PREAMP, order[1])
        assertEquals("the chain still has all eight stages", 8, order.size)
        assertEquals(ChainStage.LIMITER, order.last())
        assertEquals(
            "the order the screen sent is the order the host holds",
            recorder.chain.value.order,
            recorder.applied.last().order,
        )

        tapButton("Move Preamp earlier")
        assertEquals(DspChainRules.DEFAULT_ORDER, recorder.chain.value.order)
    }

    @Test
    fun theLimiterHasNoSwitchAndCannotBeMovedFromTheEnd() {
        val recorder = render()

        assertEquals(
            "the limiter is switched off by the Limiter card, not by a row switch",
            0,
            compose.onAllNodesWithContentDescription("Enable Limiter").fetchSemanticsNodes().size,
        )

        tapButton("Move Limiter earlier")
        assertEquals(
            "a refused move must leave the chain exactly as it was",
            DspChainRules.DEFAULT_ORDER,
            recorder.chain.value.order,
        )
        assertEquals("nothing was sent to the host", 0, recorder.applied.size)
        assertEquals(DspChainRules.LIMITER_LAST_REASON, recorder.status.value)
    }

    /**
     * The Audio Console's own inline card switch — the second place the redesign
     * put a real enable/bypass control — reaches the same chain, and reaches it
     * per effect.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h3000dp-xxhdpi")
    fun theAudioConsoleCardSwitchBypassesThatEffectOnly() {
        val sent = mutableStateOf(DspChain.default())
        val applied = mutableListOf<DspChain>()
        val host = consoleHost { next ->
            applied.add(next)
            sent.value = next
            true
        }
        compose.setContent {
            SonicCoreTheme(glassLevel = 1) { AudioConsoleScreen(host = host) }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Enable the preamp stage").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()

        assertEquals(1, applied.size)
        assertTrue(
            "the console's preamp switch did not reach the chain",
            sent.value.isBypassed(ChainStage.PREAMP),
        )
        assertEquals(DspChainRules.DEFAULT_ORDER, sent.value.order)
        assertEquals(setOf(ChainStage.PREAMP), sent.value.bypassed)
    }

    @Test
    fun aChangeTheEngineRefusesIsReportedAndLeavesTheChainAlone() {
        val recorder = render()
        recorder.engineAccepts = false

        tapSwitch("Enable Preamp")
        assertEquals(1, recorder.applied.size)
        assertFalse(recorder.chain.value.isBypassed(ChainStage.PREAMP))
        val message = recorder.status.value
        assertNotNull("a refused change must say so", message)
        assertTrue(
            "the refusal has to name the stage it left alone, was: $message",
            message!!.contains("unchanged"),
        )
    }
}
