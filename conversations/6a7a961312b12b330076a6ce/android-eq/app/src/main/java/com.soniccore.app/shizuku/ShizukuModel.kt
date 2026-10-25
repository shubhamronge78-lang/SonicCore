package com.soniccore.app.shizuku

/**
 * Structured result of a Shizuku operation.
 *
 * Exactly four states are ever reported, and only [AVAILABLE] is runnable:
 *
 *  * [AVAILABLE]       — prerequisites are met and, for executable operations, a
 *                        real implementation exists.
 *  * [BLOCKED]         — a prerequisite is missing right now (Shizuku absent,
 *                        service stopped, connection lost, not authorized).
 *  * [UNSUPPORTED]     — this Android version, Shizuku server or device does not
 *                        provide what the operation needs. Not fixable by the user.
 *  * [NOT_IMPLEMENTED] — SonicCore has no implementation for it (yet, or ever).
 *                        A stub is never reported as a successful operation.
 */
enum class ShizukuOutcome(val label: String, val isRunnable: Boolean) {
    AVAILABLE("Available", true),
    BLOCKED("Blocked", false),
    UNSUPPORTED("Unsupported", false),
    NOT_IMPLEMENTED("Not implemented", false)
}

/**
 * The first prerequisite that stops an operation. Ordered by the setup sequence,
 * so the UI can always name the real next step instead of a blanket "unavailable".
 */
enum class ShizukuBlocker(val label: String) {
    NOT_INSTALLED("Shizuku not installed"),
    SERVICE_STOPPED("Shizuku service stopped"),
    BINDER_DISCONNECTED("Shizuku connection lost"),
    PERMISSION_REQUIRED("Authorization required"),
    PERMISSION_DENIED("Authorization denied"),
    API_LEVEL("Android API too low"),
    SERVER_VERSION("Shizuku server too old"),
    DEVICE_UNSUPPORTED("Device does not support this")
}

/**
 * Who can perform an operation on a stock, non-rooted device. This is part of the
 * operation registry so every entry documents what it actually needs.
 */
enum class ShizukuAccess(val label: String, val detail: String) {
    NONE("Local", "Runs inside SonicCore; no elevated identity involved."),
    SHIZUKU("Shizuku", "Needs the Shizuku service running and SonicCore authorized."),
    ADB("ADB / shell", "Needs shell (uid 2000) privileges — Shizuku started over adb, or Sui."),
    ROOT("Root", "Needs uid 0 — Shizuku started as root, or Sui on Magisk."),
    IMPOSSIBLE("Not possible", "No third-party app can do this on a stock device.")
}

/** Identity the Shizuku server is running with. */
enum class ShizukuPrivilege(val label: String) {
    SHELL("ADB / shell (uid 2000)"),
    ROOT("Root (uid 0)"),
    OTHER("Other uid"),
    UNKNOWN("Unknown");

    companion object {
        fun fromUid(uid: Int): ShizukuPrivilege = when (uid) {
            ShizukuEnvironment.UNKNOWN_VALUE -> UNKNOWN
            0 -> ROOT
            2000 -> SHELL
            else -> OTHER
        }
    }
}

/**
 * Everything the pure eligibility logic needs to know about the current Shizuku
 * situation. Built by [ShizukuBridge] from the real API; unit tests construct it
 * directly, which is why this file contains no Android types.
 */
data class ShizukuEnvironment(
    val state: ShizukuState,
    val deviceSdk: Int,
    val serverVersion: Int = UNKNOWN_VALUE,
    val serverPatchVersion: Int = UNKNOWN_VALUE,
    val serverUid: Int = UNKNOWN_VALUE,
    val selinuxContext: String? = null,
    val permissionGranted: Boolean = false,
    val permissionDeniedPermanently: Boolean = false,
    val preV11: Boolean = false,
    val binderAlive: Boolean = false
) {
    val privilege: ShizukuPrivilege get() = ShizukuPrivilege.fromUid(serverUid)
    val isElevated: Boolean get() = privilege == ShizukuPrivilege.SHELL || privilege == ShizukuPrivilege.ROOT

    companion object {
        const val UNKNOWN_VALUE = -1

        /** Minimal environment for a state + device API, used by legacy call sites and tests. */
        fun of(state: ShizukuState, deviceSdk: Int): ShizukuEnvironment {
            val connected = state == ShizukuState.CONNECTED
            return ShizukuEnvironment(
                state = state,
                deviceSdk = deviceSdk,
                permissionGranted = connected,
                binderAlive = connected
            )
        }
    }
}

