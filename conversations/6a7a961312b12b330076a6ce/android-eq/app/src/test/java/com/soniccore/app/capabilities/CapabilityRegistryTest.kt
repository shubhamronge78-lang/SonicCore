package com.soniccore.app.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRegistryTest {

    @Test
    fun capabilityCategories_haveDisplayNames() {
        for (cat in CapabilityCategory.values()) {
            assertNotNull(cat.displayName)
            assertFalse(cat.displayName.isBlank())
        }
    }

    @Test
    fun supportStates_haveLabels() {
        assertEquals("Supported", SupportState.SUPPORTED.label)
        assertEquals("Partial", SupportState.PARTIALLY_SUPPORTED.label)
        assertEquals("Unsupported", SupportState.UNSUPPORTED.label)
        assertEquals("Permission Needed", SupportState.PERMISSION_REQUIRED.label)
        assertEquals("Restricted", SupportState.RESTRICTED.label)
    }

    @Test
    fun capabilityItem_structureHoldsExpectedFields() {
        val item = CapabilityItem(
            id = "test_cap",
            name = "AudioPlaybackCapture",
            category = CapabilityCategory.AUDIO_CAPTURE,
            state = SupportState.SUPPORTED,
            summary = "Capture audio from supported applications",
            technicalDetails = "android.media.AudioRecord capture config",
            restrictionsAndNotes = "Source apps can opt out via manifest",
            actionableGuidance = "Target Android 10+"
        )
        assertEquals("test_cap", item.id)
        assertEquals(CapabilityCategory.AUDIO_CAPTURE, item.category)
        assertEquals(SupportState.SUPPORTED, item.state)
        assertNotNull(item.actionableGuidance)
    }
}
