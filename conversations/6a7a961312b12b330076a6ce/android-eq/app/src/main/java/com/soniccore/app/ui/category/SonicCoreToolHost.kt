package com.soniccore.app.ui.category

import com.soniccore.app.NeonTheme
import com.soniccore.app.dsp.AudioControlState
import com.soniccore.app.dsp.DspChain
import com.soniccore.app.dsp.DspTelemetry
import com.soniccore.app.engine.AutoEqImport
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
     * AutoEQ import, step 1 — (profile name, AutoEQ text) to what the profile
     * contains. Pure: it parses and reports, and changes nothing, so cancelling
     * the review cannot disturb the active sound.
     */
    val onReviewAutoEqProfile: (String, String) -> AutoEqImport.Review,
    /**
     * AutoEQ import, step 2 — the deliberate step. Stores the reviewed profile as
     * a custom preset on the engine's own curve and, when `applyNow` is true, makes
     * it the active curve and writes its preamp once through the native preamp
     * parameter. Returns the sentence the screen shows.
     */
    val onApplyAutoEqProfile: (AutoEqImport.Review, Boolean) -> String,
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
    /**
     * The native processing chain the engine is really running: the order of
     * its eight stages and which of them are bypassed (see [DspChain]).
     */
    val dspChain: DspChain,
    /**
     * Applies a whole chain — order and bypass flags together — through the same
     * validated path the rest of the app uses.
     *
     * @return true when the engine accepted the order **and** the parameter set
     *   was persisted. A native refusal (a malformed order) returns false so the
     *   screen can say the write did not land instead of reporting success.
     */
    val onDspChainChange: (DspChain) -> Boolean,
    /**
     * The graphic EQ band values the engine holds, in dB. Read-only view used by
     * the Audio Console's inline response curve; the faders themselves live on
     * the Equalizer screen.
     */
    val eqBands: List<Float>,
    /**
     * One measured snapshot of the native pipeline, or a telemetry value whose
     * `available` is false with the bridge's own reason. Called on a slow tick by
     * the console, never once per recomposition.
     */
    val measuredDsp: () -> DspTelemetry,

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
