package com.neon.eq.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eligibility matrix for the Shizuku operation registry.
 *
 * Every state — absent, stopped, unauthorized, authorized, disconnected, platform
 * unsupported — must resolve each operation to exactly one of AVAILABLE, BLOCKED,
 * UNSUPPORTED or NOT_IMPLEMENTED, with the first blocking step named.
 */
class ShizukuOperationEngineTest {

    private val operations = ShizukuOperations.OPERATIONS.associateBy { it.id }

    private fun env(
        state: ShizukuState,
        sdk: Int = 34,
        serverVersion: Int = 13,
        serverUid: Int = 2000,
        deniedPermanently: Boolean = false
    ) = ShizukuEnvironment(
        state = state,
        deviceSdk = sdk,
        serverVersion = if (state == ShizukuState.CONNECTED) serverVersion else ShizukuEnvironment.UNKNOWN_VALUE,
        serverUid = if (state == ShizukuState.CONNECTED) serverUid else ShizukuEnvironment.UNKNOWN_VALUE,
        permissionGranted = state == ShizukuState.CONNECTED,
        permissionDeniedPermanently = deniedPermanently,
        binderAlive = state == ShizukuState.CONNECTED
    )

    private fun status(state: ShizukuState, id: String, sdk: Int = 34): ShizukuOperationStatus {
        val environment = env(state, sdk)
        return ShizukuOperationEngine.evaluate(operations.getValue(id), environment)
    }

    @Test
    fun everyOperationResolvesToExactlyOneStructuredOutcome() {
        ShizukuState.values().forEach { state ->
            ShizukuOperationEngine.evaluateAll(env(state)).forEach { status ->
                assertNotNull(status.outcome)
                assertNotNull("Missing availability for ${status.operation.id}", status.availability)
                assertTrue("Empty reason for ${status.operation.id} in $state", status.reason.isNotBlank())
                // BLOCKED and UNSUPPORTED verdicts name their cause; the other two do not.
                if (status.outcome == ShizukuOutcome.BLOCKED || status.outcome == ShizukuOutcome.UNSUPPORTED) {
                    assertNotNull("${status.operation.id} must name its blocker", status.blocker)
                } else {
                    assertNull(status.blocker)
                }
            }
        }
    }

    @Test
    fun absentShizukuBlocksEveryElevatedOperation() {
        val evaluated = ShizukuOperationEngine.evaluateAll(env(ShizukuState.NOT_INSTALLED))
        evaluated.filter { it.operation.requiresConnection }.forEach { status ->
            assertEquals(status.operation.id, ShizukuOutcome.BLOCKED, status.outcome)
            assertEquals(status.operation.id, ShizukuBlocker.NOT_INSTALLED, status.blocker)
        }
        // Local operations never depend on the elevated bridge.
        assertEquals(ShizukuOutcome.AVAILABLE, status(ShizukuState.NOT_INSTALLED, "connection_state").outcome)
        assertEquals(ShizukuOutcome.AVAILABLE, status(ShizukuState.NOT_INSTALLED, "open_manager").outcome)
    }

