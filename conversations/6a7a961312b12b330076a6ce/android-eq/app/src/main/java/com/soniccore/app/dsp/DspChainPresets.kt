package com.soniccore.app.dsp

/**
 * SONICCORE — SAVED DSP CHAINS
 *
 * A chain preset is a name plus the order and bypass flags of the eight stages.
 * It is stored as one short line of text, and every rule about it — the name
 * sanitising, the cap, what "save" means when the name already exists — is a
 * pure decision here, pinned by `DspChainPresetsTest`.
 *
 * Nothing in this file touches the engine. A preset is only heard once the
 * console hands it to the host and the host applies it through
 * [DspParams.applyTo], exactly like a manual edit.
 */
object DspChainPresets {

    /** How many chains a user can keep. Past this the oldest is dropped. */
    const val MAX_PRESETS = 8

    const val MAX_NAME_CHARS = 40

    /** What an unnamed chain is called instead of being refused. */
    const val DEFAULT_NAME = "Custom chain"

    private const val ORDER_KEY = "order"
    private const val OFF_KEY = "off"

    /** One stored chain. */
    data class Saved(val name: String, val chain: DspChain)

    /**
     * Treats a typed name as untrusted input: control characters, separators and
     * quotes are removed and the result is capped, so a name can never break the
     * line format or a list row. An empty result falls back to [DEFAULT_NAME]
     * rather than storing an unnamed preset.
     */
    fun sanitizeName(raw: String): String {
        val cleaned = raw
            // Separators the stored line uses, and control characters, become a
            // space rather than vanishing: "Studio;chain" and "Studio\nchain"
            // must not silently become one word.
            .map { ch ->
                if (ch.isISOControl() || ch == ';' || ch == '=' || ch == '|' || ch == ',') ' ' else ch
            }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_NAME_CHARS)
            .trim()
        return cleaned.ifEmpty { DEFAULT_NAME }
    }

    /** The stored form: the stage ids in order, plus the switched-off ones. */
    fun encode(chain: DspChain): String =
        "$ORDER_KEY=${chain.stageOrderIds.joinToString(",")};" +
            "$OFF_KEY=${chain.bypassedIds.sorted().joinToString(",")}"

    /**
     * Reads a stored chain. A damaged line yields null rather than a chain the
     * engine must not run; [DspChain.of] then re-validates whatever ids it holds.
     */
    fun decode(name: String, encoded: String): Saved? {
        val parts = encoded.split(';')
        var orderIds: List<Int>? = null
        var offIds: Set<Int> = emptySet()
        parts.forEach { part ->
            val kv = part.split('=', limit = 2)
            if (kv.size != 2) return@forEach
            val ids = kv[1].split(',').mapNotNull { it.trim().toIntOrNull() }
            when (kv[0].trim()) {
                ORDER_KEY -> if (ids.isNotEmpty()) orderIds = ids
                OFF_KEY -> offIds = ids.toSet()
            }
        }
        val order = orderIds ?: return null
        return Saved(sanitizeName(name), DspChain.of(order, offIds))
    }

    /**
     * Stores [chain] under [name]: an existing name is replaced where it is, a
     * new one goes to the front, and the list never exceeds [MAX_PRESETS].
     */
    fun save(existing: List<Saved>, name: String, chain: DspChain): List<Saved> {
        val safeName = sanitizeName(name)
        val kept = existing.map { it.copy(name = sanitizeName(it.name)) }
        val replaced = kept.map { if (it.name == safeName) Saved(safeName, chain) else it }
        val withNew = if (kept.any { it.name == safeName }) replaced else listOf(Saved(safeName, chain)) + kept
        return withNew.take(MAX_PRESETS)
    }

    /** Removes the preset with this name, if it is there. */
    fun remove(existing: List<Saved>, name: String): List<Saved> {
        val safeName = sanitizeName(name)
        return existing.filterNot { sanitizeName(it.name) == safeName }
    }
}
