package com.neon.eq.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure formatting/classification helpers behind the device dashboard. These are
 * the parts of capability detection that do not need a device, so they are
 * covered by fast JVM tests.
 */
class DeviceCapabilityProbeTest {

    @Test
    fun formatStorageUsesReadableUnits() {
        assertEquals("Unavailable", DeviceCapabilityProbe.formatStorage(0L))
        assertEquals("Unavailable", DeviceCapabilityProbe.formatStorage(-1L))
        assertEquals("512 MB", DeviceCapabilityProbe.formatStorage(512L * 1024L * 1024L))
        assertEquals("1.5 GB", DeviceCapabilityProbe.formatStorage((1536L * 1024L * 1024L)))
        assertEquals("128.0 GB", DeviceCapabilityProbe.formatStorage(128L * 1024L * 1024L * 1024L))
    }

    @Test
    fun storageUsagePercentStaysWithinBounds() {
        assertEquals(75, DeviceCapabilityProbe.storageUsagePercent(100L, 25L))
        assertEquals(0, DeviceCapabilityProbe.storageUsagePercent(0L, 0L))
        // Free space larger than total (can happen with clamped quotas) must not go negative.
        assertEquals(0, DeviceCapabilityProbe.storageUsagePercent(100L, 200L))
        assertEquals(100, DeviceCapabilityProbe.storageUsagePercent(100L, 0L))
    }

    @Test
    fun formatFrequencyHandlesMissingAndScales() {
        assertEquals("Not readable", DeviceCapabilityProbe.formatFrequency(null))
        assertEquals("Not readable", DeviceCapabilityProbe.formatFrequency(0L))
        assertEquals("800 MHz", DeviceCapabilityProbe.formatFrequency(800L))
        assertEquals("2.40 GHz", DeviceCapabilityProbe.formatFrequency(2400L))
    }

    @Test
    fun networkStatusLabelDistinguishesOfflineAndMetering() {
        assertEquals("Offline", DeviceCapabilityProbe.networkStatusLabel(false, "Wi-Fi", null))
        assertEquals("Wi-Fi · metered", DeviceCapabilityProbe.networkStatusLabel(true, "Wi-Fi", true))
        assertEquals("Wi-Fi · unmetered", DeviceCapabilityProbe.networkStatusLabel(true, "Wi-Fi", false))
        assertEquals("Cellular", DeviceCapabilityProbe.networkStatusLabel(true, "Cellular", null))
    }

    @Test
    fun sensorCoveragePreservesTrackedOrder() {
        val (present, missing) = DeviceCapabilityProbe.sensorCoverage(
            listOf("Gyroscope", "Accelerometer", "Light")
        )
        assertEquals(listOf("Accelerometer", "Gyroscope", "Light"), present)
        assertTrue(missing.contains("Magnetometer"))
        assertTrue(missing.contains("Step counter"))
        assertEquals(
            "Present and missing must partition the tracked list",
            DeviceCapabilityProbe.TRACKED_SENSORS.size,
            present.size + missing.size
        )
    }

    @Test
    fun unknownSensorsAreIgnoredRatherThanInvented() {
        val (present, missing) = DeviceCapabilityProbe.sensorCoverage(listOf("Not A Real Sensor"))
        assertTrue(present.isEmpty())
        assertEquals(DeviceCapabilityProbe.TRACKED_SENSORS.size, missing.size)
    }
}
