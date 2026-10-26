package com.soniccore.app.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the DSP chain editor's model.
 *
 * The chain editor claims two things, and both are pinned here rather than in a
 * screen: that a stage really can be switched off and on without losing its
 * settings, and that an order SonicCore must not run is refused *before*
 * anything reaches the engine. The native half of the claim — that a committed
 * order is the order the audio thread walks — is measured in
 * `tools/dspharness/harness.cpp` section [18], where moving the limiter off the
 * end demonstrably lets a signal past the ceiling.
 */
class DspChainTest {

    private val defaultOrder = DspChainRules.DEFAULT_ORDER

    @Test
    fun theDefaultOrderIsTheEnginesOwnSequence() {
        assertEquals(
            listOf(
                ChainStage.PREAMP,
                ChainStage.PARAMETRIC_EQ,
                ChainStage.GRAPHIC_EQ,
                ChainStage.SHELVES,
                ChainStage.STEREO,
                ChainStage.CONVOLVER,
                ChainStage.COMPRESSOR,
                ChainStage.LIMITER
            ),
            defaultOrder
        )
        // The ids are the native engine's own stage numbers.
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), defaultOrder.map { it.id })
        assertTrue(DspChain.default().isDefault)
        assertTrue(DspChain.default().isDefaultOrder)
        assertTrue(DspChain.default().bypassed.isEmpty())
    }

    @Test
    fun everyStageIdRoundTripsToItsStage() {
        ChainStage.entries.forEach { stage ->
            assertEquals(stage, ChainStage.byId(stage.id))
        }
        assertEquals(null, ChainStage.byId(-1))
        assertEquals(null, ChainStage.byId(8))
    }

    @Test
    fun theLimiterIsTheOnlyStageThatCannotBeBypassed() {
        assertEquals(ChainStage.LIMITER, ChainStage.entries.single { !it.bypassable })
        val chain = DspChain.default().withBypass(ChainStage.LIMITER, true)
        // A mandatory stage ignores the request instead of returning a chain the
        // engine would run without an output ceiling.
        assertFalse(chain.isBypassed(ChainStage.LIMITER))
        assertEquals(DspChain.default(), chain)
    }

    @Test
    fun bypassingAStageKeepsItsParametersAndItsPlaceInTheOrder() {
        val bypassed = DspChain.default().withBypass(ChainStage.COMPRESSOR, true)
        assertTrue(bypassed.isBypassed(ChainStage.COMPRESSOR))
        assertEquals(setOf(ChainStage.COMPRESSOR.id), bypassed.bypassedIds)
        // The order is untouched: bypass is the engine's stage enable, never a
        // removal from the chain.
        assertEquals(defaultOrder, bypassed.order)
        assertFalse(bypassed.isDefault)

        val restored = bypassed.withBypass(ChainStage.COMPRESSOR, false)
        assertEquals(DspChain.default(), restored)
        assertTrue(restored.bypassedIds.isEmpty())
    }

    @Test
    fun aStageCanBeMovedToAnotherPosition() {
        val moved = defaultOrder.toMutableList().apply {
            val preamp = removeAt(0)
            add(3, preamp)
        }
        assertTrue(DspChainRules.isValidOrder(moved))
        when (val result = DspChainRules.move(defaultOrder, 0, 3)) {
            is ChainMove.Applied -> assertEquals(moved, result.order)
            is ChainMove.Refused -> throw AssertionError("a legal move was refused: ${result.reason}")
        }
    }

    @Test
    fun movingTheLimiterAwayFromLastIsRefusedWithAReason() {
        val result = DspChainRules.move(defaultOrder, 7, 2)
        assertTrue(result is ChainMove.Refused)
        val reason = (result as ChainMove.Refused).reason
        assertEquals(DspChainRules.LIMITER_LAST_REASON, reason)
        // The refusal says who refuses and why, not just "no".
        assertTrue(reason.contains("limiter"))
        assertTrue(reason.contains("ceiling"))
    }

    @Test
    fun movingAnyStagePastTheLimiterIsRefused() {
        // Preamp to the very end would push the limiter off the end of the chain.
        val result = DspChainRules.move(defaultOrder, 0, 7)
        assertTrue(result is ChainMove.Refused)
    }

    @Test
    fun aMoveOutsideTheChainIsRefusedInsteadOfCrashing() {
        listOf(-1 to 0, 0 to -1, 8 to 0, 0 to 8).forEach { (from, to) ->
            val result = DspChainRules.move(defaultOrder, from, to)
            assertTrue("from=$from to=$to", result is ChainMove.Refused)
        }
    }

    @Test
    fun aMoveOntoItsOwnPositionIsAcceptedAndChangesNothing() {
        val result = DspChainRules.move(defaultOrder, 3, 3)
        when (result) {
            is ChainMove.Applied -> assertEquals(defaultOrder, result.order)
            is ChainMove.Refused -> throw AssertionError("a no-op move was refused")
        }
    }

    @Test
    fun movedRunsThroughTheChainSoAScreenCannotSkipTheRules() {
        val chain = DspChain.default()
        assertTrue(chain.moved(7, 0) is ChainMove.Refused)
        val applied = chain.moved(1, 2)
        assertTrue(applied is ChainMove.Applied)
        assertEquals(
            listOf(
                ChainStage.PREAMP,
                ChainStage.GRAPHIC_EQ,
                ChainStage.PARAMETRIC_EQ,
                ChainStage.SHELVES,
                ChainStage.STEREO,
                ChainStage.CONVOLVER,
                ChainStage.COMPRESSOR,
                ChainStage.LIMITER
            ),
            (applied as ChainMove.Applied).order
        )
    }

    @Test
    fun anInvalidOrderIsNotAValidChain() {
        // Missing a stage.
        assertFalse(DspChainRules.isValidOrder(defaultOrder.dropLast(2)))
        // A duplicate: seven distinct stages plus one repeat.
        assertFalse(
            DspChainRules.isValidOrder(
                defaultOrder.dropLast(1) + ChainStage.PREAMP
            )
        )
        // All eight, but the limiter is not last.
        assertFalse(
            DspChainRules.isValidOrder(
                listOf(
                    ChainStage.PREAMP,
                    ChainStage.PARAMETRIC_EQ,
                    ChainStage.GRAPHIC_EQ,
                    ChainStage.SHELVES,
                    ChainStage.STEREO,
                    ChainStage.CONVOLVER,
                    ChainStage.LIMITER,
                    ChainStage.COMPRESSOR
                )
            )
        )
        assertTrue(DspChainRules.isValidOrder(defaultOrder))
    }

    @Test
    fun storedIdsAreTreatedAsUntrustedInput() {
        // A damaged store falls back to the engine's own order instead of being
        // forwarded to the audio thread.
        assertEquals(DspChain.default(), DspChain.of(listOf(3, 3, 3), emptySet()))
        assertEquals(DspChain.default(), DspChain.of(listOf(7, 0, 1, 2, 3, 4, 5, 6), emptySet()))
        assertEquals(DspChain.default(), DspChain.of(emptyList(), emptySet()))
        assertEquals(DspChain.default(), DspChain.of(listOf(-4, 99), emptySet()))
        // A mandatory stage in the stored bypass set is dropped, not honoured.
        val bypassed = DspChain.of(defaultOrder.map { it.id }, setOf(ChainStage.LIMITER.id, 99))
        assertTrue(bypassed.bypassedIds.isEmpty())
        // A valid stored order is kept.
        val valid = DspChain.of(listOf(1, 0, 2, 3, 4, 5, 6, 7), setOf(0))
        assertEquals(ChainStage.PARAMETRIC_EQ, valid.order.first())
        assertEquals(setOf(0), valid.bypassedIds)
    }

    @Test
    fun fromParamsReadsThePersistedChain() {
        val params = DspParams()
        assertEquals(DspChain.default(), DspChain.fromParams(params))

        params.stageOrder = listOf(2, 1, 0, 3, 4, 5, 6, 7)
        params.stageBypassed = setOf(ChainStage.SHELVES.id, ChainStage.LIMITER.id)
        val chain = DspChain.fromParams(params)
        assertEquals(ChainStage.GRAPHIC_EQ, chain.order.first())
        assertEquals(setOf(ChainStage.SHELVES.id), chain.bypassedIds)
    }

    @Test
    fun theOrderTextNamesTheOrderTheEngineRuns() {
        val text = DspChain.default().orderText
        assertTrue(text.startsWith("Preamp"))
        assertTrue(text.endsWith("Limiter"))
        assertEquals(8, text.split(" → ").size)
        assertNotNull(text)
    }

    @Test
    fun resetReturnsTheEngineDefaultEvenAfterEdits() {
        val edited = DspChain.default()
            .withBypass(ChainStage.PREAMP, true)
            .withBypass(ChainStage.COMPRESSOR, true)
        assertFalse(edited.isDefault)
        assertEquals(DspChain.default(), edited.reset())
    }
}
