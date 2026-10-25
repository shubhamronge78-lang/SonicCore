package com.soniccore.app.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Bounded IPC plumbing: timeouts, mapping of thrown failures, and the guarantee
 * that one stuck call never blocks the next.
 */
class ShizukuInvokerTest {

    private class DeadObjectException : RuntimeException("binder died")

    @Test
    fun successfulCallReturnsValueAndElapsed() {
        val call = ShizukuInvoker.call(2_000) { listOf(ShizukuDetail("k", "v")) }
        assertTrue(call is ShizukuCall.Success)
        val success = call as ShizukuCall.Success
        assertEquals(1, success.value.size)
        assertEquals("v", success.value.first().value)
        assertTrue("elapsed must be measured", success.elapsedMs >= 0)
    }

    @Test
    fun overrunReturnsTimeoutFailureWithTheConfiguredBound() {
        val call = ShizukuInvoker.call(50) {
            Thread.sleep(1_000)
            "late"
        }
        assertTrue(call is ShizukuCall.Failure)
        val failure = call as ShizukuCall.Failure
        assertEquals(ShizukuFailure.TIMEOUT, failure.failure)
        assertEquals(ShizukuOutcome.BLOCKED, failure.failure.outcome)
        assertTrue("timeout reason must state the bound", failure.reason.contains("50 ms"))
    }

    @Test
    fun thrownFailuresAreClassified() {
        val call = ShizukuInvoker.call(2_000) { throw DeadObjectException() }
        assertTrue(call is ShizukuCall.Failure)
        assertEquals(ShizukuFailure.BINDER_DISCONNECTED, (call as ShizukuCall.Failure).failure)
    }

    @Test
    fun unsupportedCallsSurfaceAsUnsupported() {
        val call = ShizukuInvoker.call(2_000) { throw UnsupportedOperationException("no such command") }
        val failure = (call as ShizukuCall.Failure).failure
        assertEquals(ShizukuFailure.UNSUPPORTED_COMMAND, failure)
        assertEquals(ShizukuOutcome.UNSUPPORTED, failure.outcome)
    }

    @Test
    fun nonPositiveTimeoutIsRejected() {
        try {
            ShizukuInvoker.call(0) { "never" }
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }

    @Test
    fun aTimedOutCallDoesNotWedgeTheNextOne() {
        val stalled = ShizukuInvoker.call(20) {
            Thread.sleep(400)
            "late"
        }
        assertTrue(stalled is ShizukuCall.Failure)
        assertEquals(ShizukuFailure.TIMEOUT, (stalled as ShizukuCall.Failure).failure)

        val next = ShizukuInvoker.call(2_000) { "recovered" }
        assertTrue("a timed-out call must not block later calls", next is ShizukuCall.Success)
        assertEquals("recovered", (next as ShizukuCall.Success).value)
    }

    @Test
    fun failuresNeverReportSuccess() {
        val call = ShizukuInvoker.call(2_000) { throw IllegalStateException("binder haven't been received") }
        val failure = (call as ShizukuCall.Failure).failure
        assertEquals(ShizukuFailure.SERVICE_UNAVAILABLE, failure)
        assertFalse(failure.outcome == ShizukuOutcome.AVAILABLE)
    }
}
