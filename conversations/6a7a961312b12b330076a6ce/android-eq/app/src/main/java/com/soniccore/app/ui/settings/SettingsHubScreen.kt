package com.soniccore.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.PlatformInfoRow
import com.soniccore.app.ui.design.PlatformSectionLabel
import com.soniccore.app.ui.design.SettingRow
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.navigation.SonicCoreDestination

/**
 * Settings hub. Every row performs a real action: opening the existing settings
 * sheet, navigating to a center, or launching the system app-info screen.
 */
@Composable
fun SettingsHubScreen(
    appVersion: String,
    onOpenFullSettings: () -> Unit,
    onNavigate: (SonicCoreDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // The launcher shell owns vertical scrolling; see DeviceDiagnosticsScreen.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            PlatformSectionLabel("Preferences")
            Spacer(Modifier.height(4.dp))
            SettingRow(
                title = "Engine & UI settings",
                description = "Performance mode, start on boot, preset restore, theme application",
                onClick = onOpenFullSettings,
                modifier = Modifier.semantics { contentDescription = "Open engine and UI settings" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
            SettingRow(
                title = "Customization Center",
                description = "Themes, glass intensity, appearance, system customization support",
                onClick = { onNavigate(SonicCoreDestination.CUSTOMIZATION) },
                modifier = Modifier.semantics { contentDescription = "Open Customization Center" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
            SettingRow(
                title = "Permissions & Privacy",
                description = "Real permission status, capture consent and privacy explanations",
                onClick = { onNavigate(SonicCoreDestination.PERMISSIONS) },
                modifier = Modifier.semantics { contentDescription = "Open Permissions and Privacy Center" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("Platform tools", color = SonicCoreColors.Violet)
            Spacer(Modifier.height(4.dp))
            SettingRow(
                title = "Device Tools",
                description = "System audio shortcuts and public audio focus probes",
                onClick = { onNavigate(SonicCoreDestination.DEVICE_TOOLS) },
                modifier = Modifier.semantics { contentDescription = "Open Device Tools" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
            SettingRow(
                title = "Shizuku Center",
                description = "Optional elevated bridge status and operation registry",
                onClick = { onNavigate(SonicCoreDestination.SHIZUKU) },
                modifier = Modifier.semantics { contentDescription = "Open Shizuku Center" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
            SettingRow(
                title = "Diagnostics",
                description = "Capability probes, operation log and exportable report",
                onClick = { onNavigate(SonicCoreDestination.DIAGNOSTICS) },
                modifier = Modifier.semantics { contentDescription = "Open Diagnostics" }
            ) { Text("›", fontSize = 18.sp, color = SonicCoreColors.Cyan) }
        }

        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("About")
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Version", appVersion)
            Spacer(Modifier.height(10.dp))
            SettingRow(
                title = "App info & system permissions",
                description = "Opens the Android app-info screen for SonicCore",
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    } catch (_: Throwable) {
                        // Nothing else to do: the platform has no app-info screen to open.
                    }
                },
                modifier = Modifier.semantics { contentDescription = "Open Android app info" }
            ) { Text("↗", fontSize = 16.sp, color = SonicCoreColors.Cyan, fontWeight = FontWeight.Bold) }
        }
    }
}
