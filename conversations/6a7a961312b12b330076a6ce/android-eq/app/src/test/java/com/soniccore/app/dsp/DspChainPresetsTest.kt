package com.soniccore.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the saved-chain store and of the sentences the dynamics cards show.
 *
 * Both halves exist to stop two specific kinds of dishonesty: a stored string
 * that rebuilds into a chain the engine must not run, and a meter that reports a
 * number when there was nothing to measure.
 */
class DspChainPresetsTest {

    // ── Encoding round-trips ──

    @Test
    fun aChainRoundTripsThroughItsStoredForm() {
        val chain = DspChain.default()
            .withBypass(ChainStage.PREAMP, true)
            .withBypass(ChainStage.CONVOLVER, true)
        val encoded = DspChainPresets.encode(chain)
        val decoded = DspChainPresets.decode("Studio", encoded)
        assertNotNull(decoded)
        assertEquals("Studio", decoded!!.name)
        assertEquals(chain.order, decoded.chain.order)
        assertEquals(chain.bypassedIds, decoded.chain.bypassedIds)
    }

    @Test
    fun aDamagedLineYieldsNothingInsteadOfABadChain() {
        assertNull(DspChainPresets.decode("Broken", ""))
        assertNull(DspChainPresets.decode("Broken", "off=0"))
        assertNull(DspChainPresets.decode("Broken", "order=;off=0"))
        assertNull(DspChainPresets.decode("Broken", "order=abc,def;off=0"))
    }

    @Test
    fun aStoredOrderThatIsNotAValidChainFallsBackToTheEngineDefault() {
        val decoded = DspChainPresets.decode("Odd", "order=7,0,1,2,3,4,5,6;off=")
        assertNotNull(decoded)
        assertEquals(DspChainRules.DEFAULT_ORDER, decoded!!.chain.order)
    }

    // ── Names are untrusted input ──

    @Test
    fun namesAreSanitizedBeforeTheyAreStored() {
        // The separators the line format uses cannot survive into a name.
        assertEquals("Studio chain", DspChainPresets.sanitizeName("Studio;chain"))
        assertEquals("A B", DspChainPresets.sanitizeName("A\nB"))
        assertEquals("A B", DspChainPresets.sanitizeName("A   B"))
        assertEquals("Custom chain", DspChainPresets.sanitizeName(""))
        assertEquals("Custom chain", DspChainPresets.sanitizeName("   "))
        assertEquals(
            DspChainPresets.MAX_NAME_CHARS,
            DspChainPresets.sanitizeName("x".repeat(200)).length
        )
        // Control characters never reach the store.
        assertFalse(DspChainPresets.sanitizeName("a\u0000b\u0007").contains('\u0000'))
    }

    @Test
    fun aSanitizedLongNameStillRoundTrips() {
        val saved = DspChainPresets.save(emptyList(), "Long".repeat(30), DspChain.default())
        val name = saved.single().name
        assertEquals(DspChainPresets.MAX_NAME_CHARS, name.length)
        val decoded = DspChainPresets.decode(name, DspChainPresets.encode(saved.single().chain))
        assertEquals(name, decoded!!.name)
    }

    // ── Saving, replacing and removing ──

    @Test
    fun savingTheSameNameReplacesItInPlaceInsteadOfDuplicating() {
        var presets = DspChainPresets.save(emptyList(), "First", DspChain.default())
        presets = DspChainPresets.save(presets, "Second", DspChain.default())
        val bypassed = DspChain.default().withBypass(ChainStage.PREAMP, true)
        presets = DspChainPresets.save(presets, "First", bypassed)

        assertEquals(2, presets.size)
        assertEquals(listOf("Second", "First"), presets.map { it.name })
        assertEquals(bypassed.bypassedIds, presets.first { it.name == "First" }.chain.bypassedIds)
    }

    @Test
    fun theStoreIsCappedAndDropsTheOldestEntry() {
        var presets = emptyList<DspChainPresets.Saved>()
        repeat(DspChainPresets.MAX_PRESETS + 3) { index ->
            presets = DspChainPresets.save(presets, "chain $index", DspChain.default())
        }
        assertEquals(DspChainPresets.MAX_PRESETS, presets.size)
        // Newest first: the last saved is at the top, the first ones are gone.
        assertEquals("chain 10", presets.first().name)
        assertFalse(presets.any { it.name == "chain 0" })
    }

