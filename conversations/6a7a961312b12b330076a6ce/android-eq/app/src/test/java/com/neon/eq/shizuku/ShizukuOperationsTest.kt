package com.neon.eq.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capability detection and error handling for the Shizuku operation registry.
 * Every state must resolve to an explicit, explained availability — never a
 * silent "available" for something that cannot run.
 */
class ShizukuOperationsTest {

    private fun statuses(state: ShizukuState, apiLevel: Int = 34) =
        ShizukuOperations.evaluate(state, apiLevel).associateBy { it.operation.id }

    @Test
    fun everyOperationHasUniqueIdAndDescription() {
        val ids = ShizukuOperations.OPERATIONS.map { it.id }
        assertEquals("Operation ids must be unique", ids.distinct().size, ids.size)
        ShizukuOperations.OPERATIONS.forEach { operation ->
            assertTrue(operation.id.isNotBlank())
            assertTrue(operation.title.isNotBlank())
            assertTrue(operation.description.isNotBlank())
        }
    }

    @Test
    fun everyEvaluatedStatusCarriesANonEmptyReason() {
        ShizukuState.values().forEach { state ->
            ShizukuOperations.evaluate(state, 34).forEach { status ->
                assertNotNull(status.reason)
                assertTrue(
                    "Empty reason for ${status.operation.id} in $state",
                    status.reason.isNotBlank()
                )
            }
        }
    }

    @Test
    fun onlyImplementedOperationsEverBecomeAvailable() {
        val connected = statuses(ShizukuState.CONNECTED)
        val available = connected.values.filter { it.availability == ShizukuAvailability.AVAILABLE }
        // Exactly the two genuinely wired operations: state probe and manager launch.
        assertEquals(2, available.size)
        assertEquals(
            setOf("connection_state", "open_manager"),
            available.map { it.operation.id }.toSet()
        )
        available.forEach { status ->
            assertTrue(status.operation.implemented)
            assertTrue(status.availability.isRunnable)
        }
    }

    @Test
    fun privilegedOperationsAreExplicitlyNotImplementedWhenConnected() {
        val connected = statuses(ShizukuState.CONNECTED)
        listOf("audio_policy_read", "audio_route_force", "secure_settings_read", "permission_grant", "binder_diagnostics")
            .forEach { id ->
                val status = connected.getValue(id)
                assertEquals(ShizukuAvailability.NOT_IMPLEMENTED, status.availability)
                assertFalse(status.availability.isRunnable)
                assertTrue(
                    "Unimplemented ${status.operation.title} must explain itself",
                    status.reason.isNotBlank()
                )
            }
    }

    @Test
    fun missingInstallationReportsInstallGuidance() {
        val status = statuses(ShizukuState.NOT_INSTALLED).getValue("audio_policy_read")
        assertEquals(ShizukuAvailability.NOT_INSTALLED, status.availability)
        assertTrue(status.reason.contains("Install", ignoreCase = true))
    }

    @Test
    fun stoppedServiceReportsConnectionRequirement() {
        val status = statuses(ShizukuState.NOT_RUNNING).getValue("audio_policy_read")
        assertEquals(ShizukuAvailability.REQUIRES_CONNECTION, status.availability)
        assertTrue(status.reason.contains("Start", ignoreCase = true))
    }

    @Test
    fun missingAuthorizationReportsPermissionRequirement() {
        val status = statuses(ShizukuState.PERMISSION_REQUIRED).getValue("audio_policy_read")
        assertEquals(ShizukuAvailability.REQUIRES_PERMISSION, status.availability)
        assertTrue(status.reason.contains("Authorize", ignoreCase = true))
    }

    @Test
    fun unsupportedPlatformReportsNotSupported() {
        val status = statuses(ShizukuState.UNSUPPORTED).getValue("audio_policy_read")
        assertEquals(ShizukuAvailability.NOT_SUPPORTED, status.availability)
    }

    @Test
    fun belowMinimumApiNothingIsAvailable() {
        val apiLevel = ShizukuOperations.MIN_API - 1
        val evaluated = ShizukuOperations.evaluate(ShizukuState.CONNECTED, apiLevel)
        assertTrue(evaluated.isNotEmpty())
        assertTrue(
            "No operation may run below the minimum API",
            evaluated.all { it.availability == ShizukuAvailability.NOT_SUPPORTED }
        )
    }

    @Test
    fun localOperationsStayAvailableWithoutShizuku() {
        val missing = statuses(ShizukuState.NOT_INSTALLED)
        assertEquals(ShizukuAvailability.AVAILABLE, missing.getValue("connection_state").availability)
        assertEquals(ShizukuAvailability.AVAILABLE, missing.getValue("open_manager").availability)
    }

    @Test
    fun setupStepsReflectConnectionProgress() {
        val connected = ShizukuOperations.setupSteps(ShizukuState.CONNECTED)
        assertTrue(connected.all { it.done })

        val missing = ShizukuOperations.setupSteps(ShizukuState.NOT_INSTALLED)
        assertFalse(missing.first().done)
        assertTrue("Later steps are blocked until Shizuku is installed", missing.drop(1).all { it.blocked })

        val unauthorized = ShizukuOperations.setupSteps(ShizukuState.PERMISSION_REQUIRED)
        assertTrue(unauthorized[0].done)
        assertTrue(unauthorized[1].done)
        assertFalse(unauthorized[2].done)
    }
}
