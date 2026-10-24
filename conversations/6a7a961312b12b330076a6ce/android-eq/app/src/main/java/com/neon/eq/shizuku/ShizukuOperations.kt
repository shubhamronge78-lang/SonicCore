package com.neon.eq.shizuku

/**
 * Registry of the elevated operations SonicCore knows how to talk about.
 *
 * Every entry states who can perform it ([ShizukuAccess]) and whether SonicCore
 * actually implements it ([ShizukuOperation.implemented]). Eligibility is decided
 * by [ShizukuOperationEngine] from a live [ShizukuEnvironment]; execution of the
 * read-only diagnostics lives in [ShizukuDiagnostics].
 *
 * SonicCore deliberately implements only read-only diagnostics. Operations that
 * would change secure settings, grant permissions or force audio routes are
 * listed so they can be reported honestly as NOT_IMPLEMENTED, and are never
 * attempted silently.
 */
enum class ShizukuGroup(val label: String) {
    CONNECTION("Connection"),
    AUDIO("Audio"),
    SYSTEM("System"),
    DIAGNOSTICS("Diagnostics")
}

/**
 * Legacy single-value availability, derived from [ShizukuOutcome] + [ShizukuBlocker].
 * New code should read [ShizukuOperationStatus.outcome] instead.
 */
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
    val access: ShizukuAccess = ShizukuAccess.SHIZUKU,
    val readOnly: Boolean = true,
    /** Minimum Shizuku server API when known; 0 means "no server-version gate". */
    val minServerVersion: Int = 0,
    val note: String? = null
)

data class ShizukuOperationStatus(
    val operation: ShizukuOperation,
    val outcome: ShizukuOutcome,
    val blocker: ShizukuBlocker?,
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
            implemented = true,
            access = ShizukuAccess.NONE
        ),
        ShizukuOperation(
            id = "open_manager",
            title = "Open Shizuku Manager",
            description = "Launches the Shizuku Manager app so setup or authorization can continue there.",
            group = ShizukuGroup.CONNECTION,
            requiresConnection = false,
            implemented = true,
            access = ShizukuAccess.NONE
        ),
        ShizukuOperation(
            id = "ipc_diagnostics",
            title = "Shizuku IPC diagnostics",
            description = "Read-only binder round-trip: server version, privilege level, SELinux context and latency.",
            group = ShizukuGroup.DIAGNOSTICS,
            implemented = true,
            access = ShizukuAccess.SHIZUKU,
            note = "Read-only. Uses the official Shizuku client only; nothing on the device is changed."
        ),
        ShizukuOperation(
            id = "audio_policy_read",
            title = "Read restricted audio policy",
            description = "Query hidden audio policy values that the public AudioManager API does not expose.",
            group = ShizukuGroup.AUDIO,
            access = ShizukuAccess.SHIZUKU,
            note = "Needs the hidden IAudioService AIDL stubs plus a hidden-API bypass; SonicCore ships neither."
        ),
        ShizukuOperation(
            id = "audio_route_force",
            title = "Force output route",
            description = "Override the active output device independently of the system's own routing decision.",
            group = ShizukuGroup.AUDIO,
            access = ShizukuAccess.IMPOSSIBLE,
            note = "Changing the global output route needs a platform-signed permission; Shizuku's shell identity cannot do it."
        ),
        ShizukuOperation(
            id = "secure_settings_read",
            title = "Read secure settings",
            description = "Read Settings.Secure keys that are hidden from third-party apps.",
            group = ShizukuGroup.SYSTEM,
            access = ShizukuAccess.SHIZUKU,
            note = "Requires a Shizuku user service (or hidden-API access) to read as shell; not bundled."
        ),
        ShizukuOperation(
            id = "secure_settings_write",
            title = "Change secure settings",
            description = "Modify system settings that are otherwise locked.",
            group = ShizukuGroup.SYSTEM,
            access = ShizukuAccess.SHIZUKU,
            readOnly = false,
            note = "Deliberately not implemented: SonicCore never changes secure settings or system policy."
        ),
        ShizukuOperation(
            id = "permission_grant",
            title = "Grant app permissions",
            description = "Grant runtime permissions to other packages without user taps.",
            group = ShizukuGroup.SYSTEM,
            access = ShizukuAccess.ADB,
            readOnly = false,
            note = "Deliberately not implemented: silently granting permissions to other apps is a high-risk capability."
        ),
        ShizukuOperation(
            id = "kernel_audio_debugfs",
            title = "Read kernel audio debug nodes",
            description = "Inspect ALSA / vendor audio debug nodes that are unreadable without uid 0.",
            group = ShizukuGroup.AUDIO,
            access = ShizukuAccess.ROOT,
            note = "Requires root (uid 0) and a Shizuku user service to read outside the app sandbox; not bundled."
        ),
        ShizukuOperation(
            id = "binder_diagnostics",
            title = "Binder service enumeration",
            description = "List every system binder service to troubleshoot audio routing problems.",
            group = ShizukuGroup.DIAGNOSTICS,
            access = ShizukuAccess.SHIZUKU,
            note = "Needs a Shizuku user service to enumerate the service manager; SonicCore does not bundle one."
        )
    )

    /** Registry with the live connection state applied. */
    fun evaluate(environment: ShizukuEnvironment): List<ShizukuOperationStatus> =
        ShizukuOperationEngine.evaluateAll(environment)

    /**
     * State + device API convenience overload. Kept for the existing screens and
     * tests; it builds a synthetic environment with no server details known.
     */
    fun evaluate(state: ShizukuState, apiLevel: Int): List<ShizukuOperationStatus> =
        evaluate(ShizukuEnvironment.of(state, apiLevel))

    fun availabilityFor(
        operation: ShizukuOperation,
        state: ShizukuState,
        apiLevel: Int
    ): ShizukuAvailability = ShizukuOperationEngine
        .evaluate(operation, ShizukuEnvironment.of(state, apiLevel))
        .availability

    fun setupSteps(state: ShizukuState): List<ShizukuSetupStep> {
        val installed = state != ShizukuState.NOT_INSTALLED && state != ShizukuState.UNSUPPORTED
        // PERMISSION_REQUIRED already means "installed and the service is running".
        // DISCONNECTED means the service was running but the binder is gone, so it
        // must be started again.
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
