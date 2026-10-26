package com.soniccore.app.capabilities

import com.soniccore.app.ui.design.GlassRadius
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.SonicCoreColors

@Composable
fun CapabilityRegistryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(CapabilityRegistry.evaluateAll(context)) }
    var selectedCategory by remember { mutableStateOf<CapabilityCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = items.filter { item ->
        val matchesCategory = selectedCategory == null || item.category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.summary.contains(searchQuery, ignoreCase = true) ||
                item.technicalDetails.contains(searchQuery, ignoreCase = true) ||
                item.restrictionsAndNotes.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val supportedCount = items.count { it.state == SupportState.SUPPORTED }
    val partialCount = items.count { it.state == SupportState.PARTIALLY_SUPPORTED || it.state == SupportState.RESTRICTED }
    val permCount = items.count { it.state == SupportState.PERMISSION_REQUIRED }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Overview & Stat Banner
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            Text(
                "SYSTEM CAPABILITY REGISTRY",
                style = MaterialTheme.typography.labelSmall,
                color = SonicCoreColors.Cyan,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Real hardware, OS, and permission capability probes without OEM bias.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatPill("SUPPORTED", "$supportedCount", Color(0xFF00E676), Modifier.weight(1f))
                StatPill("PARTIAL", "$partialCount", Color(0xFFFFAB00), Modifier.weight(1f))
                StatPill("PERMISSIONS", "$permCount", Color(0xFF00E5FF), Modifier.weight(1f))
            }
        }

        // 2. Search & Category Filter
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search system capabilities" },
                placeholder = { Text("Filter capabilities...", color = SonicCoreColors.SecondaryText, fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SonicCoreColors.Cyan,
                    unfocusedBorderColor = SonicCoreColors.Border,
                    focusedTextColor = SonicCoreColors.PrimaryText,
                    unfocusedTextColor = SonicCoreColors.PrimaryText
                ),
                shape = RoundedCornerShape(GlassRadius.tile)
            )

            Spacer(Modifier.height(10.dp))

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryChip(
                    title = "All (${items.size})",
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null }
                )
                CapabilityCategory.values().forEach { cat ->
                    val count = items.count { it.category == cat }
                    CategoryChip(
                        title = "${cat.displayName} ($count)",
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }
        }

        // 3. Capability Items List
        filteredItems.forEach { item ->
            CapabilityCard(
                item = item,
                onActionClick = {
                    if (item.actionableGuidance != null) {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Throwable) { }
                    }
                }
            )
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(GlassRadius.tile))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = SonicCoreColors.SecondaryText)
        }
    }
}

@Composable
private fun CategoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlassRadius.chip))
            .background(if (selected) SonicCoreColors.Cyan.copy(alpha = 0.22f) else SonicCoreColors.ElevatedSurface)
            .border(1.dp, if (selected) SonicCoreColors.Cyan else SonicCoreColors.Border, RoundedCornerShape(GlassRadius.chip))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            title,
            color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CapabilityCard(
    item: CapabilityItem,
    onActionClick: () -> Unit
) {
    val stateColor = when (item.state) {
        SupportState.SUPPORTED -> Color(0xFF00E676)
        SupportState.PARTIALLY_SUPPORTED -> Color(0xFFFFAB00)
        SupportState.PERMISSION_REQUIRED -> Color(0xFF00E5FF)
        SupportState.RESTRICTED -> Color(0xFFFF9100)
        SupportState.UNSUPPORTED -> Color(0xFFFF5252)
    }

    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.titleSmall,
                color = SonicCoreColors.PrimaryText,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(stateColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    item.state.label,
                    color = stateColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            item.summary,
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.PrimaryText,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(6.dp))
        Text(
            "API: ${item.technicalDetails}",
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.Cyan,
            fontSize = 11.sp
        )

        Spacer(Modifier.height(4.dp))
        Text(
            "Restrictions: ${item.restrictionsAndNotes}",
            style = MaterialTheme.typography.bodySmall,
            color = SonicCoreColors.SecondaryText,
            fontSize = 11.sp
        )

        if (item.actionableGuidance != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.actionableGuidance,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFFFAB00),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onActionClick,
                    modifier = Modifier.semantics { contentDescription = "Resolve requirement for ${item.name}" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                        contentColor = SonicCoreColors.Cyan
                    ),
                    shape = RoundedCornerShape(GlassRadius.chip)
                ) {
                    Text("Resolve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
