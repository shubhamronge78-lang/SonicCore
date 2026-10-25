package com.soniccore.app.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * The only place in SonicCore that performs elevated Shizuku work.
 *
 * Scope is deliberately narrow: read-only identity and health probes through the
 * official client. No settings are written, no permissions are granted, no audio
 * route is touched. Every call is bounded by [ShizukuInvoker] and every failure is
 * mapped to a [ShizukuFailure], so a binder that dies mid-call or a user who
 * revokes authorization produces the same structured result as any other blocker.
 */
object ShizukuDiagnostics {

    const val OPERATION_ID = "ipc_diagnostics"

    fun run(
        environment: ShizukuEnvironment,
        timeoutMs: Long = ShizukuInvoker.DEFAULT_TIMEOUT_MS
    ): ShizukuResult {
        val operation = ShizukuOperations.OPERATIONS.first { it.id == OPERATION_ID }

        // Never touch the binder without a live, authorized connection: the client
        // would throw, and reporting a failed call as "ran" would be a lie.
        if (environment.state != ShizukuState.CONNECTED) {
            val status = ShizukuOperationEngine.evaluate(operation, environment)
            return ShizukuResult(
                operationId = operation.id,
                outcome = status.outcome,
                reason = status.reason
            )
        }

        return when (val call = ShizukuInvoker.call(timeoutMs) { probe() }) {
            is ShizukuCall.Failure -> ShizukuResult(
                operationId = operation.id,
                outcome = call.failure.outcome,
                reason = call.reason,
                failure = call.failure,
                elapsedMs = call.elapsedMs
            )

            is ShizukuCall.Success -> ShizukuResult(
                operationId = operation.id,
                outcome = ShizukuOutcome.AVAILABLE,
                reason = "Read-only diagnostic completed.",
                details = call.value,
                elapsedMs = call.elapsedMs
            )
        }
    }

    /**
     * Reads only cached server facts and one binder ping. Throws when the binder is
     * gone so the invoker maps it to BINDER_DISCONNECTED instead of returning
     * half-filled data that looks trustworthy.
     */
    private fun probe(): List<ShizukuDetail> {
        val details = mutableListOf<ShizukuDetail>()

        val pingStartedAt = System.nanoTime()
        val alive = Shizuku.pingBinder()
        val pingMs = (System.nanoTime() - pingStartedAt) / 1_000_000
        details += ShizukuDetail("Binder", if (alive) "Alive" else "Not responding")
        details += ShizukuDetail("Ping round-trip", "$pingMs ms")
        if (!alive) {
            throw IllegalStateException("Shizuku binder is no longer alive")
        }

        val preV11 = Shizuku.isPreV11()
        details += ShizukuDetail("Client protocol", if (preV11) "Pre-v11 (unsupported)" else "v11 or newer")
        if (preV11) {
            throw UnsupportedOperationException("Shizuku server predates the v11 API")
        }

        val uid = Shizuku.getUid()
        details += ShizukuDetail("Server uid", uid.toString())
        details += ShizukuDetail("Privilege", ShizukuPrivilege.fromUid(uid).label)

        val version = Shizuku.getVersion()
        details += ShizukuDetail("Server API version", version.toString())
        val patch = Shizuku.getServerPatchVersion()
        if (patch >= 0) {
            details += ShizukuDetail("Server patch", patch.toString())
        }
        val latest = runCatching { Shizuku.getLatestServiceVersion() }.getOrNull()
        if (latest != null && latest >= 0) {
            details += ShizukuDetail("Latest supported API", latest.toString())
        }

        val selinux = runCatching { Shizuku.getSELinuxContext() }.getOrNull()
        details += ShizukuDetail("SELinux context", selinux?.takeIf { it.isNotBlank() } ?: "Not reported")

        val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        details += ShizukuDetail("SonicCore authorization", if (granted) "Granted" else "Not granted")

        return details
    }
}
