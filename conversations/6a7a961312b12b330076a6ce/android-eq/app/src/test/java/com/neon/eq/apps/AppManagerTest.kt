package com.neon.eq.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppManagerTest {

    @Test
    fun installedAudioApp_fieldsAndDefaults() {
        val app = InstalledAudioApp(
            packageName = "com.spotify.music",
            appName = "Spotify",
            versionName = "8.9.10",
            targetSdk = 34,
            isSystemApp = false,
            isCurrentlyPlaying = true,
            assignedPreset = "Bass Boost"
        )
        assertEquals("com.spotify.music", app.packageName)
        assertEquals("Spotify", app.appName)
        assertEquals("8.9.10", app.versionName)
        assertEquals(34, app.targetSdk)
        assertFalse(app.isSystemApp)
        assertTrue(app.isCurrentlyPlaying)
        assertEquals("Bass Boost", app.assignedPreset)
    }

    @Test
    fun sortingOrder_prioritizesPlayingAppAndAssignedProfiles() {
        val app1 = InstalledAudioApp("com.app.a", "Alpha Music", "1.0", 33, false, isCurrentlyPlaying = false, assignedPreset = null)
        val app2 = InstalledAudioApp("com.app.b", "Beta Music", "1.0", 33, false, isCurrentlyPlaying = true, assignedPreset = null)
        val app3 = InstalledAudioApp("com.app.c", "Charlie Audio", "1.0", 33, false, isCurrentlyPlaying = false, assignedPreset = "Rock")

        val list = listOf(app1, app2, app3).sortedWith(
            compareByDescending<InstalledAudioApp> { it.isCurrentlyPlaying }
                .thenByDescending { it.assignedPreset != null }
                .thenBy { it.appName.lowercase() }
        )

        assertEquals("Beta Music", list[0].appName) // Currently playing
        assertEquals("Charlie Audio", list[1].appName) // Assigned profile
        assertEquals("Alpha Music", list[2].appName) // Alphabetical
    }
}
