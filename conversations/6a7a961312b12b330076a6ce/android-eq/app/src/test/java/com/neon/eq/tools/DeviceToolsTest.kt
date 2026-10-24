package com.neon.eq.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceToolsTest {

    @Test
    fun shortcutsList_hasAllExpectedShortcutsWithUniqueIds() {
        val shortcuts = DeviceToolsManager.SHORTCUTS
        assertTrue(shortcuts.size >= 8)

        val ids = shortcuts.map { it.id }
        assertEquals("All shortcut IDs must be unique", ids.distinct().size, ids.size)

        assertTrue(ids.contains("sound_settings"))
        assertTrue(ids.contains("volume_panel"))
        assertTrue(ids.contains("system_equalizer"))
        assertTrue(ids.contains("bluetooth_settings"))
        assertTrue(ids.contains("battery_optimization"))
        assertTrue(ids.contains("app_notifications"))
        assertTrue(ids.contains("app_permissions"))
        assertTrue(ids.contains("dev_options"))
    }

    @Test
    fun shortcuts_haveNonEmptyTitlesAndRequirements() {
        for (sc in DeviceToolsManager.SHORTCUTS) {
            assertNotNull(sc.title)
            assertFalse(sc.title.isBlank())
            assertNotNull(sc.subtitle)
            assertFalse(sc.subtitle.isBlank())
            assertNotNull(sc.permissionRequirement)
            assertFalse(sc.permissionRequirement.isBlank())
        }
    }
}
