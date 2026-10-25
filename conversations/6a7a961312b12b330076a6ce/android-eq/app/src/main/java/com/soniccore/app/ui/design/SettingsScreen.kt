package com.soniccore.app.ui.design

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.provider.Settings
import android.os.Build
import android.net.Uri
import com.soniccore.app.ui.navigation.SonicCoreDestination
import com.soniccore.app.tools.ToolShortcut
import com.soniccore.app.tools.DeviceToolsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onGlassIntensityChange: (String) -> Unit = {},
    onThemeChange: (String) -> Unit = {},
    currentGlassIntensity: String = "MEDIUM",
    currentTheme: String = "cyan_dream",
    onNavigate: (SonicCoreDestination) -> Unit = {},
    onSettingsChanged: () -> Unit = {},
    onToolClick: (ToolShortcut) -> Unit = {}
) {
    val context = LocalContext.current
    val glassIntensities = listOf("LOW", "MEDIUM", "HIGH")
    val themes = listOf("cyan_dream", "violet_moon", "emerald_grove", "amber_sunset")
    
    var selectedGlassIntensity by remember { mutableStateOf(currentGlassIntensity) }
    var selectedTheme by remember { mutableStateOf(currentTheme) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SonicCoreColors.Background),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "APP SETTINGS",
                    style = MaterialTheme.typography.headlineSmall,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Customize SonicCore appearance and behavior",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 12.sp
                )
            }
        }
        
        // Glass Intensity Section
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Floating
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Glass Intensity",
                    style = MaterialTheme.typography.titleMedium,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "Control the translucency and blur effect of glass panels",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                glassIntensities.forEach { intensity ->
                    GlassIntensityOption(
                        intensity = intensity,
                        isSelected = intensity == selectedGlassIntensity,
                        onSelected = {
                            selectedGlassIntensity = intensity
                            onGlassIntensityChange(intensity)
                            onSettingsChanged()
                        }
                    )
                }
            }
        }
        
        // Theme Section
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Floating
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Color Theme",
                    style = MaterialTheme.typography.titleMedium,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "Choose your preferred color palette for the interface",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                themes.forEach { theme ->
                    ThemeOption(
                        theme = theme,
                        isSelected = theme == selectedTheme,
                        onSelected = {
                            selectedTheme = theme
                            onThemeChange(theme)
                            onSettingsChanged()
                        }
                    )
                }
            }
        }
        
        // Sessions & Reports Section
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Floating
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Sessions & Reports",
                    style = MaterialTheme.typography.titleMedium,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "View session history, performance reports, and export diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Navigate to Sessions section
                SystemSettingButton(
                    title = "Sessions",
                    subtitle = "View session history and diagnostic reports",
                    icon = "▤",
                    onClick = {
                        try {
                            onNavigate(SonicCoreDestination.SESSIONS)
                        } catch (_: Exception) { }
                    }
                )
            }
        }
        
        // System Settings Section
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Floating
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "System Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "Access Android system audio and display settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                // System Sound & Vibration
                SystemSettingButton(
                    title = "Sound & Vibration",
                    subtitle = "Media volume, ring, and vibration controls",
                    icon = "🔊",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                        } catch (_: Exception) { }
                    }
                )
                
                // Bluetooth Settings
                SystemSettingButton(
                    title = "Bluetooth",
                    subtitle = "Connect and manage wireless devices",
                    icon = "📱",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (_: Exception) { }
                    }
                )
                
                // Battery Optimization
                SystemSettingButton(
                    title = "Battery Optimization",
                    subtitle = "Prevent Android from killing the EQ service",
                    icon = "🔋",
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= 23) {
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    context.startActivity(this)
                                }
                            } else {
                                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                            }
                        } catch (_: Exception) { }
                    }
                )
                
                // App Info
                SystemSettingButton(
                    title = "App Information",
                    subtitle = "Version, permissions, and app details",
                    icon = "ℹ️",
                    onClick = {
                        try {
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            ).also { intent ->
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) { }
                    }
                )
            }
        }
        
        // Privacy & Security Section
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Floating
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Privacy & Security",
                    style = MaterialTheme.typography.titleMedium,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    "Manage app permissions and privacy settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Navigate to Permissions & Privacy Center section
                SystemSettingButton(
                    title = "Permissions & Privacy",
                    subtitle = "Real permission status with recovery and explanations",
                    icon = "🔒",
                    onClick = {
                        try {
                            onNavigate(SonicCoreDestination.CAPABILITIES)
                        } catch (_: Exception) { }
                    }
                )
            }
        }
        
        // Device Tools Section - System shortcuts
        DeviceToolsSection(
            modifier = Modifier.fillMaxWidth(),
            onToolClick = { onToolClick(it) },
            onNavigate = { onNavigate(it) }
        )
        
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GlassIntensityOption(
    intensity: String,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val backgroundColor = if (isSelected) 
        SonicCoreColors.Cyan.copy(alpha = 0.20f) 
    else 
        SonicCoreColors.ElevatedSurface
    
    val borderColor = if (isSelected) 
        SonicCoreColors.Cyan 
    else 
        SonicCoreColors.Border
    
    val textColor = if (isSelected) 
        SonicCoreColors.Cyan 
    else 
        SonicCoreColors.PrimaryText
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelected)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (intensity) {
                    "LOW" -> "Low Glass (Clear)"
                    "MEDIUM" -> "Medium Glass (Balanced)"
                    "HIGH" -> "High Glass (Translucent)"
                    else -> intensity
                },
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(borderColor.copy(alpha = 0.20f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = intensity,
                    color = borderColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    theme: String,
    isSelected: Boolean,
    onSelected: () -> Unit
) {
    val backgroundColor = if (isSelected) 
        SonicCoreColors.Violet.copy(alpha = 0.15f) 
    else 
        SonicCoreColors.ElevatedSurface
    
    val borderColor = if (isSelected) 
        SonicCoreColors.Violet 
    else 
        SonicCoreColors.Border
    
    val textColor = if (isSelected) 
        SonicCoreColors.Violet 
    else 
        SonicCoreColors.PrimaryText
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelected)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (theme) {
                    "cyan_dream" -> "Cyan Dream"
                    "violet_moon" -> "Violet Moon"
                    "emerald_grove" -> "Emerald Grove"
                    "amber_sunset" -> "Amber Sunset"
                    else -> theme
                },
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
            
            if (isSelected) {
                Text(
                    "✓",
                    color = SonicCoreColors.Violet,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SystemSettingButton(
    title: String,
    subtitle: String,
    icon: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.ElevatedSurface)
            .border(1.dp, SonicCoreColors.Border.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics { contentDescription = "Open $title settings" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = SonicCoreColors.PrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = subtitle,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
            }
            
            Text(
                text = "→",
                color = SonicCoreColors.Cyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Device Tools Section - Comprehensive list of system audio shortcuts
@Composable
fun DeviceToolsSection(
    modifier: Modifier = Modifier,
    onToolClick: (ToolShortcut) -> Unit = {},
    onNavigate: (SonicCoreDestination) -> Unit = {}
) {
    val context = LocalContext.current
    val tools = DeviceToolsManager.SHORTCUTS
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tools.forEach { tool ->
            DeviceToolCard(
                icon = tool.title.split(" ").firstOrNull()?.let { 
                    if (it.isNotEmpty()) it.substring(0,1).uppercase() else ""
                } ?: "",
                title = tool.title,
                subtitle = tool.subtitle,
                permissionText = tool.permissionRequirement,
                onClick = { 
                    onToolClick(tool)
                    // Navigate to Device Tools section
                    onNavigate(SonicCoreDestination.DEVICE_TOOLS)
                }
            )
        }
    }
}

// Device Tool Card Component
@Composable
fun DeviceToolCard(
    icon: String,
    title: String,
    subtitle: String,
    permissionText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.ElevatedSurface)
            .border(1.dp, SonicCoreColors.Border.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics { contentDescription = "Open $title settings" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = SonicCoreColors.PrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = subtitle,
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 11.sp
                )
                
                Text(
                    text = "Permission: $permissionText",
                    color = SonicCoreColors.Cyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Text(
                text = "→",
                color = SonicCoreColors.Cyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}