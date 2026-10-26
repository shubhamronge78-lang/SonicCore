package com.soniccore.app.dsp

/**
 * SONICCORE — THE DSP CHAIN MODEL
 *
 * The eight stages the native engine really processes, the order it really runs
 * them in, and the rules for what a valid chain is.
 *
 * This file is deliberately plain Kotlin with no Android imports: "is this
 * chain something SonicCore can apply?" is a decision, so it is a function
 * pinned by `DspChainTest` rather than a branch inside a screen.
 *
 * Two independent guards keep the visible order honest, which is what makes the
 * chain editor a real control instead of a picture:
 *
 *  - [DspChainRules.move] refuses an order the engine must not be asked to run
 *    **before** anything is applied, and returns the sentence the screen shows;
 *  - [NeonDsp.setStageOrder] re-validates the permutation at the audio boundary
 *    and returns `false` instead of accepting a bad chain. The native harness
 *    (`tools/dspharness`) measures that moving a stage really changes the
 *    processed audio, so a reorder is never a label that only a UI believes.
 *
 * The default order is the engine's historical hard-coded sequence, so a chain
 * nobody has touched behaves exactly as before this editor existed.
 */
enum class ChainStage(
    val id: Int,
    val title: String,
    val summary: String,
    /**
     * False for a stage that must always run. The limiter is the output
     * ceiling: a chain that could switch it off would let a stage after it
     * drive the signal past full scale with nothing guarding the output.
     */
    val bypassable: Boolean
) {
    PREAMP(0, "Preamp", "Output trim applied before the tone stages", true),
    PARAMETRIC_EQ(1, "Parametric EQ", "Up to eight peaking slots", true),
    GRAPHIC_EQ(2, "Graphic EQ", "The 10 / 15 / 31 band graphic equalizer", true),
    SHELVES(3, "Bass & Treble", "One low shelf and one high shelf", true),
    STEREO(4, "Stereo", "Width, balance, channel swap and mono downmix", true),
    CONVOLVER(5, "Convolver", "Optional impulse-response convolution", true),
    COMPRESSOR(6, "Compressor", "Stereo-linked gain-reduction compressor", true),
    LIMITER(7, "Limiter", "Final output ceiling — always on, always last", false);

    companion object {
        /** The stage a native id names, or null when the id is not one of ours. */
        fun byId(id: Int): ChainStage? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A complete chain: the order the stages run in, and which of them are bypassed.
 *
 * Immutable, so a screen hands a whole new chain back through the host exactly
 * the way the Audio control screens hand back an `AudioControlState`.
 */
data class DspChain(
    val order: List<ChainStage> = DspChainRules.DEFAULT_ORDER,
    val bypassed: Set<ChainStage> = emptySet()
) {
    /** The chain as the native engine takes it: the stage ids, in order. */
    val stageOrderIds: List<Int> get() = order.map { it.id }

    /** The stage ids the engine is told to switch off. */
    val bypassedIds: Set<Int> get() = bypassed.filter { it.bypassable }.map { it.id }.toSet()

    /** True when [stage] is switched off. A mandatory stage is never bypassed. */
    fun isBypassed(stage: ChainStage): Boolean = stage.bypassable && stage in bypassed

    /**
     * Switches [stage] on or off. Parameters are untouched either way — bypass
     * is the engine's own stage enable, not a reset of what the stage holds.
     */
    fun withBypass(stage: ChainStage, bypass: Boolean): DspChain = when {
        !stage.bypassable -> this
        bypass -> copy(bypassed = bypassed + stage)
        else -> copy(bypassed = bypassed - stage)
    }

    /** Moves the stage at [from] so it lands at [to]; refused orders change nothing. */
    fun moved(from: Int, to: Int): ChainMove = DspChainRules.move(order, from, to)

    /** The chain with the engine's own order and every stage enabled. */
    fun reset(): DspChain = default()

    val isDefaultOrder: Boolean get() = order == DspChainRules.DEFAULT_ORDER
    val isDefault: Boolean get() = isDefaultOrder && bypassed.isEmpty()

    /** e.g. "Preamp → Parametric EQ → … → Limiter" — the order audio really runs in. */
    val orderText: String get() = order.joinToString(" → ") { it.title }

    companion object {
        fun default(): DspChain = DspChain()

        /**
         * Rebuilds a chain from what was stored.
         *
         * Stored ids are untrusted input: an unknown id, a duplicate or an order
         * that would put a stage after the limiter all fall back to the engine's
         * default order rather than being forwarded to the audio thread.
         */
        fun of(orderIds: List<Int>, bypassedIds: Set<Int>): DspChain {
            val parsed = orderIds.mapNotNull { ChainStage.byId(it) }
            val safeOrder = if (DspChainRules.isValidOrder(parsed)) parsed else DspChainRules.DEFAULT_ORDER
            val safeBypass = bypassedIds
                .mapNotNull { ChainStage.byId(it) }
                .filter { it.bypassable }
                .toSet()
            return DspChain(safeOrder, safeBypass)
        }

        /** The chain the persisted parameter set describes. */
        fun fromParams(params: DspParams): DspChain =
            of(params.stageOrder, params.stageBypassed)
    }
}

/** The outcome of asking for a reorder. */
sealed interface ChainMove {
    data class Applied(val order: List<ChainStage>) : ChainMove
    data class Refused(val reason: String) : ChainMove
}

/**
 * The engine's own rules for a chain, as pure decisions.
 *
 * The native engine accepts any permutation of its eight stages, so an order
 * that is rejected here is rejected by SonicCore's safety contract, not by a
 * limitation of the DSP — and the refusal says so, because a user who is told
 * "no" deserves to know who is saying it and why.
 */
object DspChainRules {

    /** The engine's historical order, and the order a reset returns to. */
    val DEFAULT_ORDER: List<ChainStage> = listOf(
        ChainStage.PREAMP,
        ChainStage.PARAMETRIC_EQ,
        ChainStage.GRAPHIC_EQ,
        ChainStage.SHELVES,
        ChainStage.STEREO,
        ChainStage.CONVOLVER,
        ChainStage.COMPRESSOR,
        ChainStage.LIMITER
    )

    /** The stage SonicCore keeps at the end of every chain. */
    val MANDATORY_LAST: ChainStage = ChainStage.LIMITER

    /** Why the limiter is pinned, in the words the screens use. */
    const val LIMITER_LAST_REASON: String =
        "SonicCore keeps the limiter last: it is the output ceiling, and anything " +
            "after it would run without a guard on the output."

    /**
     * True when [order] names every stage exactly once and leaves the mandatory
     * stage last — the only shape the app will hand to the engine.
     */
    fun isValidOrder(order: List<ChainStage>): Boolean =
        order.size == ChainStage.entries.size &&
            order.toSet().size == order.size &&
            order.lastOrNull() == MANDATORY_LAST

    /**
     * Applies one move to [order].
     *
     * Never partially applies: a refused move returns the reason and the caller
     * keeps the chain it had, which is what makes a bad drop safe.
     */
    fun move(order: List<ChainStage>, from: Int, to: Int): ChainMove {
        if (from !in order.indices || to !in order.indices) {
            return ChainMove.Refused("That position is not part of the chain.")
        }
        if (from == to) return ChainMove.Applied(order)
        val next = order.toMutableList()
        val moved = next.removeAt(from)
        next.add(to, moved)
        if (next.lastOrNull() != MANDATORY_LAST) {
            return ChainMove.Refused(LIMITER_LAST_REASON)
        }
        return ChainMove.Applied(next)
    }
}
