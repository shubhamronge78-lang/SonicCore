package com.soniccore.app.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permission status mapping and recovery guidance. These assertions run on the
 * JVM against plain data, so the mapping that drives the Permissions Center is
 * verified without an instrumented device.
 */
class PermissionCenterTest {

    private val recordAudio = "android.permission.RECORD_AUDIO"
    private val modifyAudio = "android.permission.MODIFY_AUDIO_SETTINGS"
    private val postNotifications = "android.permission.POST_NOTIFICATIONS"
    private val bluetoothConnect = "android.permission.BLUETOOTH_CONNECT"

    private fun evaluate(
        apiLevel: Int = 34,
        declared: Set<String> = setOf(recordAudio, modifyAudio, postNotifications, bluetoothConnect),
        granted: Set<String> = emptySet(),
        batteryIgnored: Boolean = false,
        usageAccess: Boolean = false,
        captureActive: Boolean = false
    ): Map<String, PermissionEntry> = PermissionCenter.evaluate(
        PermissionProbeResult(
            apiLevel = apiLevel,
            declared = declared,
            granted = granted,
            batteryOptimizationIgnored = batteryIgnored,
            usageAccessGranted = usageAccess,
            captureSessionActive = captureActive
        )
    ).associateBy { it.id }

    @Test
    fun everyEntryHasUniqueIdAndExplainableText() {
        val entries = PermissionCenter.evaluate(
            PermissionProbeResult(34, emptySet(), emptySet(), false, false, false)
        )
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        entries.forEach { entry ->
            assertTrue(entry.title.isNotBlank())
            assertTrue(entry.reason.isNotBlank())
            assertTrue(entry.detail.isNotBlank())
        }
    }

    @Test
    fun grantedRuntimePermissionReportsGranted() {
        val entry = evaluate(granted = setOf(recordAudio)).getValue(recordAudio)
        assertEquals(PermissionState.GRANTED, entry.state)
        assertNull(entry.recovery)
        assertEquals(PermissionTarget.NONE, entry.target)
    }

    @Test
    fun declaredButUngrantedPermissionReportsDeniedWithRecovery() {
        val entry = evaluate().getValue(recordAudio)
        assertEquals(PermissionState.DENIED, entry.state)
        assertNotNull("A denied permission must offer a recovery route", entry.recovery)
        assertEquals(PermissionTarget.APP_DETAILS, entry.target)
    }

    @Test
    fun undeclaredPermissionIsNotReportedAsDenied() {
        val entry = evaluate(declared = emptySet()).getValue(recordAudio)
        assertEquals(PermissionState.NOT_DECLARED, entry.state)
        assertNull("Nothing to grant, so no settings link may be offered", entry.recovery)
        assertEquals(PermissionTarget.NONE, entry.target)
    }

    @Test
    fun normalPermissionIsGrantedAtInstall() {
        val entry = evaluate(granted = setOf(modifyAudio)).getValue(modifyAudio)
        assertEquals(PermissionState.GRANTED, entry.state)
    }

    @Test
    fun apiGateMarksNewerPermissionsUnsupported() {
        val api32 = evaluate(apiLevel = 32, granted = setOf(recordAudio))
        assertEquals(PermissionState.NOT_SUPPORTED, api32.getValue(postNotifications).state)
        assertEquals(PermissionTarget.NONE, api32.getValue(postNotifications).target)

        val api30 = evaluate(apiLevel = 30)
        assertEquals(PermissionState.NOT_SUPPORTED, api30.getValue(bluetoothConnect).state)
    }

    @Test
    fun servicesSonicCoreDoesNotShipAreReportedAsNotDeclared() {
        val entries = evaluate()
        assertEquals(PermissionState.NOT_DECLARED, entries.getValue("svc_accessibility").state)
        assertEquals(PermissionState.NOT_DECLARED, entries.getValue("svc_notification_listener").state)
    }

    @Test
    fun batteryOptimizationAndUsageAccessReflectRealProbes() {
        val off = evaluate(batteryIgnored = false, usageAccess = false)
        assertEquals(PermissionState.DENIED, off.getValue("battery_optimization").state)
        assertEquals(PermissionState.DENIED, off.getValue("usage_access").state)
        assertEquals(PermissionTarget.BATTERY_OPTIMIZATION, off.getValue("battery_optimization").target)

        val on = evaluate(batteryIgnored = true, usageAccess = true)
        assertEquals(PermissionState.GRANTED, on.getValue("battery_optimization").state)
        assertEquals(PermissionState.GRANTED, on.getValue("usage_access").state)
        assertNull(on.getValue("usage_access").recovery)
    }

    @Test
    fun captureConsentIsAlwaysSessionScoped() {
        val idle = evaluate(captureActive = false).getValue("capture_consent")
        assertEquals(PermissionState.SESSION_PROMPTED, idle.state)
        assertEquals(PermissionTarget.NONE, idle.target)
        assertTrue(idle.detail.contains("consent", ignoreCase = true))

        val active = evaluate(captureActive = true).getValue("capture_consent")
        assertEquals(PermissionState.SESSION_ACTIVE, active.state)
    }
}
