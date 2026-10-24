package com.neon.eq.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeResponsiveLayoutTest {
    @Test
    fun landscapeKeepsTwoColumnsEvenOnNarrowWindows() {
        assertTrue(homeUsesTwoColumns(landscape = true, availableWidth = 480.dp))
    }

    @Test
    fun widePortraitWindowUsesTwoColumnsAtExpandedWidth() {
        assertTrue(homeUsesTwoColumns(landscape = false, availableWidth = 840.dp))
    }

    @Test
    fun narrowPortraitWindowKeepsSingleColumn() {
        assertFalse(homeUsesTwoColumns(landscape = false, availableWidth = 839.dp))
    }
}
