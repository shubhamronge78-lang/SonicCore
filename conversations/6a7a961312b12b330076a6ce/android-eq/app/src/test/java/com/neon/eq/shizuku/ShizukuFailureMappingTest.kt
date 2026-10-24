package com.neon.eq.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Error mapping for every failure a Shizuku IPC call can produce. Nothing here may
 * map to AVAILABLE: a failed call is a BLOCKED or UNSUPPORTED outcome, never a
 * success, and the reason must tell the user what to do next.
 */
class ShizukuFailureMappingTest {

    // Local stand-ins with the same simple names as the real exceptions. The mapper
    // classifies by name so it stays free of Android types, and these let the whole
    // table run on the JVM.
    private class DeadObjectException : RuntimeException("binder died")
    private class TimeoutException : RuntimeException("no reply")
    private class SecurityException : RuntimeException("permission denied by shizuku")
    private class RemoteException(message: String) : RuntimeException(message)
    private class IllegalStateException : RuntimeException("binder haven't been received")
    private class UnsupportedOperationException : RuntimeException("not supported")
    private class NoSuchMethodError : RuntimeException("missing")
    private class InterruptedException : RuntimeException("interrupted")

    @Test
    fun binderDeathMapsToDisconnected() {
        val failure = ShizukuFailureMapper.classify(DeadObjectException())
        assertEquals(ShizukuFailure.BINDER_DISCONNECTED, failure)
        assertEquals(ShizukuOutcome.BLOCKED, failure.outcome)
        assertTrue(failure.reason.contains("connection was lost", ignoreCase = true))
    }

    @Test
    fun timeoutMapsToTimeout() {
        val failure = ShizukuFailureMapper.classify(TimeoutException())
        assertEquals(ShizukuFailure.TIMEOUT, failure)
        assertEquals(ShizukuOutcome.BLOCKED, failure.outcome)
    }

    @Test
    fun securityExceptionMapsToPermissionDenied() {
        val failure = ShizukuFailureMapper.classify(SecurityException())
        assertEquals(ShizukuFailure.PERMISSION_DENIED, failure)
        assertTrue(failure.reason.contains("Authorize", ignoreCase = true))
    }

    @Test
    fun remoteExceptionAboutPermissionsMapsToPermissionDenied() {
        val failure = ShizukuFailureMapper.classify(RemoteException("Requires permission: moe.shizuku.manager.permission"))
        assertEquals(ShizukuFailure.PERMISSION_DENIED, failure)
    }

    @Test
    fun plainRemoteExceptionMapsToRemoteException() {
        val failure = ShizukuFailureMapper.classify(RemoteException("Transaction failed"))
        assertEquals(ShizukuFailure.REMOTE_EXCEPTION, failure)
        assertTrue(failure.reason.contains("Nothing was changed", ignoreCase = true))
    }

    @Test
    fun illegalStateMapsToServiceUnavailable() {
        val failure = ShizukuFailureMapper.classify(IllegalStateException())
        assertEquals(ShizukuFailure.SERVICE_UNAVAILABLE, failure)
        assertEquals(ShizukuOutcome.BLOCKED, failure.outcome)
    }

    @Test
    fun unsupportedCallsMapToUnsupportedOutcome() {
        listOf(UnsupportedOperationException(), NoSuchMethodError()).forEach { throwable ->
            val failure = ShizukuFailureMapper.classify(throwable)
            assertEquals(ShizukuFailure.UNSUPPORTED_COMMAND, failure)
            assertEquals(ShizukuOutcome.UNSUPPORTED, failure.outcome)
        }
    }

    @Test
    fun interruptionMapsToInterrupted() {
        assertEquals(ShizukuFailure.INTERRUPTED, ShizukuFailureMapper.classify(InterruptedException()))
    }

    @Test
    fun unknownFailuresStayBlockedAndGeneric() {
        val failure = ShizukuFailureMapper.classify(IllegalArgumentException("boom"))
        assertEquals(ShizukuFailure.UNKNOWN, failure)
        assertEquals(ShizukuOutcome.BLOCKED, failure.outcome)
    }

    @Test
    fun noFailureEverMapsToAvailable() {
        ShizukuFailure.values().forEach { failure ->
            assertFalse("$failure must not masquerade as success", failure.outcome == ShizukuOutcome.AVAILABLE)
        }
    }

    @Test
    fun reasonKeepsTheServiceDetail() {
        val failure = ShizukuFailure.TIMEOUT
        val reason = ShizukuFailureMapper.reasonFor(failure, "No answer within 2000 ms.")
        assertTrue(reason.startsWith(failure.reason))
        assertTrue(reason.contains("2000 ms"))

        // A duplicated message is not appended twice.
        assertEquals(failure.reason, ShizukuFailureMapper.reasonFor(failure, failure.reason))
        assertEquals(failure.reason, ShizukuFailureMapper.reasonFor(failure, "   "))
    }
}
