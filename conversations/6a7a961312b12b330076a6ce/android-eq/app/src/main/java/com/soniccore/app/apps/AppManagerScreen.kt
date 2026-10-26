package com.soniccore.app.apps

import com.soniccore.app.ui.design.GlassRadius
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.soniccore.app.engine.Presets
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun AppManagerScreen(
    currentlyPlayingPackage: String?,
    perAppProfiles: Map<String, String>,
    onAssignProfile: (packageName: String, presetName: String) -> Unit,
    onRemoveProfile: (packageName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var apps by remember(currentlyPlayingPackage, perAppProfiles) {
        mutableStateOf(
            AppManagerHelper.getInstalledAudioApps(context, currentlyPlayingPackage, perAppProfiles)
        )
    }

    val filteredApps = apps.filter { app ->
        searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Overview Banner
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                "APP AUDIO MANAGER",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Manage detected audio players, package visibility, and per-app audio profiles.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Total Apps: ${apps.size}",
                    fontSize = 11.sp,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                Text("•", fontSize = 11.sp, color = SonicCoreColors.SecondaryText)
                Text(
                    "Profiles Assigned: ${perAppProfiles.size}",
                    fontSize = 11.sp,
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 2. Search
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search audio applications" },
                placeholder = { Text("Search by name or package...", color = SonicCoreColors.SecondaryText, fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SonicCoreColors.Cyan,
                    unfocusedBorderColor = SonicCoreColors.Border,
                    focusedTextColor = SonicCoreColors.PrimaryText,
                    unfocusedTextColor = SonicCoreColors.PrimaryText
                ),
                shape = RoundedCornerShape(GlassRadius.tile)
            )
        }

        // 3. App Cards List
        filteredApps.forEach { app ->
            AppCard(
                app = app,
                onAssignPreset = { preset ->
                    onAssignProfile(app.packageName, preset)
                },
                onRemovePreset = {
                    onRemoveProfile(app.packageName)
                },
                onLaunch = {
                    val ok = AppManagerHelper.launchApp(context, app.packageName)
                    if (!ok) {
                        Toast.makeText(context, "Cannot launch ${app.appName}", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenSettings = {
                    AppManagerHelper.openAppSettings(context, app.packageName)
                }
            )
        }
    }
}

@Composable
private fun AppCard(
    app: InstalledAudioApp,
    onAssignPreset: (String) -> Unit,
    onRemovePreset: () -> Unit,
    onLaunch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showPresetSelector by remember { mutableStateOf(false) }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Monogram icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.16f))
                    .border(1.dp, SonicCoreColors.Cyan.copy(alpha = 0.40f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.appName.firstOrNull()?.uppercase() ?: "?",
                    color = SonicCoreColors.Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.appName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SonicCoreColors.PrimaryText
                    )
                    if (app.isCurrentlyPlaying) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF00E676).copy(alpha = 0.20f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("PLAYING", color = Color(0xFF00E676), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    app.packageName,
                    fontSize = 10.sp,
                    color = SonicCoreColors.SecondaryText
                )
                Text(
                    "v${app.versionName} • Target SDK ${app.targetSdk}",
                    fontSize = 9.sp,
                    color = SonicCoreColors.Border
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Profile mapping row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("EQ Profile: ", fontSize = 11.sp, color = SonicCoreColors.SecondaryText)
                if (app.assignedPreset != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SonicCoreColors.Violet.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(app.assignedPreset, fontSize = 10.sp, color = SonicCoreColors.Violet, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("None (Default)", fontSize = 11.sp, color = SonicCoreColors.PrimaryText)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (app.assignedPreset != null) {
                    Button(
                        onClick = onRemovePreset,
                        modifier = Modifier.semantics { contentDescription = "Clear preset profile for ${app.appName}" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                            contentColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(GlassRadius.chip)
                    ) {
                        Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { showPresetSelector = !showPresetSelector },
                    modifier = Modifier.semantics { contentDescription = "Assign EQ preset to ${app.appName}" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                        contentColor = SonicCoreColors.Cyan
                    ),
                    shape = RoundedCornerShape(GlassRadius.chip)
                ) {
                    Text(if (showPresetSelector) "Close" else "Assign", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Preset selector horizontal pill list
        if (showPresetSelector) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Presets.BUILTIN_QUICK.forEach { (presetName, _) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(GlassRadius.chip))
                            .background(if (app.assignedPreset == presetName) SonicCoreColors.Cyan.copy(alpha = 0.30f) else SonicCoreColors.ElevatedSurface)
                            .border(1.dp, if (app.assignedPreset == presetName) SonicCoreColors.Cyan else SonicCoreColors.Border, RoundedCornerShape(GlassRadius.chip))
                            .clickable {
                                onAssignPreset(presetName)
                                showPresetSelector = false
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            presetName,
                            fontSize = 10.sp,
                            color = if (app.assignedPreset == presetName) SonicCoreColors.Cyan else SonicCoreColors.PrimaryText,
                            fontWeight = if (app.assignedPreset == presetName) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action links
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Launch ${app.appName}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.ElevatedSurface,
                    contentColor = SonicCoreColors.PrimaryText
                ),
                shape = RoundedCornerShape(GlassRadius.chip)
            ) {
                Text("Launch", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Open Android settings for ${app.appName}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.ElevatedSurface,
                    contentColor = SonicCoreColors.SecondaryText
                ),
                shape = RoundedCornerShape(GlassRadius.chip)
            ) {
                Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
