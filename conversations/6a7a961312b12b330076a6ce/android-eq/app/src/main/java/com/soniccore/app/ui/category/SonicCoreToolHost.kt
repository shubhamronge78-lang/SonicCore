package com.soniccore.app.ui.category

import com.soniccore.app.NeonTheme
import com.soniccore.app.ui.home.HomeAudioSummary
import com.soniccore.app.ui.navigation.SonicCoreDestination

/**
 * Everything a SonicCore tool screen is allowed to observe or trigger.
 *
 * Tool screens never touch the engine or capture pipeline directly: they read
 * the values collected here and request changes through these callbacks, so the
 * UI stays a pure consumer and the audio path is never driven from a
 * composition.
 */
data class SonicCoreToolHost(
    val appVersion: String,
    val audioSummary: HomeAudioSummary,

    // Presets
    val currentPreset: String,
    val builtinPresetNames: List<String>,
    val customPresetNames: List<String>,
    val presetsExportJson: () -> String,
    val onImportPresets: (String) -> Int,
    val onApplyPreset: (String) -> Boolean,
    val onSaveCustomPreset: (String) -> Unit,
    val onDeleteCustomPreset: (String) -> Unit,

    // Appearance
    val glassLevel: Int,
    val onGlassLevelChange: (Int) -> Unit,
    val themes: List<NeonTheme>,
    val currentThemeId: String,
    val onThemeApply: (String) -> Unit,

    // Data / navigation
    val operationLogSize: () -> Int,
    val onClearOperationLog: () -> Unit,
    val sessionHistorySize: () -> Int,
    val onClearSessionHistory: () -> Boolean,
    val locallyStoredBytes: () -> Long,
    val onOpenFullSettings: () -> Unit,
    val onShareText: (String, String) -> Unit,
    val onNavigate: (SonicCoreDestination) -> Unit
)
