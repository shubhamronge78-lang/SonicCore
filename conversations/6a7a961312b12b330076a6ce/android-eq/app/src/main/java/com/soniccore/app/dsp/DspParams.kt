package com.soniccore.app.dsp

import com.soniccore.app.engine.EqualizerEngine
import org.json.JSONArray
import org.json.JSONObject

/** One parametric EQ slot (native DSP supports 8). */
data class PeqSlot(var on: Boolean = false, var freq: Float = 1000f, var gain: Float = 0f, var q: Float = 1.0f)

/**
 * Build #117: the full DSP-chain parameter set — preamp, parametric slots,
 * bass/treble shelves, stereo width/balance/swap/mono, compressor, limiter.
 * Persisted as JSON (dsp_params_json) and applied to the native engine both
 * live (UI edits) and at capture start. Validated by the C++ layer too.
 */
class DspParams {
    var preamp = 0f
    var bass = 0f
    var treble = 0f
    var width = 1f
    var balance = 0f
    var mono = false
    var swap = false
    var compOn = false
    var compThresh = -18f
    var limiterOn = true
    var limThresh = -1f
    var slots = List(8) { PeqSlot() }

    /**
     * The order the eight native stages run in, as stage ids (see [DspChain]).
     *
     * This is not a display preference: `applyTo` hands it to
     * [NeonDsp.setStageOrder], which is what the audio thread walks.
     */
    var stageOrder: List<Int> = DspChainRules.DEFAULT_ORDER.map { it.id }

    /**
     * Stage ids the chain editor switched off. A stage's own parameters are
     * never cleared by a bypass, and a mandatory stage (the limiter) is never
     * in here — [DspChain] enforces both.
     */
    var stageBypassed: Set<Int> = emptySet()

    /**
     * Pushes every parameter to the native engine.
     *
     * @return true when every setter completed. A missing native library or a
     *   failing setter reports false instead of being absorbed silently, so a
     *   caller that must not claim a write it could not make can tell the truth.
     */
    fun applyTo(dsp: NeonDsp): Boolean =
        try {
            dsp.setPreamp(preamp)
            dsp.setShelves(bass, treble)
            dsp.setStereo(width, balance, swap, mono)
            dsp.setCompressor(compOn, compThresh, 4f, 5f, 150f)
            dsp.setLimiter(limiterOn, limThresh)
            slots.forEachIndexed { i, s -> dsp.setParametric(i, s.on, s.freq, s.gain, s.q) }
            // The chain editor's two controls. The engine re-validates the order
            // and answers whether it took it, so the outcome is part of this
            // method's own answer instead of being swallowed: a caller that must
            // not claim a write it could not make can see that it failed.
            var chainAccepted = dsp.setStageOrder(stageOrder.toIntArray())
            ChainStage.entries.forEach { stage ->
                val enabled = !(stage.bypassable && stageBypassed.contains(stage.id))
                chainAccepted = dsp.setStageEnabled(stage.id, enabled) && chainAccepted
            }
            chainAccepted
        } catch (_: Throwable) { false }

    /** @return true when the parameter set was persisted; see [applyTo]. */
    fun save(engine: EqualizerEngine): Boolean {
        return try {
            val o = JSONObject()
            o.put("preamp", preamp.toDouble()); o.put("bass", bass.toDouble()); o.put("treble", treble.toDouble())
            o.put("width", width.toDouble()); o.put("balance", balance.toDouble())
            o.put("mono", mono); o.put("swap", swap)
            o.put("compOn", compOn); o.put("compThresh", compThresh.toDouble())
            o.put("limiterOn", limiterOn); o.put("limThresh", limThresh.toDouble())
            o.put("stageOrder", JSONArray(stageOrder))
            o.put("stageBypassed", JSONArray(stageBypassed.sorted()))
            val arr = JSONArray()
            slots.forEach { s ->
                val p = JSONObject()
                p.put("on", s.on); p.put("f", s.freq.toDouble()); p.put("g", s.gain.toDouble()); p.put("q", s.q.toDouble())
                arr.put(p)
            }
            o.put("peq", arr)
            engine.setDspParamsJson(o.toString())
            true
        } catch (_: Throwable) { false }
    }

    companion object {
        fun load(engine: EqualizerEngine): DspParams {
            val p = DspParams()
            try {
                val o = JSONObject(engine.getDspParamsJson())
                p.preamp = o.optDouble("preamp", 0.0).toFloat().coerceIn(-30f, 30f)
                p.bass = o.optDouble("bass", 0.0).toFloat().coerceIn(-30f, 30f)
                p.treble = o.optDouble("treble", 0.0).toFloat().coerceIn(-30f, 30f)
                p.width = o.optDouble("width", 1.0).toFloat().coerceIn(0f, 4f)
                p.balance = o.optDouble("balance", 0.0).toFloat().coerceIn(-1f, 1f)
                p.mono = o.optBoolean("mono", false)
                p.swap = o.optBoolean("swap", false)
                p.compOn = o.optBoolean("compOn", false)
                p.compThresh = o.optDouble("compThresh", -18.0).toFloat().coerceIn(-60f, 0f)
                p.limiterOn = o.optBoolean("limiterOn", true)
                p.limThresh = o.optDouble("limThresh", -1.0).toFloat().coerceIn(-60f, 0f)
                // Stored chain ids are untrusted input: DspChain.of falls back to
                // the engine's default order when they are not a valid chain, so a
                // damaged store can never hand the audio thread a bad order.
                val orderArr = o.optJSONArray("stageOrder")
                if (orderArr != null) {
                    val ids = ArrayList<Int>(orderArr.length())
                    for (i in 0 until orderArr.length()) ids.add(orderArr.optInt(i, -1))
                    p.stageOrder = DspChain.of(ids, emptySet()).stageOrderIds
                }
                val offArr = o.optJSONArray("stageBypassed")
                if (offArr != null) {
                    val off = LinkedHashSet<Int>()
                    for (i in 0 until offArr.length()) off.add(offArr.optInt(i, -1))
                    p.stageBypassed = DspChain.of(p.stageOrder, off).bypassedIds
                }
                val arr = o.optJSONArray("peq")
                if (arr != null) {
                    val list = ArrayList<PeqSlot>()
                    for (i in 0 until 8) {
                        val s = arr.optJSONObject(i) ?: break
                        list.add(PeqSlot(
                            s.optBoolean("on", false),
                            s.optDouble("f", 1000.0).toFloat().coerceIn(10f, 20000f),
                            s.optDouble("g", 0.0).toFloat().coerceIn(-30f, 30f),
                            s.optDouble("q", 1.0).toFloat().coerceIn(0.1f, 10f)))
                    }
                    while (list.size < 8) list.add(PeqSlot())
                    p.slots = list
                }
            } catch (_: Throwable) { }
            return p
        }
    }
}