    @Test
    fun stoppedServiceBlocksWithStartGuidance() {
        val status = status(ShizukuState.NOT_RUNNING, "ipc_diagnostics")
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.SERVICE_STOPPED, status.blocker)
        assertTrue(status.reason.contains("Start", ignoreCase = true))
    }

    @Test
    fun unauthorizedBlocksOnPermissionAndDistinguishesPermanentDenial() {
        val required = status(ShizukuState.PERMISSION_REQUIRED, "ipc_diagnostics")
        assertEquals(ShizukuOutcome.BLOCKED, required.outcome)
        assertEquals(ShizukuBlocker.PERMISSION_REQUIRED, required.blocker)
        assertTrue(required.reason.contains("Authorize", ignoreCase = true))

        val denied = ShizukuOperationEngine.evaluate(
            operations.getValue("ipc_diagnostics"),
            env(ShizukuState.PERMISSION_REQUIRED, deniedPermanently = true)
        )
        assertEquals(ShizukuBlocker.PERMISSION_DENIED, denied.blocker)
        assertTrue(denied.reason.contains("denied", ignoreCase = true))
    }

    @Test
    fun authorizedMakesOnlyImplementedOperationsAvailable() {
        val evaluated = ShizukuOperationEngine.evaluateAll(env(ShizukuState.CONNECTED))
        val available = evaluated.filter { it.outcome == ShizukuOutcome.AVAILABLE }

        assertEquals(
            setOf("connection_state", "open_manager", "ipc_diagnostics"),
            available.map { it.operation.id }.toSet()
        )
        available.forEach { status ->
            assertTrue("${status.operation.id} reports AVAILABLE without an implementation", status.operation.implemented)
            assertEquals(ShizukuAvailability.AVAILABLE, status.availability)
        }
    }

    @Test
    fun disconnectedBlocksAsConnectionLostNotAsNeverStarted() {
        val status = status(ShizukuState.DISCONNECTED, "ipc_diagnostics")
        assertEquals(ShizukuOutcome.BLOCKED, status.outcome)
        assertEquals(ShizukuBlocker.BINDER_DISCONNECTED, status.blocker)
        assertEquals(ShizukuAvailability.REQUIRES_CONNECTION, status.availability)
        assertTrue(status.reason.contains("lost", ignoreCase = true))
        assertFalse(status.reason.contains("Install", ignoreCase = true))
    }

    @Test
    fun platformUnsupportedIsUnsupportedNotBlocked() {
        val status = status(ShizukuState.UNSUPPORTED, "ipc_diagnostics")
        assertEquals(ShizukuOutcome.UNSUPPORTED, status.outcome)
        assertEquals(ShizukuBlocker.DEVICE_UNSUPPORTED, status.blocker)
        assertEquals(ShizukuAvailability.NOT_SUPPORTED, status.availability)
    }

    @Test
    fun deviceApiBelowMinimumIsUnsupportedForEverything() {
        val evaluated = ShizukuOperationEngine.evaluateAll(env(ShizukuState.CONNECTED, sdk = ShizukuOperations.MIN_API - 1))
        assertTrue(evaluated.isNotEmpty())
        evaluated.forEach { status ->
            assertEquals(status.operation.id, ShizukuOutcome.UNSUPPORTED, status.outcome)
            assertEquals(ShizukuBlocker.API_LEVEL, status.blocker)
        }
    }

    @Test
    fun olderShizukuServerIsUnsupportedOnlyWhenItActuallyGates() {
        val gated = operations.getValue("ipc_diagnostics").copy(minServerVersion = 13)
        val environment = env(ShizukuState.CONNECTED, serverVersion = 12)
        val status = ShizukuOperationEngine.evaluate(gated, environment)
        assertEquals(ShizukuOutcome.UNSUPPORTED, status.outcome)
        assertEquals(ShizukuBlocker.SERVER_VERSION, status.blocker)
        assertTrue(status.reason.contains("Update", ignoreCase = true))

        val fine = ShizukuOperationEngine.evaluate(gated.copy(minServerVersion = 12), environment)
        assertEquals(ShizukuOutcome.AVAILABLE, fine.outcome)
    }

    @Test
    fun unknownServerVersionNeverFakesAVersionGate() {
        // A connected environment without a reported version must not be rejected
        // for being "too old"; the operation simply stays eligible.
        val gated = operations.getValue("ipc_diagnostics").copy(minServerVersion = 13)
        val environment = env(ShizukuState.CONNECTED).copy(serverVersion = ShizukuEnvironment.UNKNOWN_VALUE)
        assertEquals(ShizukuOutcome.AVAILABLE, ShizukuOperationEngine.evaluate(gated, environment).outcome)
    }

    @Test
    fun unimplementedOperationsAreNeverRunnableInAnyState() {
        ShizukuState.values().forEach { state ->
            ShizukuOperationEngine.evaluateAll(env(state))
                .filter { !it.operation.implemented }
                .forEach { status ->
                    assertFalse(
                        "${status.operation.id} is runnable in $state without an implementation",
                        status.outcome.isRunnable
                    )
                    assertFalse(status.availability.isRunnable)
                }
        }
    }

    @Test
    fun operationsThatWouldChangeSystemStateAreNeverAvailable() {
        ShizukuOperations.OPERATIONS.filter { !it.readOnly }.forEach { operation ->
            val status = ShizukuOperationEngine.evaluate(operation, env(ShizukuState.CONNECTED))
            assertEquals(operation.id, ShizukuOutcome.NOT_IMPLEMENTED, status.outcome)
            assertTrue(
                "${operation.id} must explain why it is refused",
                status.reason.contains("not implemented", ignoreCase = true)
            )
        }
    }

    @Test
    fun accessTiersAreDocumentedForEveryOperation() {
        ShizukuOperations.OPERATIONS.forEach { operation ->
            assertTrue("${operation.id} needs an access tier", operation.access.label.isNotBlank())
            assertTrue("${operation.id} needs access detail", operation.access.detail.isNotBlank())
        }
        assertEquals(ShizukuAccess.NONE, operations.getValue("connection_state").access)
        assertEquals(ShizukuAccess.SHIZUKU, operations.getValue("ipc_diagnostics").access)
        assertEquals(ShizukuAccess.ADB, operations.getValue("permission_grant").access)
        assertEquals(ShizukuAccess.ROOT, operations.getValue("kernel_audio_debugfs").access)
        assertEquals(ShizukuAccess.IMPOSSIBLE, operations.getValue("audio_route_force").access)

        // Every tier is represented, so the registry itself documents who can do what.
        assertEquals(
            ShizukuAccess.values().toSet(),
            ShizukuOperations.OPERATIONS.map { it.access }.toSet()
        )
    }

    @Test
    fun disconnectedStateIsDistinguishedInSetupSteps() {
        val steps = ShizukuOperations.setupSteps(ShizukuState.DISCONNECTED)
        assertTrue("Shizuku is still installed", steps[0].done)
        assertFalse("The service must be started again", steps[1].done)
        assertTrue("Authorization stays blocked until the service is back", steps.drop(2).all { it.blocked })
    }
}