/** One labelled value produced by a read-only diagnostic. */
data class ShizukuDetail(val label: String, val value: String)

/**
 * Result of actually executing an operation. [outcome] follows the same four
 * states as eligibility; [failure] carries the classified error when the call
 * could not complete.
 */
data class ShizukuResult(
    val operationId: String,
    val outcome: ShizukuOutcome,
    val reason: String,
    val failure: ShizukuFailure? = null,
    val details: List<ShizukuDetail> = emptyList(),
    val elapsedMs: Long = 0L
) {
    val succeeded: Boolean get() = outcome == ShizukuOutcome.AVAILABLE
}

/**
 * Classified failures. Mapping is done from the exception's class name so the
 * same table is exercised by JVM tests without an Android runtime.
 */
enum class ShizukuFailure(val code: String, val reason: String, val outcome: ShizukuOutcome) {
    BINDER_DISCONNECTED(
        "BINDER_DISCONNECTED",
        "The Shizuku connection was lost. Start the service again, then refresh.",
        ShizukuOutcome.BLOCKED
    ),
    TIMEOUT(
        "TIMEOUT",
        "The Shizuku service did not answer in time. It may be busy or stopping; try again.",
        ShizukuOutcome.BLOCKED
    ),
    PERMISSION_DENIED(
        "PERMISSION_DENIED",
        "Shizuku denied the call. Authorize SonicCore inside Shizuku Manager.",
        ShizukuOutcome.BLOCKED
    ),
    SERVICE_UNAVAILABLE(
        "SERVICE_UNAVAILABLE",
        "The Shizuku binder is not ready. Start the Shizuku service and refresh.",
        ShizukuOutcome.BLOCKED
    ),
    REMOTE_EXCEPTION(
        "REMOTE_EXCEPTION",
        "The Shizuku service rejected the call. Nothing was changed.",
        ShizukuOutcome.BLOCKED
    ),
    INTERRUPTED(
        "INTERRUPTED",
        "The call was interrupted before it finished. Nothing was changed.",
        ShizukuOutcome.BLOCKED
    ),
    UNSUPPORTED_COMMAND(
        "UNSUPPORTED_COMMAND",
        "This Shizuku server does not implement the requested call.",
        ShizukuOutcome.UNSUPPORTED
    ),
    UNKNOWN(
        "UNKNOWN",
        "The call failed for an unrecognised reason. Nothing was changed.",
        ShizukuOutcome.BLOCKED
    )
}

/**
 * Maps a thrown exception to a [ShizukuFailure].
 *
 * Classification is by class name on purpose: it keeps the table pure JVM code
 * (no `android.os.RemoteException` import in unit-testable paths) and covers the
 * exceptions actually thrown by the Shizuku client and its binder layer.
 */
object ShizukuFailureMapper {

    fun classify(throwableClassName: String, message: String? = null): ShizukuFailure {
        val name = throwableClassName.substringAfterLast('.')
        val text = message.orEmpty()
        return when {
            name.endsWith("DeadObjectException") || name.endsWith("DeadSystemException") ->
                ShizukuFailure.BINDER_DISCONNECTED
            name.endsWith("TimeoutException") || name.endsWith("TimeoutCancellationException") ->
                ShizukuFailure.TIMEOUT
            name.endsWith("InterruptedException") || name.endsWith("InterruptedIOException") ->
                ShizukuFailure.INTERRUPTED
            name.endsWith("SecurityException") || (name.endsWith("RemoteException") && text.contains("permission", true)) ->
                ShizukuFailure.PERMISSION_DENIED
            name.endsWith("IllegalStateException") ->
                // Thrown by the Shizuku client for "binder haven't been received".
                ShizukuFailure.SERVICE_UNAVAILABLE
            name.endsWith("UnsupportedOperationException") ||
                name.endsWith("NoSuchMethodError") ||
                name.endsWith("AbstractMethodError") ||
                name.endsWith("NoSuchMethodException") ->
                ShizukuFailure.UNSUPPORTED_COMMAND
            name.endsWith("RemoteException") -> ShizukuFailure.REMOTE_EXCEPTION
            else -> ShizukuFailure.UNKNOWN
        }
    }

    fun classify(throwable: Throwable): ShizukuFailure =
        classify(throwable.javaClass.name, throwable.message)

    /** Human-readable reason, keeping the classified text and any detail the service returned. */
    fun reasonFor(failure: ShizukuFailure, message: String? = null): String {
        val detail = message?.trim().orEmpty()
        if (detail.isEmpty() || detail == failure.reason) return failure.reason
        return "${failure.reason} ($detail)"
    }
}
