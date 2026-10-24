package com.neon.eq.ui.navigation

import androidx.compose.runtime.saveable.Saver

/**
 * Back stack for the SonicCore category navigation, kept as an immutable value
 * so it is both trivially unit-testable and `rememberSaveable`-friendly
 * (a single encoded String survives process death).
 *
 * Tokens are `home`, `cat:<CATEGORY>` and `tool:<tool.id>`. The stack always
 * starts at Home and never grows past [MAX_DEPTH] entries.
 */
data class SonicCoreNavState(val tokens: List<String> = listOf(SonicCoreRoutes.HOME)) {

    val current: String get() = tokens.last()

    val depth: Int get() = tokens.size

    val category: SonicCoreCategory? get() = SonicCoreRoutes.categoryOf(current)

    val tool: SonicCoreTool? get() = SonicCoreRoutes.toolOf(current)

    /** True when a category hub (not a tool) is on top of the stack. */
    val onCategoryHub: Boolean get() = category != null && tool == null

    fun push(token: String): SonicCoreNavState {
        if (token.isBlank() || token == current) return this
        val next = tokens + token
        return copy(tokens = if (next.size > MAX_DEPTH) next.takeLast(MAX_DEPTH) else next)
    }

    /** Pops one level. Home is the floor — it never pops away. */
    fun pop(): SonicCoreNavState =
        if (tokens.size <= 1) this else copy(tokens = tokens.dropLast(1))

    fun resetToHome(): SonicCoreNavState = SonicCoreNavState()

    fun encode(): String = tokens.joinToString("|")

    companion object {
        const val MAX_DEPTH = 12

        /**
         * `rememberSaveable` saver for the stack: one encoded String, so the
         * route survives configuration changes and process death.
         */
        val Saver: Saver<SonicCoreNavState, String> = Saver(
            save = { it.encode() },
            restore = { decode(it) }
        )

        fun decode(encoded: String?): SonicCoreNavState {
            val tokens = encoded
                ?.split("|")
                ?.filter { it.isNotBlank() && SonicCoreRoutes.isKnown(it) }
                .orEmpty()
            return if (tokens.isEmpty()) SonicCoreNavState() else SonicCoreNavState(tokens)
        }
    }
}

/** Route token vocabulary shared by the navigation state and the UI. */
object SonicCoreRoutes {

    const val HOME = "home"

    fun category(category: SonicCoreCategory): String = "cat:${category.name}"

    fun tool(tool: SonicCoreTool): String = "tool:${tool.id}"

    fun categoryOf(token: String): SonicCoreCategory? {
        if (!token.startsWith("cat:")) return null
        val name = token.removePrefix("cat:")
        return SonicCoreCategory.entries.firstOrNull { it.name == name }
    }

    fun toolOf(token: String): SonicCoreTool? {
        if (!token.startsWith("tool:")) return null
        return SonicCoreToolRegistry.find(token.removePrefix("tool:"))
    }

    fun isKnown(token: String): Boolean =
        token == HOME || categoryOf(token) != null || toolOf(token) != null
}
