package com.neon.eq.shizuku

/**
 * Registry of the elevated operations SonicCore knows how to talk about.
 *
 * Detection is real (see [ShizukuBridge]); execution of privileged calls is NOT
 * implemented, because SonicCore deliberately does not bundle a third-party
 * Shizuku IPC client. Every operation therefore resolves to an explicit
 * availability state instead of pretending to run:
 *
 *  * a version/device gate          -> NOT_SUPPORTED
 *  * Shizuku missing                -> NOT_INSTALLED
 *  * Shizuku installed but stopped   -> REQUIRES_CONNECTION
 *  * running but unauthorized        -> REQUIRES_PERMISSION
 *  * authorized but not wired up     -> NOT_IMPLEMENTED
 *
 * Only operations that genuinely work (state probe, opening Shizuku Manager)
 * ever report AVAILABLE.
 */
enum class ShizukuGroup(val label: String) {
    CONNECTION("Connection"),
    AUDIO("Audio"),
    SYSTEM("System"),
    DIAGNOSTICS("Diagnostics")
}

enum class ShizukuAvailability(val label: String, val isRunnable: Boolean) {
    AVAILABLE("Available", true),
    REQUIRES_CONNECTION("Needs Shizuku running", false),
    REQUIRES_PERMISSION("Needs authorization", false),
    NOT_INSTALLED("Shizuku not installed", false),
    NOT_SUPPORTED("Not supported on this device", false),
    NOT_IMPLEMENTED("Not implemented yet", false)
}

data class ShizukuOperation(
    val id: String,
    val title: String,
    val description: String,
    val group: ShizukuGroup,
    val requiredApi: Int = 0,
    val requiresConnection: Boolean = true,
    val implemented: Boolean = false,
    val note: String? = null
)

data class ShizukuOperationStatus(
    val operation: ShizukuOperation,
    val availability: ShizukuAvailability,
    val reason: String
)

data class ShizukuSetupStep(
    val index: Int,
    val title: String,
    val detail: String,
    val done: Boolean,
    val blocked: Boolean = false
)

object ShizukuOperations {

    const val MIN_API = 26

    val OPERATIONS: List<ShizukuOperation> = listOf(
        ShizukuOperation(
            id = "connection_state",
            title = "Connection & permission state",
            description = "Reads whether Shizuku is installed, running, and authorized for SonicCore.",
            group = ShizukuGroup.CONNECTION,
            requiresConnection = false,
            implemented = true
        ),
        ShizukuOperation(
            id = "open_manager",
            title = "Open Shizuku Manager",
            description = "Launches the Shizuku Manager app so setup or authorization can continue there.",
            group = ShizukuGroup.CONNECTION,
            requiresConnection = false,
            implemented = true
        ),
        ShizukuOperation(
            id = "audio_policy_read",
            title = "Read restricted audio policy",
            description = "Query hidden audio policy values that the public AudioManager API does not expose.",
            group = ShizukuGroup.AUDIO,
            note = "Requires a Shizuku IPC client. SonicCore does not bundle one, so this stays unavailable even when Shizuku is connected."
        ),
        ShizukuOperation(
            id = "audio_route_force",
            title = "Force output route",
            description = "Override the active output device independently of the system's own routing decision.",
            group = ShizukuGroup.AUDIO,
            note = "Requires a Shizuku IPC client and an OEM-safe route table; not wired up."
        ),
        ShizukuOperation(
            id = "secure_settings_read",
            title = "Read secure settings",
            description = "Read Settings.Secure keys that are hidden from third-party apps.",
            group = ShizukuGroup.SYSTEM,
            note = "Requires a Shizuku IPC client. Not implemented."
        ),
        ShizukuOperation(
            id = "permission_grant",
            title = "Grant app permissions",
            description = "Grant runtime permissions to other packages without user taps.",
            group = ShizukuGroup.SYSTEM,
            note = "Deliberately not implemented: silently granting permissions to other apps is a high-risk capability."
        ),
        ShizukuOperation(
            id = "binder_diagnostics",
            title = "Binder service diagnostics",
            description = "Enumerate system binder services for troubleshooting audio routing problems.",
            group = ShizukuGroup.DIAGNOSTICS,
            note = "Requires a Shizuku IPC client. Not implemented."
        )
    )

