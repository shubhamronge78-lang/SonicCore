package com.soniccore.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Current UI application mode state.
 */
val appModeState: MutableState<SurfaceModes> = mutableStateOf(SurfaceModes.DARK)
