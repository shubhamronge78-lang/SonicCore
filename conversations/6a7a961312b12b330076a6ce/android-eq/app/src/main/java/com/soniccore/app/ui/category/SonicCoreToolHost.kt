package com.soniccore.app.ui.category

import com.soniccore.app.NeonTheme
import com.soniccore.app.dsp.AudioControlState
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
    /**
     * AutoEQ import: (profile name, AutoEQ text) to the sentence the screen shows.
     * The host parses the profile, stores it as a custom preset on the engine's own
     * curve and applies its preamp through the native preamp parameter.
     */
    val onImportAutoEqProfile: (String, String) -> String,
    val onApplyPreset: (String) -> Boolean,
    val onSaveCustomPreset: (String) -> Unit,
    val onDeleteCustomPreset: (String) -> Unit,

    // Appearance
    val glassLevel: Int,
    val onGlassLevelChange: (Int) -> Unit,
    val themes: List<NeonTheme>,
    val currentThemeId: String,
    val onThemeApply: (String) -> Unit,

    // Audio controls — the DSP parameters the Audio Center screens edit. A
    // screen reads [audioControl] and hands a new snapshot back; the host
    // validates it, merges it into the persisted parameter set and applies it
    // through the existing native path. No screen writes DSP state directly.
    val audioControl: AudioControlState,
    val onAudioControlChange: (AudioControlState) -> Unit,

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
