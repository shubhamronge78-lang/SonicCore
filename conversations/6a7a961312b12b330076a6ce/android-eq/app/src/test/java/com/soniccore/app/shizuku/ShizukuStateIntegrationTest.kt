package com.soniccore.app.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end checks of the five connection states the Shizuku center must handle:
 * absent, stopped, unauthorized, authorized and disconnected.
 *
 * These run on the JVM against the real registry, engine and diagnostics gate — no
 * binder exists here, so the authorized case asserts eligibility and the execution
 * contract of [ShizukuDiagnostics] (a blocked environment returns before any
 * Android call). The physical-device half of this matrix is covered in
 * docs/shizuku-operations.md and is reported as not automatically verified.
 */
class ShizukuStateIntegrationTest {

    private val diagnosticsOperation =
        ShizukuOperations.OPERATIONS.first { it.id == ShizukuDiagnostics.OPERATION_ID }

    private fun environment(
        state: ShizukuState,
        uid: Int = 2000,
        serverVersion: Int = 13,
        deniedPermanently: Boolean = false
    ): ShizukuEnvironment {
        val connected = state == ShizukuState.CONNECTED
        return ShizukuEnvironment(
            state = state,
            deviceSdk = 34,
            serverVersion = if (connected) serverVersion else ShizukuEnvironment.UNKNOWN_VALUE,
            serverUid = if (connected) uid else ShizukuEnvironment.UNKNOWN_VALUE,
            permissionGranted = connected,
            permissionDeniedPermanently = deniedPermanently,
            binderAlive = connected
        )
    }

    @Test
    fun state1ShizukuAbsent() {
        val environment = environment(ShizukuState.NOT_INSTALLED)
        val status = ShizukuOperationEngine.evaluate(diagnosticsOperation, environment)
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.NOT_INSTALLED, status.blocker)

        val result = ShizukuDiagnostics.run(environment)
        assertEquals(ShizukuOutcome.BLOCKED, result.outcome)
        assertFalse(result.succeeded)
        assertTrue("A blocked run must not report values", result.details.isEmpty())
        assertEquals(ShizukuDiagnostics.OPERATION_ID, result.operationId)
        assertNotNull(result.reason)
    }

    @Test
    fun state2ServiceStopped() {
        val environment = environment(ShizukuState.NOT_RUNNING)
        val status = ShizukuOperationEngine.evaluate(diagnosticsOperation, environment)
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.SERVICE_STOPPED, status.blocker)

        val result = ShizukuDiagnostics.run(environment)
        assertEquals(ShizukuOutcome.BLOCKED, result.outcome)
        assertTrue(result.details.isEmpty())
        assertTrue(result.reason.contains("Start", ignoreCase = true))
    }

    @Test
    fun state3RunningButUnauthorized() {
        val environment = environment(ShizukuState.PERMISSION_REQUIRED)
        val status = ShizukuOperationEngine.evaluate(diagnosticsOperation, environment)
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.PERMISSION_REQUIRED, status.blocker)

        val result = ShizukuDiagnostics.run(environment)
        assertEquals(ShizukuOutcome.BLOCKED, result.outcome)
        assertTrue(result.details.isEmpty())
        assertTrue(result.reason.contains("Authorize", ignoreCase = true))

        val denied = ShizukuDiagnostics.run(environment(ShizukuState.PERMISSION_REQUIRED, deniedPermanently = true))
        assertEquals(ShizukuOutcome.BLOCKED, denied.outcome)
        assertTrue(denied.reason.contains("denied", ignoreCase = true))
    }

    @Test
    fun state4AuthorizedAndConnected() {
        val environment = environment(ShizukuState.CONNECTED)
        val status = ShizukuOperationEngine.evaluate(diagnosticsOperation, environment)
        assertEquals("A connected, authorized device must make diagnostics runnable", ShizukuOutcome.AVAILABLE, status.outcome)
        assertTrue(status.outcome.isRunnable)
        assertTrue(environment.isElevated)
        assertEquals(ShizukuPrivilege.SHELL, environment.privilege)

        // Root-backed Shizuku is reported as root, not as shell.
        assertEquals(ShizukuPrivilege.ROOT, environment(ShizukuState.CONNECTED, uid = 0).privilege)
    }

    @Test
    fun state5ConnectionLost() {
        val environment = environment(ShizukuState.DISCONNECTED)
        val status = ShizukuOperationEngine.evaluate(diagnosticsOperation, environment)
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.BINDER_DISCONNECTED, status.blocker)

        val result = ShizukuDiagnostics.run(environment)
        assertEquals(ShizukuOutcome.BLOCKED, result.outcome)
        assertTrue(result.details.isEmpty())
        assertTrue(result.reason.contains("lost", ignoreCase = true))
    }

    @Test
    fun everyStateKeepsLocalOperationsWorking() {
        listOf(
            ShizukuState.NOT_INSTALLED,
            ShizukuState.NOT_RUNNING,
            ShizukuState.PERMISSION_REQUIRED,
            ShizukuState.CONNECTED,
            ShizukuState.DISCONNECTED,
            ShizukuState.UNSUPPORTED
        ).forEach { state ->
            val statuses = ShizukuOperations.evaluate(state, 34).associateBy { it.operation.id }
            listOf("connection_state", "open_manager").forEach { id ->
                assertTrue(
                    "$id must stay available in $state",
                    statuses.getValue(id).outcome == ShizukuOutcome.AVAILABLE
                )
            }
        }
    }

    @Test
    fun blockedStatesNeverClaimAnImplementedElevatedOperation() {
        listOf(
            ShizukuState.NOT_INSTALLED,
            ShizukuState.NOT_RUNNING,
            ShizukuState.PERMISSION_REQUIRED,
            ShizukuState.DISCONNECTED,
            ShizukuState.UNSUPPORTED
        ).forEach { state ->
            ShizukuOperations.evaluate(state, 34)
                .filter { it.operation.requiresConnection }
                .forEach { status ->
                    assertFalse(
                        "${status.operation.id} must not be runnable in $state",
                        status.outcome.isRunnable
                    )
                }
        }
    }
}
