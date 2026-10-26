package com.soniccore.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.soniccore.app.ui.audio.AudioBassTrebleScreen
import com.soniccore.app.ui.audio.AudioDevicesScreen
import com.soniccore.app.ui.audio.AudioDiagnosticsScreen
import com.soniccore.app.ui.audio.AudioPresetsScreen
import com.soniccore.app.ui.audio.AudioSpatialScreen
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreToolHost
import com.soniccore.app.ui.customization.CustomizationAccentScreen
import com.soniccore.app.ui.customization.CustomizationCardScreen
import com.soniccore.app.ui.customization.CustomizationGlassScreen
import com.soniccore.app.ui.customization.CustomizationLayoutScreen
import com.soniccore.app.ui.customization.CustomizationMotionScreen
import com.soniccore.app.ui.customization.CustomizationPreviewScreen
import com.soniccore.app.ui.customization.RepositoryManagerScreen
import com.soniccore.app.ui.device.DeviceBatteryScreen
import com.soniccore.app.ui.device.DeviceCpuScreen
import com.soniccore.app.ui.device.DeviceDisplayScreen
import com.soniccore.app.ui.device.DeviceHealthScreen
import com.soniccore.app.ui.device.DeviceMemoryScreen
import com.soniccore.app.ui.device.DeviceNetworkScreen
import com.soniccore.app.ui.device.DeviceOverviewScreen
import com.soniccore.app.ui.device.DeviceSensorsScreen
import com.soniccore.app.ui.device.DeviceStorageScreen
import com.soniccore.app.ui.permissions.AudioCaptureConsentScreen
import com.soniccore.app.ui.permissions.ClearHistoryScreen
import com.soniccore.app.ui.permissions.LocalDataExportsScreen
import com.soniccore.app.ui.permissions.NotificationBackgroundScreen
import com.soniccore.app.ui.permissions.PrivacyInformationScreen
import com.soniccore.app.ui.permissions.SpecialAppAccessScreen
import com.soniccore.app.ui.settings.SettingsAboutScreen
import com.soniccore.app.ui.settings.SettingsAudioDefaultsScreen
import com.soniccore.app.ui.settings.SettingsBehaviorScreen
import com.soniccore.app.ui.settings.SettingsDiagnosticsScreen
import com.soniccore.app.ui.settings.SettingsGeneralScreen
import com.soniccore.app.ui.settings.SettingsNavigationScreen
import com.soniccore.app.ui.settings.SettingsNotificationsScreen
import com.soniccore.app.ui.settings.SettingsStorageScreen
import com.soniccore.app.ui.shizuku.ShizukuAuthorizationScreen
import com.soniccore.app.ui.shizuku.ShizukuConnectionScreen
import com.soniccore.app.ui.shizuku.ShizukuHistoryScreen
import com.soniccore.app.ui.shizuku.ShizukuOperationsScreen
import com.soniccore.app.ui.shizuku.ShizukuSetupGuideScreen
import com.soniccore.app.ui.shizuku.ShizukuTroubleshootingScreen

/**
 * Tool ids that render one of the dedicated category tool screens.
 *
 * A tool whose [SonicCoreTool.destination] is non-null reuses a long-standing
 * full-screen body instead and is therefore absent from this set — the two sets
 * together must cover [SonicCoreToolRegistry.all], which
 * `SonicCoreNavigationTest` asserts.
 */
object SonicCoreToolRoutes {
    const val AUDIO_PRESETS = "audio.presets"
    const val AUDIO_DIAGNOSTICS = "audio.diagnostics"
    const val AUDIO_BASS_TREBLE = "audio.bass_treble"
    const val AUDIO_SPATIAL = "audio.spatial"
    const val AUDIO_DEVICES = "audio.devices"

    const val DEVICE_OVERVIEW = "device.overview"
    const val DEVICE_CPU = "device.cpu"
    const val DEVICE_MEMORY = "device.memory"
    const val DEVICE_STORAGE = "device.storage"
    const val DEVICE_BATTERY = "device.battery"
    const val DEVICE_DISPLAY = "device.display"
    const val DEVICE_SENSORS = "device.sensors"
    const val DEVICE_NETWORK = "device.network"
    const val DEVICE_HEALTH = "device.health"

