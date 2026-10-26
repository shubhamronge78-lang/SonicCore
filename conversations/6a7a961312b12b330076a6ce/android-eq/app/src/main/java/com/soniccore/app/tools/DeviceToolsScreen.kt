package com.soniccore.app.tools

import com.soniccore.app.ui.design.GlassRadius
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.soniccore.app.shizuku.ShizukuBridge
import com.soniccore.app.shizuku.ShizukuState
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun DeviceToolsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var audioMode by remember { mutableStateOf(DeviceToolsManager.getAudioModeName(context)) }
    var ringerMode by remember { mutableStateOf(DeviceToolsManager.getRingerModeName(context)) }
    var focusProbeResult by remember { mutableStateOf("Idle (Tap probe to test)") }
    var shizukuState by remember { mutableStateOf(ShizukuBridge.checkState(context)) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Shizuku Privileged Bridge Status Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "SHIZUKU PRIVILEGED BRIDGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                val badgeColor = when (shizukuState) {
                    ShizukuState.CONNECTED -> Color(0xFF00E676)
                    ShizukuState.PERMISSION_REQUIRED -> Color(0xFFFFAB00)
                    ShizukuState.NOT_RUNNING -> Color(0xFFFF9100)
                    ShizukuState.DISCONNECTED -> Color(0xFFFF9100)
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
            Spacer(Modifier.height(6.dp))
            Text(
                shizukuState.description,
                style = MaterialTheme.typography.bodySmall,
                color = SonicCoreColors.PrimaryText,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Standard mode fallback is active. SonicCore audio capture and DSP operate independently without root or elevated privileges.",
                style = MaterialTheme.typography.bodySmall,
                color = SonicCoreColors.SecondaryText,
                fontSize = 11.sp
            )
            if (shizukuState != ShizukuState.NOT_INSTALLED && shizukuState != ShizukuState.CONNECTED) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val intent = ShizukuBridge.getLaunchManagerIntent(context)
                        if (intent != null) {
                            try {
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                                Toast.makeText(context, "Could not open Shizuku Manager", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Open Shizuku Manager app" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                        contentColor = SonicCoreColors.Cyan
                    ),
                    shape = RoundedCornerShape(GlassRadius.chip)
                ) {
                    Text("Open Shizuku Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Audio Mode & Public Focus Probe Card
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SYSTEM AUDIO FOCUS & MODES",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Audio Mode", fontSize = 12.sp, color = SonicCoreColors.SecondaryText)
                Text(audioMode, fontSize = 12.sp, color = SonicCoreColors.PrimaryText, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ringer Mode", fontSize = 12.sp, color = SonicCoreColors.SecondaryText)
                Text(ringerMode, fontSize = 12.sp, color = SonicCoreColors.PrimaryText, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlassRadius.tile))
                    .background(SonicCoreColors.ElevatedSurface)
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio Focus Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SonicCoreColors.PrimaryText)
                        Button(
                            onClick = {
                                focusProbeResult = DeviceToolsManager.probeAudioFocus(context)
                            },
                            modifier = Modifier.semantics { contentDescription = "Probe system audio focus handling" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SonicCoreColors.Violet.copy(alpha = 0.30f),
                                contentColor = SonicCoreColors.Violet
                            ),
                            shape = RoundedCornerShape(GlassRadius.chip)
                        ) {
                            Text("PROBE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        focusProbeResult,
                        fontSize = 11.sp,
                        color = SonicCoreColors.Cyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Supported System Settings Shortcuts
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SYSTEM SHORTCUTS & CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Direct Android system settings shortcuts with verified intent resolution.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))

            DeviceToolsManager.SHORTCUTS.forEach { shortcut ->
                ShortcutItem(
                    shortcut = shortcut,
                    onClick = {
                        val ok = DeviceToolsManager.launchShortcut(context, shortcut)
                        if (!ok) {
                            Toast.makeText(context, "Shortcut unavailable on this Android build", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ShortcutItem(
    shortcut: ToolShortcut,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(SonicCoreColors.ElevatedSurface.copy(alpha = 0.7f))
            .border(1.dp, SonicCoreColors.Border, RoundedCornerShape(GlassRadius.tile))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Launch ${shortcut.title}" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                shortcut.title,
                fontSize = 13.sp,
                color = SonicCoreColors.PrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                shortcut.subtitle,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Requirement: ${shortcut.permissionRequirement}",
                fontSize = 9.sp,
                color = SonicCoreColors.Cyan
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("↗", fontSize = 16.sp, color = SonicCoreColors.Cyan, fontWeight = FontWeight.Bold)
    }
}
