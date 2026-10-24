package com.neon.eq.ui.category

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.neon.eq.ui.audio.AudioDiagnosticsScreen
import com.neon.eq.ui.audio.AudioPresetsScreen
import com.neon.eq.ui.customization.CustomizationAccentScreen
import com.neon.eq.ui.customization.CustomizationCardScreen
import com.neon.eq.ui.customization.CustomizationGlassScreen
import com.neon.eq.ui.customization.CustomizationLayoutScreen
import com.neon.eq.ui.customization.CustomizationMotionScreen
import com.neon.eq.ui.customization.CustomizationPreviewScreen
import com.neon.eq.ui.device.DeviceBatteryScreen
import com.neon.eq.ui.device.DeviceCpuScreen
import com.neon.eq.ui.device.DeviceDisplayScreen
import com.neon.eq.ui.device.DeviceHealthScreen
import com.neon.eq.ui.device.DeviceMemoryScreen
import com.neon.eq.ui.device.DeviceNetworkScreen
import com.neon.eq.ui.device.DeviceOverviewScreen
import com.neon.eq.ui.device.DeviceSensorsScreen
import com.neon.eq.ui.device.DeviceStorageScreen
import com.neon.eq.ui.navigation.SonicCoreTool
import com.neon.eq.ui.permissions.AudioCaptureConsentScreen
import com.neon.eq.ui.permissions.ClearHistoryScreen
import com.neon.eq.ui.permissions.LocalDataExportsScreen
import com.neon.eq.ui.permissions.NotificationBackgroundScreen
import com.neon.eq.ui.permissions.PrivacyInformationScreen
import com.neon.eq.ui.permissions.SpecialAppAccessScreen
import com.neon.eq.ui.settings.SettingsAboutScreen
import com.neon.eq.ui.settings.SettingsAudioDefaultsScreen
import com.neon.eq.ui.settings.SettingsBehaviorScreen
import com.neon.eq.ui.settings.SettingsDiagnosticsScreen
import com.neon.eq.ui.settings.SettingsNavigationScreen
import com.neon.eq.ui.settings.SettingsNotificationsScreen
import com.neon.eq.ui.settings.SettingsStorageScreen
import com.neon.eq.ui.shizuku.ShizukuAuthorizationScreen
import com.neon.eq.ui.shizuku.ShizukuConnectionScreen
import com.neon.eq.ui.shizuku.ShizukuHistoryScreen
import com.neon.eq.ui.shizuku.ShizukuOperationsScreen
import com.neon.eq.ui.shizuku.ShizukuSetupGuideScreen
import com.neon.eq.ui.shizuku.ShizukuTroubleshootingScreen

/**
 * Every tool that renders its own screen inside the navigation tree, i.e. every
 * registry tool whose [SonicCoreTool.destination] is null. A tool that reuses one
 * of the long-standing full-screen workspaces (the equalizer, the player, the
 * capture centre, …) is NOT listed here — the host routes it to that workspace
 * instead, so one screen always has exactly one owner.
 *
 * `SonicCoreNavigationTest` asserts this set matches the registry, which is what
 * keeps the dispatch below and the tool tree from drifting apart.
 */
val SonicCoreInlineToolIds: Set<String> = setOf(
    // Audio
    "audio.presets",
    "audio.diagnostics",
    // Device Center
    "device.overview",
    "device.cpu",
    "device.memory",
    "device.storage",
    "device.battery",
    "device.display",
    "device.sensors",
    "device.network",
    "device.health",
    // Shizuku Center
    "shizuku.connection",
    "shizuku.setup",
    "shizuku.authorization",
    "shizuku.operations",
    "shizuku.history",
    "shizuku.troubleshooting",
    // Customization
    "custom.accent",
    "custom.glass",
    "custom.layout",
    "custom.cards",
    "custom.motion",
    "custom.preview",
    // Permissions & Privacy
    "privacy.special",
    "privacy.consent",
    "privacy.notifications",
    "privacy.info",
    "privacy.exports",
    "privacy.reset",
    // Settings
    "settings.audio",
    "settings.navigation",
    "settings.notifications",
    "settings.behavior",
    "settings.storage",
    "settings.diagnostics",
    "settings.about"
)

