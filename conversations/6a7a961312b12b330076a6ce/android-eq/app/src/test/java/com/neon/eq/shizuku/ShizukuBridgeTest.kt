package com.neon.eq.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuBridgeTest {

    @Test
    fun shizukuStates_haveDescriptiveLabels() {
        for (state in ShizukuState.values()) {
            assertNotNull(state.label)
            assertFalse(state.label.isBlank())
            assertNotNull(state.description)
            assertFalse(state.description.isBlank())
        }
        assertEquals("Connected", ShizukuState.CONNECTED.label)
        assertEquals("Not Installed", ShizukuState.NOT_INSTALLED.label)
        assertEquals("Service Stopped", ShizukuState.NOT_RUNNING.label)
        assertEquals("Permission Required", ShizukuState.PERMISSION_REQUIRED.label)
    }

    @Test
    fun shizukuPackageConstants_areCorrect() {
        assertEquals("moe.shizuku.privileged.api", ShizukuBridge.SHIZUKU_PACKAGE_V3)
        assertEquals("moe.shizuku.manager", ShizukuBridge.SHIZUKU_PACKAGE_MANAGER)
        assertEquals("moe.shizuku.manager.permission.API_V23", ShizukuBridge.SHIZUKU_PERMISSION)
    }
}
