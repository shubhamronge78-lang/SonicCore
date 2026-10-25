package com.soniccore.app.ui.navigation

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

    /**
     * Category of the screen on top — the tool's own category while a tool is
     * open, so the dock keeps the current workspace highlighted and a second
     * tool of the same category does not push a duplicate hub.
     */
    val category: SonicCoreCategory? get() =
        SonicCoreRoutes.categoryOf(current) ?: SonicCoreRoutes.toolOf(current)?.category

    val tool: SonicCoreTool? get() = SonicCoreRoutes.toolOf(current)

    /** True on the Home screen — the floor of the stack. */
    val isHome: Boolean get() = current == SonicCoreRoutes.HOME

    /** True when a category hub (not a tool) is on top of the stack. */
    val onCategoryHub: Boolean get() = category != null && tool == null

    /**
     * Opens a category hub from the dock. Tapping a category always lands on
     * that category's hub — anything above Home is dropped, so the dock never
     * leaves a tool buried under a later category tap and back from a hub
     * always returns to Home.
     */
    fun selectCategory(category: SonicCoreCategory): SonicCoreNavState =
        SonicCoreNavState(listOf(SonicCoreRoutes.HOME, SonicCoreRoutes.category(category)))

    /**
     * Opens a tool inside its own category. The category hub is pushed first
     * when it is not already on the stack, so back always walks tool → hub →
     * Home.
     */
    fun openTool(tool: SonicCoreTool): SonicCoreNavState {
        val withCategory =
            if (category == tool.category) this else push(SonicCoreRoutes.category(tool.category))
        return withCategory.push(SonicCoreRoutes.tool(tool))
    }

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
