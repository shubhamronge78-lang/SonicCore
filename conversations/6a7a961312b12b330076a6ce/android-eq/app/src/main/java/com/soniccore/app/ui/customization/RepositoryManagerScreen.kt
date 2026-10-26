package com.soniccore.app.ui.customization

import com.soniccore.app.ui.design.GlassRadius
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.soniccore.app.repository.IntegrationStatus
import com.soniccore.app.repository.InventoryCategory
import com.soniccore.app.repository.LicenseCompatibility
import com.soniccore.app.repository.RepositoryEntry
import com.soniccore.app.repository.RepositoryInventory
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.PlatformInfoRow
import com.soniccore.app.ui.design.PlatformSectionLabel
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.StatusPill

/** Colour a status is reported in — green only for work that is actually verified in this tree. */
private fun statusColor(status: IntegrationStatus): Color = when (status) {
    IntegrationStatus.INTEGRATED -> SonicCoreColors.Success
    IntegrationStatus.READY_TO_INTEGRATE -> SonicCoreColors.Cyan
    IntegrationStatus.REFERENCE_ONLY -> SonicCoreColors.SecondaryText
    IntegrationStatus.REQUIRES_SHIZUKU -> SonicCoreColors.Violet
    IntegrationStatus.REQUIRES_ROOT -> SonicCoreColors.Warning
    IntegrationStatus.INCOMPATIBLE -> SonicCoreColors.Error
    IntegrationStatus.UNSUPPORTED -> SonicCoreColors.SecondaryText
}

private fun compatibilityColor(verdict: LicenseCompatibility): Color = when (verdict) {
    LicenseCompatibility.PERMISSIVE -> SonicCoreColors.Success
    LicenseCompatibility.WEAK_COPYLEFT -> SonicCoreColors.Warning
    LicenseCompatibility.COPYLEFT -> SonicCoreColors.Error
    LicenseCompatibility.PROPRIETARY -> SonicCoreColors.Error
    LicenseCompatibility.UNVERIFIED -> SonicCoreColors.Warning
}

private fun openOnGitHub(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Customization → Repository Manager.
 *
 * The searchable catalogue of every third-party project SonicCore audited, with
 * the licence verdict, the privilege it would need, how much of it is actually
 * used, and a link to the source. The statuses are the app's own vocabulary and
 * are never optimistic: `Integrated` appears only for the two projects whose
 * code or official client is in this repository and covered by tests.
 */
@Composable
fun RepositoryManagerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<InventoryCategory?>(null) }
    var status by remember { mutableStateOf<IntegrationStatus?>(null) }

    val visible = RepositoryInventory.search(query, category, status)
    val counts = RepositoryInventory.countByStatus().toMap()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── Inventory summary ───────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
            PlatformSectionLabel("Open-source repository inventory")
            Spacer(Modifier.height(6.dp))
            Text(
                "${RepositoryInventory.auditedCount} projects audited across the six categories — " +
                    "licence, privileges, maintenance and real feature coverage.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Audit date ${RepositoryInventory.AUDIT_DATE} · facts read from the ${RepositoryInventory.AUDIT_SOURCE}",
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InventoryStat(
                    "INTEGRATED",
                    "${counts[IntegrationStatus.INTEGRATED] ?: 0}",
                    SonicCoreColors.Success,
                    Modifier.weight(1f)
                )
                InventoryStat(
                    "READY",
                    "${counts[IntegrationStatus.READY_TO_INTEGRATE] ?: 0}",
                    SonicCoreColors.Cyan,
                    Modifier.weight(1f)
                )
                InventoryStat(
                    "REFERENCE",
                    "${counts[IntegrationStatus.REFERENCE_ONLY] ?: 0}",
                    SonicCoreColors.Violet,
                    Modifier.weight(1f)
                )
                InventoryStat(
                    "BLOCKED",
                    "${
                        (counts[IntegrationStatus.INCOMPATIBLE] ?: 0) +
                            (counts[IntegrationStatus.UNSUPPORTED] ?: 0) +
                            (counts[IntegrationStatus.REQUIRES_SHIZUKU] ?: 0) +
                            (counts[IntegrationStatus.REQUIRES_ROOT] ?: 0)
                    }",
                    SonicCoreColors.Error,
                    Modifier.weight(1f)
                )
            }
        }

        CapabilityLegend(
            SonicCoreCapability.AVAILABLE,
            "Searchable catalogue with category filters, licence verdicts, privilege requirements and source links. " +
                "Nothing is marked Integrated until its code is in this repository and the tests cover it."
        )

        // ── Search and filters ──────────────────────────────────────────────
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            depth = LiquidGlassDepth.Inset
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search audited open-source repositories" },
                placeholder = {
                    Text("Search repositories, capability or licence...", color = SonicCoreColors.SecondaryText, fontSize = 12.sp)
                },
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
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InventoryChip(
                    "All (${RepositoryInventory.auditedCount})",
                    selected = category == null,
                    onClick = { category = null }
                )
                InventoryCategory.entries.forEach { entry ->
                    val count = RepositoryInventory.forCategory(entry).size
                    InventoryChip(
                        "${entry.icon} ${entry.title} ($count)",
                        selected = category == entry,
                        onClick = { category = if (category == entry) null else entry }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                InventoryChip(
                    "Any status",
                    selected = status == null,
                    onClick = { status = null }
                )
                IntegrationStatus.entries.forEach { entry ->
                    InventoryChip(
                        "${entry.label} (${counts[entry] ?: 0})",
                        selected = status == entry,
                        onClick = { status = if (status == entry) null else entry }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "${visible.size} of ${RepositoryInventory.auditedCount} projects shown",
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
        }

        // ── Status legend ───────────────────────────────────────────────────
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("How a status is decided")
            Spacer(Modifier.height(8.dp))
            IntegrationStatus.entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    StatusPill(entry.label, statusColor(entry))
                    Text(
                        entry.detail,
                        fontSize = 11.sp,
                        color = SonicCoreColors.SecondaryText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── The catalogue ───────────────────────────────────────────────────
        if (visible.isEmpty()) {
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "No audited project matches that filter. Clear the search or pick another category.",
                    fontSize = 12.sp,
                    color = SonicCoreColors.SecondaryText
                )
            }
        }
        visible.forEach { entry ->
            RepositoryCard(entry = entry, onOpenSource = { openOnGitHub(context, entry.url) })
        }
    }
}

