package com.soniccore.app

import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.design.HomeColumnChoice

/**
 * Appearance state constants previously defined in com.neon.eq.ui.design.AppearanceState.
 * Used by UI screens for layout configuration.
 */
object AppearanceState {
    val homeColumns: HomeColumnChoice = HomeColumnChoice.TWO

    object corners {
        val radius = 16.dp
    }

    object border {
        val width = 1.dp
    }

    object density {
        val verticalPadding = 8.dp
    }
}