    @Test
    fun removingAPresetLeavesTheRestAlone() {
        var presets = DspChainPresets.save(emptyList(), "A", DspChain.default())
        presets = DspChainPresets.save(presets, "B", DspChain.default())
        val after = DspChainPresets.remove(presets, "A")
        assertEquals(listOf("B"), after.map { it.name })
        assertEquals(after, DspChainPresets.remove(after, "missing"))
    }

    // ── The dynamics cards' wording ──

    @Test
    fun aMissingBridgeIsReportedAsNotMeasurableWithItsReason() {
        val text = DspTelemetryText.compressorLine(
            DspTelemetry.unavailable("libneondsp.so did not load")
        )
        assertTrue(text.startsWith("Not measurable"))
        assertTrue(text.contains("libneondsp.so did not load"))
        assertFalse(text.contains("0.0 dB"))
    }

    @Test
    fun aRunningBridgeWithNoFramesIsWaitingForAudioNotZeroReduction() {
        val telemetry = DspTelemetry(
            available = true,
            unavailableReason = null,
            processedFrames = 0L,
            clipping = false,
            compressorReductionDb = 0f,
            limiterReductionDb = 0f,
            limiterActive = false,
            outputPeakDbfs = Float.NEGATIVE_INFINITY
        )
        assertFalse(telemetry.hasProcessedAudio)
        assertEquals(DspTelemetryText.NO_FRAMES, DspTelemetryText.compressorLine(telemetry))
        assertEquals(DspTelemetryText.NO_FRAMES, DspTelemetryText.limiterLine(telemetry))
        assertEquals("Output peak: waiting for audio.", DspTelemetryText.outputPeakLine(telemetry))
    }

    @Test
    fun aRealMeasurementIsReportedAsANumber() {
        val telemetry = DspTelemetry(
            available = true,
            unavailableReason = null,
            processedFrames = 4096L,
            clipping = false,
            compressorReductionDb = 3.4f,
            limiterReductionDb = 1.2f,
            limiterActive = true,
            outputPeakDbfs = -1.0f
        )
        assertTrue(telemetry.hasProcessedAudio)
        assertEquals("Measured reduction 3.4 dB.", DspTelemetryText.compressorLine(telemetry))
        assertEquals("Active — measured reduction 1.2 dB.", DspTelemetryText.limiterLine(telemetry))
        assertEquals("Output peak -1.0 dBFS", DspTelemetryText.outputPeakLine(telemetry))
    }

    @Test
    fun aTransparentLimiterSaysZeroBecauseItMeasuredZero() {
        val telemetry = DspTelemetry(
            available = true,
            unavailableReason = null,
            processedFrames = 1024L,
            clipping = false,
            compressorReductionDb = 0f,
            limiterReductionDb = 0f,
            limiterActive = false,
            outputPeakDbfs = -6.0f
        )
        assertEquals("Transparent — 0.0 dB of reduction below the ceiling.", DspTelemetryText.limiterLine(telemetry))
        assertEquals("Measured reduction 0.0 dB — below threshold.", DspTelemetryText.compressorLine(telemetry))
    }

    @Test
    fun clippingIsNamedOnThePeakLineWhenItWasMeasured() {
        val telemetry = DspTelemetry(
            available = true,
            unavailableReason = null,
            processedFrames = 512L,
            clipping = true,
            compressorReductionDb = 0f,
            limiterReductionDb = 0f,
            limiterActive = false,
            outputPeakDbfs = 0.4f
        )
        assertTrue(DspTelemetryText.outputPeakLine(telemetry).endsWith("· clipping"))
    }

    @Test
    fun aNullTelemetryIsTreatedAsAnUnavailableBridge() {
        assertTrue(DspTelemetryText.compressorLine(null).startsWith("Not measurable"))
        assertTrue(DspTelemetryText.limiterLine(null).startsWith("Not measurable"))
        assertEquals("Output peak: not measured.", DspTelemetryText.outputPeakLine(null))
    }
}