@Composable
private fun InventoryStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GlassRadius.tile))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(GlassRadius.tile))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = SonicCoreColors.SecondaryText)
        }
    }
}

@Composable
private fun InventoryChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(GlassRadius.chip))
            .background(if (selected) SonicCoreColors.Cyan.copy(alpha = 0.22f) else SonicCoreColors.ElevatedSurface)
            .border(1.dp, if (selected) SonicCoreColors.Cyan else SonicCoreColors.Border, RoundedCornerShape(GlassRadius.chip))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Filter repositories by $title" }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            title,
            color = if (selected) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun RepositoryCard(entry: RepositoryEntry, onOpenSource: () -> Unit) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${entry.category.icon} ${entry.category.title} · ${entry.name}",
                    fontSize = 10.sp,
                    color = SonicCoreColors.SecondaryText
                )
            }
            StatusPill(entry.status.label, statusColor(entry.status))
        }

        Spacer(Modifier.height(6.dp))
        Text(entry.capability, fontSize = 12.sp, color = SonicCoreColors.PrimaryText)
        Spacer(Modifier.height(4.dp))
        Text("SonicCore: ${entry.sonicCoreUse}", fontSize = 11.sp, color = SonicCoreColors.SecondaryText)

        Spacer(Modifier.height(8.dp))
        PlatformInfoRow(
            "Licence",
            "${entry.license} · ${entry.compatibility.label}",
            valueColor = compatibilityColor(entry.compatibility)
        )
        PlatformInfoRow("Privileges", entry.privilege.label)
        PlatformInfoRow("Coverage", entry.coverage.label)
        PlatformInfoRow(
            "Maintenance",
            "${entry.maintenance.label} · last push ${entry.lastPushed} · ${entry.stars} stars"
        )
        PlatformInfoRow("Android API", entry.apiNote)
        entry.derivedFrom?.let { PlatformInfoRow("Fork / relation", it) }
        RepositoryInventory.latestReleases[entry.name]?.let { PlatformInfoRow("Latest release", it) }

        Spacer(Modifier.height(6.dp))
        Text(
            entry.compatibility.summary,
            fontSize = 10.sp,
            color = compatibilityColor(entry.compatibility)
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.url,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Button(
                onClick = onOpenSource,
                modifier = Modifier.semantics { contentDescription = "Open ${entry.name} on GitHub" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                    contentColor = SonicCoreColors.Cyan
                ),
                shape = RoundedCornerShape(GlassRadius.chip)
            ) {
                Text("View source", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