/**
 * Renders the screen for an inline tool. Every screen is a pure consumer: it
 * reads what it needs from [host] and requests changes through its callbacks, so
 * no tool screen reaches into the engine or the capture pipeline directly.
 */
@Composable
fun SonicCoreToolScreen(
    tool: SonicCoreTool,
    host: SonicCoreToolHost,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenModifier = modifier.fillMaxWidth()
    when (tool.id) {
        // ── Audio ──
        "audio.presets" -> AudioPresetsScreen(host, screenModifier)
        "audio.diagnostics" -> AudioDiagnosticsScreen(host, screenModifier)

        // ── Device Center ──
        "device.overview" -> DeviceOverviewScreen(host, screenModifier)
        "device.cpu" -> DeviceCpuScreen(screenModifier)
        "device.memory" -> DeviceMemoryScreen(screenModifier)
        "device.storage" -> DeviceStorageScreen(screenModifier)
        "device.battery" -> DeviceBatteryScreen(screenModifier)
        "device.display" -> DeviceDisplayScreen(screenModifier)
        "device.sensors" -> DeviceSensorsScreen(screenModifier)
        "device.network" -> DeviceNetworkScreen(screenModifier)
        "device.health" -> DeviceHealthScreen(host, screenModifier)

        // ── Shizuku Center ──
        "shizuku.connection" -> ShizukuConnectionScreen(screenModifier)
        "shizuku.setup" -> ShizukuSetupGuideScreen(screenModifier)
        "shizuku.authorization" -> ShizukuAuthorizationScreen(screenModifier)
        "shizuku.operations" -> ShizukuOperationsScreen(screenModifier)
        "shizuku.history" -> ShizukuHistoryScreen(host, screenModifier)
        "shizuku.troubleshooting" -> ShizukuTroubleshootingScreen(screenModifier)

        // ── Customization ──
        "custom.accent" -> CustomizationAccentScreen(context, screenModifier)
        "custom.glass" -> CustomizationGlassScreen(host, screenModifier)
        "custom.layout" -> CustomizationLayoutScreen(context, screenModifier)
        "custom.cards" -> CustomizationCardScreen(context, screenModifier)
        "custom.motion" -> CustomizationMotionScreen(context, screenModifier)
        "custom.preview" -> CustomizationPreviewScreen(host, context, screenModifier)

        // ── Permissions & Privacy ──
        "privacy.special" -> SpecialAppAccessScreen(screenModifier)
        "privacy.consent" -> AudioCaptureConsentScreen(screenModifier)
        "privacy.notifications" -> NotificationBackgroundScreen(screenModifier)
        "privacy.info" -> PrivacyInformationScreen(screenModifier)
        "privacy.exports" -> LocalDataExportsScreen(host, screenModifier)
        "privacy.reset" -> ClearHistoryScreen(host, screenModifier)

        // ── Settings ──
        "settings.audio" -> SettingsAudioDefaultsScreen(host, screenModifier)
        "settings.navigation" -> SettingsNavigationScreen(context, screenModifier)
        "settings.notifications" -> SettingsNotificationsScreen(context, screenModifier)
        "settings.behavior" -> SettingsBehaviorScreen(context, screenModifier)
        "settings.storage" -> SettingsStorageScreen(host, screenModifier)
        "settings.diagnostics" -> SettingsDiagnosticsScreen(host, screenModifier)
        "settings.about" -> SettingsAboutScreen(host, screenModifier)

        // Unreachable while the registry and `SonicCoreInlineToolIds` agree (the
        // navigation test enforces that). It stays honest rather than blank: no
        // tool is ever reported as working when it has no screen behind it.
        else -> LimitNote(
            "${tool.title} has no screen on this build. Nothing is simulated — the tool is listed but not wired.",
            tool.category.tone
        )
    }
}
