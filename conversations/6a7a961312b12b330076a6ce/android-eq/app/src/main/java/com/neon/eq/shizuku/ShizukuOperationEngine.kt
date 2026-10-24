package com.neon.eq.shizuku

/**
 * Pure eligibility engine for the Shizuku operation registry.
 *
 * Every operation is checked against, in order: the device API level, whether it
 * needs an elevated connection at all, the live connection state, and — once
 * connected — the minimum Shizuku server version. The first failing check wins, so
 * the reported blocker is always the real next step for the user.
 *
 * This object contains no Android types: [ShizukuBridge] builds the
 * [ShizukuEnvironment] from the real API and the JVM tests build it directly.
 */
object ShizukuOperationEngine {

    fun evaluateAll(
        environment: ShizukuEnvironment,
        operations: List<ShizukuOperation> = ShizukuOperations.OPERATIONS
    ): List<ShizukuOperationStatus> = operations.map { evaluate(it, environment) }

    fun evaluate(
        operation: ShizukuOperation,
        environment: ShizukuEnvironment
    ): ShizukuOperationStatus {
        val minDeviceSdk = maxOf(ShizukuOperations.MIN_API, operation.requiredApi)
        if (environment.deviceSdk < minDeviceSdk) {
            return status(operation, ShizukuOutcome.UNSUPPORTED, ShizukuBlocker.API_LEVEL, environment)
        }

        if (!operation.requiresConnection) {
            val outcome = if (operation.implemented) ShizukuOutcome.AVAILABLE else ShizukuOutcome.NOT_IMPLEMENTED
            return status(operation, outcome, null, environment)
        }

        return when (environment.state) {
            ShizukuState.UNSUPPORTED ->
                status(operation, ShizukuOutcome.UNSUPPORTED, ShizukuBlocker.DEVICE_UNSUPPORTED, environment)

            ShizukuState.NOT_INSTALLED ->
                status(operation, ShizukuOutcome.BLOCKED, ShizukuBlocker.NOT_INSTALLED, environment)

            ShizukuState.DISCONNECTED ->
                status(operation, ShizukuOutcome.BLOCKED, ShizukuBlocker.BINDER_DISCONNECTED, environment)

            ShizukuState.NOT_RUNNING ->
                status(operation, ShizukuOutcome.BLOCKED, ShizukuBlocker.SERVICE_STOPPED, environment)

            ShizukuState.PERMISSION_REQUIRED -> {
                val blocker = if (environment.permissionDeniedPermanently) {
                    ShizukuBlocker.PERMISSION_DENIED
                } else {
                    ShizukuBlocker.PERMISSION_REQUIRED
                }
                status(operation, ShizukuOutcome.BLOCKED, blocker, environment)
            }

            ShizukuState.CONNECTED -> {
                val serverTooOld = operation.minServerVersion > 0 &&
                    environment.serverVersion != ShizukuEnvironment.UNKNOWN_VALUE &&
                    environment.serverVersion < operation.minServerVersion
                if (serverTooOld) {
                    status(operation, ShizukuOutcome.UNSUPPORTED, ShizukuBlocker.SERVER_VERSION, environment)
                } else {
                    val outcome = if (operation.implemented) ShizukuOutcome.AVAILABLE else ShizukuOutcome.NOT_IMPLEMENTED
                    status(operation, outcome, null, environment)
                }
            }
        }
    }

    private fun status(
        operation: ShizukuOperation,
        outcome: ShizukuOutcome,
        blocker: ShizukuBlocker?,
        environment: ShizukuEnvironment
    ): ShizukuOperationStatus = ShizukuOperationStatus(
        operation = operation,
        outcome = outcome,
        blocker = blocker,
        availability = availabilityOf(outcome, blocker),
        reason = reasonFor(operation, outcome, blocker, environment)
    )

    /**
     * Legacy single-value view of the four-state outcome, kept for the existing
     * screens and their tests. It never adds information — it only collapses the
     * blocker into the wording the UI already used.
     */
    fun availabilityOf(outcome: ShizukuOutcome, blocker: ShizukuBlocker?): ShizukuAvailability =
        when (outcome) {
            ShizukuOutcome.AVAILABLE -> ShizukuAvailability.AVAILABLE
            ShizukuOutcome.UNSUPPORTED -> ShizukuAvailability.NOT_SUPPORTED
            ShizukuOutcome.NOT_IMPLEMENTED -> ShizukuAvailability.NOT_IMPLEMENTED
            ShizukuOutcome.BLOCKED -> when (blocker) {
                ShizukuBlocker.NOT_INSTALLED -> ShizukuAvailability.NOT_INSTALLED
                ShizukuBlocker.SERVICE_STOPPED,
                ShizukuBlocker.BINDER_DISCONNECTED -> ShizukuAvailability.REQUIRES_CONNECTION
                else -> ShizukuAvailability.REQUIRES_PERMISSION
            }
        }

    fun reasonFor(
        operation: ShizukuOperation,
        outcome: ShizukuOutcome,
        blocker: ShizukuBlocker?,
        environment: ShizukuEnvironment
    ): String = when (outcome) {
        ShizukuOutcome.AVAILABLE -> "Ready."
        ShizukuOutcome.NOT_IMPLEMENTED ->
            operation.note ?: "Execution is not wired up yet, so this operation cannot run."
        ShizukuOutcome.UNSUPPORTED -> when (blocker) {
            ShizukuBlocker.SERVER_VERSION ->
                "The Shizuku server is older than this operation requires. Update Shizuku Manager."
            ShizukuBlocker.DEVICE_UNSUPPORTED ->
                "Shizuku cannot run on this device or platform."
            else -> "This Android version or device does not provide the required API."
        }
        ShizukuOutcome.BLOCKED -> when (blocker) {
            ShizukuBlocker.NOT_INSTALLED ->
                "Install Shizuku Manager to enable this operation."
            ShizukuBlocker.SERVICE_STOPPED ->
                "Start the Shizuku service, then refresh."
            ShizukuBlocker.BINDER_DISCONNECTED ->
                "The Shizuku connection was lost. Start the service again, then refresh."
            ShizukuBlocker.PERMISSION_DENIED ->
                "Authorization was denied. Allow SonicCore inside Shizuku Manager to continue."
            ShizukuBlocker.PERMISSION_REQUIRED ->
                "Authorize SonicCore inside Shizuku Manager, then refresh."
            ShizukuBlocker.API_LEVEL ->
                "This Android version or device does not provide the required API."
            ShizukuBlocker.SERVER_VERSION ->
                "The Shizuku server is older than this operation requires. Update Shizuku Manager."
            ShizukuBlocker.DEVICE_UNSUPPORTED ->
                "Shizuku cannot run on this device or platform."
            null ->
                if (environment.state == ShizukuState.CONNECTED) {
                    "Not available right now."
                } else {
                    "Not available until Shizuku is connected."
                }
        }
    }
}
