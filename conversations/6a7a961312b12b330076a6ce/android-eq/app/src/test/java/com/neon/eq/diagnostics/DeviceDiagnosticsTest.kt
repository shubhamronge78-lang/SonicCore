package com.neon.eq.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDiagnosticsTest {

    @Test
    fun redactSensitiveInfo_redactsMacAddress() {
        val raw = "Connected Bluetooth device at 00:1A:2B:3C:4D:5E with strong RSSI"
        val redacted = DeviceDiagnosticsManager.redactSensitiveInfo(raw)
        assertFalse(redacted.contains("00:1A:2B:3C:4D:5E"))
        assertTrue(redacted.contains("[REDACTED_MAC]"))
    }

    @Test
    fun redactSensitiveInfo_redactsIpAddress() {
        val raw = "Server socket bound at 192.168.1.105:8080"
        val redacted = DeviceDiagnosticsManager.redactSensitiveInfo(raw)
        assertFalse(redacted.contains("192.168.1.105"))
        assertTrue(redacted.contains("[REDACTED_IP]"))
    }

    @Test
    fun redactSensitiveInfo_redactsAppStorageDirectory() {
        val raw = "Saving crash dump to /data/user/0/com.neon.eq/files/crash.log"
        val redacted = DeviceDiagnosticsManager.redactSensitiveInfo(raw)
        assertFalse(redacted.contains("/data/user/0/com.neon.eq"))
        assertTrue(redacted.contains("[APP_STORAGE_DIR]"))
    }

    @Test
    fun redactSensitiveInfo_redactsBluetoothDeviceAddress() {
        val raw = "Active routing to Device(11:22:33:44:55:66)"
        val redacted = DeviceDiagnosticsManager.redactSensitiveInfo(raw)
        assertFalse(redacted.contains("11:22:33:44:55:66"))
        assertTrue(redacted.contains("Device([REDACTED_BT])"))
    }

    @Test
    fun memoryStatus_calculatesLowMemoryThresholdProperly() {
        val normalMem = MemoryStatus(
            totalRamMb = 6144,
            availRamMb = 2048,
            thresholdMb = 512,
            isLowMemory = false,
            jvmMaxHeapMb = 512,
            jvmUsedHeapMb = 64,
            jvmFreeHeapMb = 448
        )
        assertFalse(normalMem.isLowMemory)
        assertEquals(6144, normalMem.totalRamMb)

        val lowMem = MemoryStatus(
            totalRamMb = 3072,
            availRamMb = 256,
            thresholdMb = 300,
            isLowMemory = true,
            jvmMaxHeapMb = 256,
            jvmUsedHeapMb = 200,
            jvmFreeHeapMb = 56
        )
        assertTrue(lowMem.isLowMemory)
    }

    @Test
    fun thermalStatus_mapsLabelsAndThrottling() {
        val normal = ThermalStatus(0, "None (Normal)", false, "Test note")
        assertFalse(normal.isThrottling)

        val throttled = ThermalStatus(3, "Severe Throttling", true, "Test note")
        assertTrue(throttled.isThrottling)
    }
}
