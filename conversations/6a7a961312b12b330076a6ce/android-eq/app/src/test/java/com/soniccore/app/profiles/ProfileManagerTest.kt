package com.soniccore.app.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagerTest {

    @Test
    fun exportAndImportRoundTrip_preservesAllParameters() {
        val original = SonicCoreAudioProfile(
            id = "test_profile_1",
            name = "Studio Acoustic",
            bands = intArrayOf(3, 2, 1, 0, 0, 1, 2, 3, 4, 5),
            preampDb = 3.5f,
            bassBoost = 150,
            virtualizer = 200,
            loudness = 50,
            peqEnabled = true,
            peqFreq = 1250f,
            peqGainDb = 4.0f,
            peqQ = 1.8f,
            limiterEnabled = true,
            limiterCeilingDb = -0.5f,
            compressorEnabled = true,
            compressorThresholdDb = -16.0f,
            compressorRatio = 3.5f,
            stereoWidth = 1.25f,
            themeId = "neon_cyber",
            glassIntensity = "HIGH",
            isFavorite = true
        )

        val jsonStr = ProfileManager.exportToJson(original)
        assertTrue(jsonStr.contains("Studio Acoustic"))
        assertTrue(jsonStr.contains("test_profile_1"))

        val imported = ProfileManager.importFromJson(jsonStr)
        assertNotNull(imported)
        assertEquals(original.id, imported!!.id)
        assertEquals(original.name, imported.name)
        assertTrue(original.bands.contentEquals(imported.bands))
        assertEquals(original.preampDb, imported.preampDb, 0.01f)
        assertEquals(original.bassBoost, imported.bassBoost)
        assertEquals(original.virtualizer, imported.virtualizer)
        assertEquals(original.loudness, imported.loudness)
        assertEquals(original.peqEnabled, imported.peqEnabled)
        assertEquals(original.peqFreq, imported.peqFreq, 0.01f)
        assertEquals(original.peqGainDb, imported.peqGainDb, 0.01f)
        assertEquals(original.peqQ, imported.peqQ, 0.01f)
        assertEquals(original.limiterEnabled, imported.limiterEnabled)
        assertEquals(original.limiterCeilingDb, imported.limiterCeilingDb, 0.01f)
        assertEquals(original.compressorEnabled, imported.compressorEnabled)
        assertEquals(original.compressorThresholdDb, imported.compressorThresholdDb, 0.01f)
        assertEquals(original.compressorRatio, imported.compressorRatio, 0.01f)
        assertEquals(original.stereoWidth, imported.stereoWidth, 0.01f)
        assertEquals(original.themeId, imported.themeId)
        assertEquals(original.glassIntensity, imported.glassIntensity)
        assertEquals(original.isFavorite, imported.isFavorite)
    }

    @Test
    fun importFromJson_clampsOutOfBoundsValues() {
        val dangerousJson = """
            {
                "name": "Dangerous Boost",
                "bands": [100, -999, 45, 0, 0, 0, 0, 0, 0, 50],
                "preamp_db": 99.0,
                "bass_boost": 99999,
                "peq_freq": 500000.0,
                "peq_gain_db": -99.0,
                "peq_q": 99.0,
                "limiter_ceiling_db": 50.0,
                "compressor_threshold_db": -999.0,
                "compressor_ratio": 999.0,
                "stereo_width": 10.0,
                "glass_intensity": "EXTREME"
            }
        """.trimIndent()

        val profile = ProfileManager.importFromJson(dangerousJson)
        assertNotNull(profile)
        // Bands clamped between -20 and +20
        assertEquals(20, profile!!.bands[0])
        assertEquals(-20, profile.bands[1])
        assertEquals(20, profile.bands[2])
        assertEquals(20, profile.bands[9])
        // Preamp clamped to 20
        assertEquals(20.0f, profile.preampDb, 0.01f)
        // Bass boost clamped to 1000
        assertEquals(1000, profile.bassBoost)
        // PEQ freq clamped to 20000
        assertEquals(20000.0f, profile.peqFreq, 0.01f)
        // PEQ gain clamped to -20
        assertEquals(-20.0f, profile.peqGainDb, 0.01f)
        // PEQ Q clamped to 20
        assertEquals(20.0f, profile.peqQ, 0.01f)
        // Limiter ceiling clamped to 0
        assertEquals(0.0f, profile.limiterCeilingDb, 0.01f)
        // Compressor threshold clamped to -60
        assertEquals(-60.0f, profile.compressorThresholdDb, 0.01f)
        // Compressor ratio clamped to 20
        assertEquals(20.0f, profile.compressorRatio, 0.01f)
        // Stereo width clamped to 2.0
        assertEquals(2.0f, profile.stereoWidth, 0.01f)
        // Glass intensity falls back to MEDIUM
        assertEquals("MEDIUM", profile.glassIntensity)
    }

    @Test
    fun importFromJson_rejectsMalformedOrEmptyJson() {
        assertNull(ProfileManager.importFromJson(""))
        assertNull(ProfileManager.importFromJson("    "))
        assertNull(ProfileManager.importFromJson("{ not valid json }"))
        assertNull(ProfileManager.importFromJson("""{"type":"unknown"}""")) // missing bands
    }

    @Test
    fun exportAndImportBundle_handlesMultipleProfiles() {
        val p1 = SonicCoreAudioProfile(id = "p1", name = "Profile 1", bands = IntArray(10) { 1 })
        val p2 = SonicCoreAudioProfile(id = "p2", name = "Profile 2", bands = IntArray(10) { -1 })

        val bundleJson = ProfileManager.exportBundleToJson(listOf(p1, p2))
        val importedList = ProfileManager.importBundleFromJson(bundleJson)

        assertEquals(2, importedList.size)
        assertEquals("Profile 1", importedList[0].name)
        assertEquals("Profile 2", importedList[1].name)
    }
}