    fun evaluate(state: ShizukuState, apiLevel: Int): List<ShizukuOperationStatus> =
        OPERATIONS.map { operation -> operation to availabilityFor(operation, state, apiLevel) }
            .map { (operation, availability) ->
                ShizukuOperationStatus(
                    operation = operation,
                    availability = availability,
                    reason = reasonFor(operation, availability)
                )
            }

    fun availabilityFor(
        operation: ShizukuOperation,
        state: ShizukuState,
        apiLevel: Int
    ): ShizukuAvailability {
        if (apiLevel < maxOf(MIN_API, operation.requiredApi)) {
            return ShizukuAvailability.NOT_SUPPORTED
        }
        if (!operation.requiresConnection) {
            return if (operation.implemented) ShizukuAvailability.AVAILABLE
            else ShizukuAvailability.NOT_IMPLEMENTED
        }
        // Connection-gated operations report the first blocking step, so the UI
        // always names the real reason rather than a blanket "unavailable".
        return when (state) {
            ShizukuState.UNSUPPORTED -> ShizukuAvailability.NOT_SUPPORTED
            ShizukuState.NOT_INSTALLED -> ShizukuAvailability.NOT_INSTALLED
            ShizukuState.NOT_RUNNING -> ShizukuAvailability.REQUIRES_CONNECTION
            ShizukuState.PERMISSION_REQUIRED -> ShizukuAvailability.REQUIRES_PERMISSION
            ShizukuState.CONNECTED ->
                if (operation.implemented) ShizukuAvailability.AVAILABLE
                else ShizukuAvailability.NOT_IMPLEMENTED
        }
    }

    private fun reasonFor(
        operation: ShizukuOperation,
        availability: ShizukuAvailability
    ): String = when (availability) {
        ShizukuAvailability.AVAILABLE -> "Ready."
        ShizukuAvailability.NOT_INSTALLED -> "Install Shizuku Manager to enable this operation."
        ShizukuAvailability.REQUIRES_CONNECTION -> "Start the Shizuku service, then refresh."
        ShizukuAvailability.REQUIRES_PERMISSION -> "Authorize SonicCore inside Shizuku Manager, then refresh."
        ShizukuAvailability.NOT_SUPPORTED -> "This Android version or device does not provide the required API."
        ShizukuAvailability.NOT_IMPLEMENTED ->
            operation.note ?: "Execution is not wired up yet, so this operation cannot run."
    }

    fun setupSteps(state: ShizukuState): List<ShizukuSetupStep> {
        val installed = state != ShizukuState.NOT_INSTALLED && state != ShizukuState.UNSUPPORTED
        // PERMISSION_REQUIRED already means "installed and the service is running".
        val running = state == ShizukuState.CONNECTED || state == ShizukuState.PERMISSION_REQUIRED
        val authorized = state == ShizukuState.CONNECTED
        return listOf(
            ShizukuSetupStep(
                index = 1,
                title = "Install Shizuku Manager",
                detail = "Shizuku ships from its own project releases and from app stores; SonicCore cannot install it for you.",
                done = installed,
                blocked = state == ShizukuState.UNSUPPORTED
            ),
            ShizukuSetupStep(
                index = 2,
                title = "Start the Shizuku service",
                detail = "Start it over wireless debugging or USB (adb) each time the device reboots. Android grants it no permanent start.",
                done = running,
                blocked = !installed
            ),
            ShizukuSetupStep(
                index = 3,
                title = "Authorize SonicCore",
                detail = "Approve the SonicCore permission request inside Shizuku Manager. This can be revoked at any time.",
                done = authorized,
                blocked = !running
            ),
            ShizukuSetupStep(
                index = 4,
                title = "Refresh status here",
                detail = "SonicCore re-checks install, service and permission state; nothing is assumed.",
                done = authorized,
                blocked = !running
            )
        )
    }
}