    const val SHIZUKU_CONNECTION = "shizuku.connection"
    const val SHIZUKU_SETUP = "shizuku.setup"
    const val SHIZUKU_AUTHORIZATION = "shizuku.authorization"
    const val SHIZUKU_OPERATIONS = "shizuku.operations"
    const val SHIZUKU_HISTORY = "shizuku.history"
    const val SHIZUKU_TROUBLESHOOTING = "shizuku.troubleshooting"

    const val CUSTOM_ACCENT = "custom.accent"
    const val CUSTOM_GLASS = "custom.glass"
    const val CUSTOM_LAYOUT = "custom.layout"
    const val CUSTOM_CARDS = "custom.cards"
    const val CUSTOM_MOTION = "custom.motion"
    const val CUSTOM_PREVIEW = "custom.preview"
    const val CUSTOM_REPOSITORIES = "custom.repositories"

    const val PRIVACY_SPECIAL = "privacy.special"
    const val PRIVACY_CONSENT = "privacy.consent"
    const val PRIVACY_NOTIFICATIONS = "privacy.notifications"
    const val PRIVACY_INFO = "privacy.info"
    const val PRIVACY_EXPORTS = "privacy.exports"
    const val PRIVACY_RESET = "privacy.reset"

    const val SETTINGS_AUDIO = "settings.audio"
    const val SETTINGS_NAVIGATION = "settings.navigation"
    const val SETTINGS_NOTIFICATIONS = "settings.notifications"
    const val SETTINGS_BEHAVIOR = "settings.behavior"
    const val SETTINGS_STORAGE = "settings.storage"
    const val SETTINGS_DIAGNOSTICS = "settings.diagnostics"
    const val SETTINGS_ABOUT = "settings.about"

    val ids: Set<String> = setOf(
        AUDIO_PRESETS, AUDIO_DIAGNOSTICS, AUDIO_BASS_TREBLE, AUDIO_SPATIAL, AUDIO_DEVICES,
        DEVICE_OVERVIEW, DEVICE_CPU, DEVICE_MEMORY, DEVICE_STORAGE, DEVICE_BATTERY,
        DEVICE_DISPLAY, DEVICE_SENSORS, DEVICE_NETWORK, DEVICE_HEALTH,
        SHIZUKU_CONNECTION, SHIZUKU_SETUP, SHIZUKU_AUTHORIZATION, SHIZUKU_OPERATIONS,
        SHIZUKU_HISTORY, SHIZUKU_TROUBLESHOOTING,
        CUSTOM_ACCENT, CUSTOM_GLASS, CUSTOM_LAYOUT, CUSTOM_CARDS, CUSTOM_MOTION, CUSTOM_PREVIEW,
        CUSTOM_REPOSITORIES,
        PRIVACY_SPECIAL, PRIVACY_CONSENT, PRIVACY_NOTIFICATIONS, PRIVACY_INFO,
        PRIVACY_EXPORTS, PRIVACY_RESET,
        SETTINGS_AUDIO, SETTINGS_NAVIGATION, SETTINGS_NOTIFICATIONS, SETTINGS_BEHAVIOR,
        SETTINGS_STORAGE, SETTINGS_DIAGNOSTICS, SETTINGS_ABOUT
    )
}

/**
 * Renders the tool screen that belongs to [tool].
 *
 * Only tools without a destination reach this router — the host renders the
 * tools that reuse a full-screen body itself. A tool with no registered screen
 * says so instead of rendering an empty or decorative page.
 */
