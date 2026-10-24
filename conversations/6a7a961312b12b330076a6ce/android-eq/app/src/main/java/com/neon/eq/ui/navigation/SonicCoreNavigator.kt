package com.neon.eq.ui.navigation

/**
 * Navigation requests a screen is allowed to make.
 *
 * The screen never owns the back stack: it publishes a new [SonicCoreNavState]
 * through the host ([SonicCoreNavHost] in `MainActivity.kt`), which is the only
 * place that holds it. That keeps every tool screen a pure consumer and makes
 * the stack itself a plain value that survives process death.
 */
class SonicCoreNavigator(
    val state: SonicCoreNavState,
    private val onStateChange: (SonicCoreNavState) -> Unit
) {
    /** True on Home — the launcher's root screen. */
    val isHome: Boolean get() = state.isHome

    /** Category currently shown, or null on Home. */
    val category: SonicCoreCategory? get() = state.category

    /** Tool currently shown, or null on Home and on a category hub. */
    val tool: SonicCoreTool? get() = state.tool

    fun openCategory(category: SonicCoreCategory) = onStateChange(state.selectCategory(category))

    fun openTool(tool: SonicCoreTool) = onStateChange(state.openTool(tool))

    /**
     * Opens the tool that owns [destination]. Destinations without a tool are
     * ignored — a missing screen must never look like a successful navigation.
     */
    fun openDestination(destination: SonicCoreDestination): Boolean {
        val tool = SonicCoreToolRegistry.firstFor(destination) ?: return false
        openTool(tool)
        return true
    }

    fun back() = onStateChange(state.pop())

    fun home() = onStateChange(state.resetToHome())
}
