package com.neon.eq.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.neon.eq.tools.DeviceToolsManager
import com.neon.eq.shizuku.ShizukuBridge
import com.neon.eq.shizuku.ShizukuState
import com.neon.eq.ui.design.LiquidGlassDepth
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors

@Composable
fun PermissionsPrivacyCenter(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onOpenSettingsClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var postNotificationsState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.POST_NOTIFICATIONS)) }
    var recordAudioState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.RECORD_AUDIO)) }
    var modifyAudioSettingsState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.MODIFY_AUDIO_SETTINGS)) }
    var bluetoothConnectState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.BLUETOOTH_CONNECT)) }
    var accessibilityState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.BIND_ACCESSIBILITY_SERVICE)) }
    var notificationListenerState by remember { mutableStateOf(getPermissionStatus(context, Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)) }
    var batteryOptimizationState by remember { mutableStateOf("AVAILABLE") }
    var usageAccessState by remember { mutableStateOf("UNAVAILABLE") }
    var shizukuState by remember { mutableStateOf(ShizukuBridge.checkState(context)) }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SonicCoreColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "PERMISSIONS & PRIVACY",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Manage app permissions, system access, and privacy settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 12.sp
                )
            }
        }

        // Post Notifications Section (API 33+)
        PermissionSection(
            title = "Notifications",
            description = "Grant permission to show foreground EQ service notifications",
            permissionName = "POST_NOTIFICATIONS",
            status = postNotificationsState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_APP_NOTIFICATION_SETTINGS) },
            requiresApi = Build.VERSION_CODES.TIRAMISU,
            icon = "🔔",
            color = SonicCoreColors.Cyan
        )

        // Record Audio Section
        PermissionSection(
            title = "Record Audio",
            description = "Allow SonicCore to capture audio from system output for processing",
            permissionName = "RECORD_AUDIO",
            status = recordAudioState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) },
            icon = "🎙",
            color = SonicCoreColors.Success
        )

        // Modify Audio Settings Section
        PermissionSection(
            title = "Audio Settings",
            description = "Control system volume and audio routing",
            permissionName = "MODIFY_AUDIO_SETTINGS",
            status = modifyAudioSettingsState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_SOUND_SETTINGS) },
            icon = "🔊",
            color = SonicCoreColors.Violet
        )

        // Bluetooth Connect Section (API 31+)
        PermissionSection(
            title = "Bluetooth",
            description = "Connect to Bluetooth headsets and speakers",
            permissionName = "BLUETOOTH_CONNECT",
            status = bluetoothConnectState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_BLUETOOTH_SETTINGS) },
            requiresApi = Build.VERSION_CODES.S,
            icon = "📱",
            color = SonicCoreColors.Cyan
        )

        // Accessibility Section
        PermissionSection(
            title = "Accessibility",
            description = "Enable audio guidance and alternative input modes",
            permissionName = "BIND_ACCESSIBILITY_SERVICE",
            status = accessibilityState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
            icon = "♿",
            color = SonicCoreColors.Violet
        )

        // Notification Listener Section
        PermissionSection(
            title = "Notification Listener",
            description = "Monitor and process notifications from other apps",
            permissionName = "BIND_NOTIFICATION_LISTENER_SERVICE",
            status = notificationListenerState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            icon = "🔔",
            color = SonicCoreColors.Cyan
        )

        // Battery Optimization Section
        PermissionSection(
            title = "Battery Optimization",
            description = "Prevent Android from killing the EQ background service",
            permissionName = "IGNORE_BATTERY_OPTIMIZATIONS",
            status = batteryOptimizationState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) },
            icon = "🔋",
            color = SonicCoreColors.Warning
        )

        // Usage Access Section
        PermissionSection(
            title = "Usage Access",
            description = "Track app usage for adaptive audio processing",
            permissionName = "PACKAGE_USAGE_STATS",
            status = usageAccessState,
            onOpenSettingsClick = { onOpenSettingsClick(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            icon = "📊",
            color = SonicCoreColors.SecondaryText
        )

        // Shizuku Integration Section
        ShizukuSection(
            shizukuState = shizukuState,
            onOpenManagerClick = {
                val intent = ShizukuBridge.getLaunchManagerIntent(context)
                if (intent != null) {
                    try {
                        context.startActivity(intent)
                    } catch (_: Throwable) {
                        Toast.makeText(context, "Could not open Shizuku Manager", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        // System Access Shortcuts
        SystemAccessSection(
            onSoundSettingsClick = { context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS)) },
            onBluetoothSettingsClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            onBatteryOptimizationClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            context.startActivity(this)
                        }
                    } catch (_: Throwable) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                } else {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            onAppInfoClick = {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(this)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionSection(
    title: String,
    description: String,
    permissionName: String,
    status: String,
    onOpenSettingsClick: () -> Unit,
    icon: String,
    color: Color,
    requiresApi: Int = 0
) {
    val canOpenSettings = when (permissionName) {
        "POST_NOTIFICATIONS" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        "BLUETOOTH_CONNECT" -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> true
    }

    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        depth = LiquidGlassDepth.Floating
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 20.sp)
                    Column {
                        Text(
                            title,
                            fontSize = 14.sp,
                            color = SonicCoreColors.PrimaryText,
                            fontWeight = FontWeight.Medium
                        )
                        if (requiresApi > 0) {
                            Text(
                                "API ${requiresApi} required",
                                fontSize = 10.sp,
                                color = SonicCoreColors.SecondaryText
                            )
                        }
                    }
                }

                StatusBadge(status = status)
            }

            Text(
                description,
                fontSize = 12.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 16.sp
            )

            Spacer(Modifier.height(8.dp))

            if (status == "GRANTED" || status == "AVAILABLE") {
                Text(
                    "✓ Permission granted - Full access",
                    fontSize = 11.sp,
                    color = SonicCoreColors.Success,
                    fontWeight = FontWeight.Medium
                )
            } else if (status == "DENIED" || status == "BLOCKED") {
                Text(
                    "✗ Permission denied - Limited functionality",
                    fontSize = 11.sp,
                    color = SonicCoreColors.Error,
                    fontWeight = FontWeight.Medium
                )
            } else if (status == "UNAVAILABLE") {
                Text(
                    "? Feature not available on this device",
                    fontSize = 11.sp,
                    color = SonicCoreColors.Warning,
                    fontWeight = FontWeight.Medium
                )
            }

            if (canOpenSettings && (status == "DENIED" || status == "BLOCKED")) {
                Button(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open $title settings" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = color.copy(alpha = 0.20f),
                        contentColor = color
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (backgroundColor, textColor, label) = when (status) {
        "GRANTED" -> Triple(SonicCoreColors.Success.copy(alpha = 0.15f), SonicCoreColors.Success, "GRANTED")
        "DENIED" -> Triple(SonicCoreColors.Error.copy(alpha = 0.15f), SonicCoreColors.Error, "DENIED")
        "BLOCKED" -> Triple(SonicCoreColors.Warning.copy(alpha = 0.15f), SonicCoreColors.Warning, "BLOCKED")
        "AVAILABLE" -> Triple(SonicCoreColors.Cyan.copy(alpha = 0.15f), SonicCoreColors.Cyan, "AVAILABLE")
        "UNAVAILABLE" -> Triple(Color.Gray.copy(alpha = 0.15f), Color.Gray, "UNAVAILABLE")
        else -> Triple(SonicCoreColors.SecondaryText.copy(alpha = 0.15f), SonicCoreColors.SecondaryText, status)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ShizukuSection(
    shizukuState: ShizukuState,
    onOpenManagerClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        depth = LiquidGlassDepth.Floating
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SHIZUKU PRIVILEGED ACCESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                val badgeColor = when (shizukuState) {
                    ShizukuState.CONNECTED -> SonicCoreColors.Success
                    ShizukuState.PERMISSION_REQUIRED -> SonicCoreColors.Warning
                    ShizukuState.NOT_RUNNING -> SonicCoreColors.Warning
                    ShizukuState.NOT_INSTALLED -> Color.Gray
                    ShizukuState.UNSUPPORTED -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        shizukuState.label,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                shizukuState.description,
                style = MaterialTheme.typography.bodySmall,
                color = SonicCoreColors.PrimaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            if (shizukuState != ShizukuState.NOT_INSTALLED && shizukuState != ShizukuState.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenManagerClick,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open Shizuku Manager" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                        contentColor = SonicCoreColors.Cyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open Shizuku Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SystemAccessSection(
    onSoundSettingsClick: () -> Unit,
    onBluetoothSettingsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onAppInfoClick: () -> Unit
) {
    LiquidGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        depth = LiquidGlassDepth.Floating
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "QUICK SYSTEM ACCESS",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                "Direct access to Android system settings for audio and connectivity",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAccessButton(
                    icon = "🔊",
                    title = "Sound & Vib",
                    description = "Volume & audio controls",
                    onClick = onSoundSettingsClick,
                    color = SonicCoreColors.Violet
                )
                QuickAccessButton(
                    icon = "📱",
                    title = "Bluetooth",
                    description = "Device connections",
                    onClick = onBluetoothSettingsClick,
                    color = SonicCoreColors.Cyan
                )
                QuickAccessButton(
                    icon = "🔋",
                    title = "Battery",
                    description = "Optimization settings",
                    onClick = onBatteryOptimizationClick,
                    color = SonicCoreColors.Warning
                )
                QuickAccessButton(
                    icon = "ℹ️",
                    title = "App Info",
                    description = "Permissions & details",
                    onClick = onAppInfoClick,
                    color = SonicCoreColors.Cyan
                )
            }
        }
    }
}

@Composable
private fun RowScope.QuickAccessButton(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    color: Color
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.ElevatedSurface.copy(alpha = 0.7f))
            .border(1.dp, SonicCoreColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open $title settings" }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 16.sp)
            Text(
                title,
                fontSize = 11.sp,
                color = SonicCoreColors.PrimaryText,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                fontSize = 9.sp,
                color = SonicCoreColors.SecondaryText
            )
        }
    }
}

// Helper function to get permission status
private fun getPermissionStatus(context: Context, permission: String): String {
    return when (ContextCompat.checkSelfPermission(context, permission)) {
        PackageManager.PERMISSION_GRANTED -> "GRANTED"
        PackageManager.PERMISSION_DENIED -> {
            if (permission == Manifest.permission.POST_NOTIFICATIONS ||
                permission == Manifest.permission.BLUETOOTH_CONNECT) {
                "BLOCKED"
            } else {
                "DENIED"
            }
        }
        else -> "UNKNOWN"
    }
}