@Composable
fun SonicCoreToolScreen(
    tool: SonicCoreTool,
    host: SonicCoreToolHost,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (tool.id) {
            SonicCoreToolRoutes.AUDIO_PRESETS -> AudioPresetsScreen(host)
            SonicCoreToolRoutes.AUDIO_DIAGNOSTICS -> AudioDiagnosticsScreen(host)
            SonicCoreToolRoutes.AUDIO_BASS_TREBLE -> AudioBassTrebleScreen(host)
            SonicCoreToolRoutes.AUDIO_SPATIAL -> AudioSpatialScreen(host)
            SonicCoreToolRoutes.AUDIO_DEVICES -> AudioDevicesScreen()

            SonicCoreToolRoutes.DEVICE_OVERVIEW -> DeviceOverviewScreen(host)
            SonicCoreToolRoutes.DEVICE_CPU -> DeviceCpuScreen()
            SonicCoreToolRoutes.DEVICE_MEMORY -> DeviceMemoryScreen()
            SonicCoreToolRoutes.DEVICE_STORAGE -> DeviceStorageScreen()
            SonicCoreToolRoutes.DEVICE_BATTERY -> DeviceBatteryScreen()
            SonicCoreToolRoutes.DEVICE_DISPLAY -> DeviceDisplayScreen()
            SonicCoreToolRoutes.DEVICE_SENSORS -> DeviceSensorsScreen()
            SonicCoreToolRoutes.DEVICE_NETWORK -> DeviceNetworkScreen()
            SonicCoreToolRoutes.DEVICE_HEALTH -> DeviceHealthScreen(host)

            SonicCoreToolRoutes.SHIZUKU_CONNECTION -> ShizukuConnectionScreen()
            SonicCoreToolRoutes.SHIZUKU_SETUP -> ShizukuSetupGuideScreen()
            SonicCoreToolRoutes.SHIZUKU_AUTHORIZATION -> ShizukuAuthorizationScreen()
            SonicCoreToolRoutes.SHIZUKU_OPERATIONS -> ShizukuOperationsScreen()
            SonicCoreToolRoutes.SHIZUKU_HISTORY -> ShizukuHistoryScreen(host)
            SonicCoreToolRoutes.SHIZUKU_TROUBLESHOOTING -> ShizukuTroubleshootingScreen()

            SonicCoreToolRoutes.CUSTOM_ACCENT -> CustomizationAccentScreen(context)
            SonicCoreToolRoutes.CUSTOM_GLASS -> CustomizationGlassScreen(host)
            SonicCoreToolRoutes.CUSTOM_LAYOUT -> CustomizationLayoutScreen(context)
            SonicCoreToolRoutes.CUSTOM_CARDS -> CustomizationCardScreen(context)
            SonicCoreToolRoutes.CUSTOM_MOTION -> CustomizationMotionScreen(context)
            SonicCoreToolRoutes.CUSTOM_PREVIEW -> CustomizationPreviewScreen(host, context)
            SonicCoreToolRoutes.CUSTOM_REPOSITORIES -> RepositoryManagerScreen()

            SonicCoreToolRoutes.PRIVACY_SPECIAL -> SpecialAppAccessScreen()
            SonicCoreToolRoutes.PRIVACY_CONSENT -> AudioCaptureConsentScreen()
            SonicCoreToolRoutes.PRIVACY_NOTIFICATIONS -> NotificationBackgroundScreen()
            SonicCoreToolRoutes.PRIVACY_INFO -> PrivacyInformationScreen()
            SonicCoreToolRoutes.PRIVACY_EXPORTS -> LocalDataExportsScreen(host)
            SonicCoreToolRoutes.PRIVACY_RESET -> ClearHistoryScreen(host)

            SonicCoreToolRoutes.SETTINGS_AUDIO -> SettingsAudioDefaultsScreen(host)
            SonicCoreToolRoutes.SETTINGS_NAVIGATION -> SettingsNavigationScreen(context)
            SonicCoreToolRoutes.SETTINGS_NOTIFICATIONS -> SettingsNotificationsScreen(context)
            SonicCoreToolRoutes.SETTINGS_BEHAVIOR -> SettingsBehaviorScreen(context)
            SonicCoreToolRoutes.SETTINGS_STORAGE -> SettingsStorageScreen(host)
            SonicCoreToolRoutes.SETTINGS_DIAGNOSTICS -> SettingsDiagnosticsScreen(host)
            SonicCoreToolRoutes.SETTINGS_ABOUT -> SettingsAboutScreen(host)

            else -> CapabilityLegend(
                SonicCoreCapability.NOT_IMPLEMENTED,
                "${tool.title} has no screen registered in this build — nothing is rendered instead of pretending it works."
            )
        }
    }
}
