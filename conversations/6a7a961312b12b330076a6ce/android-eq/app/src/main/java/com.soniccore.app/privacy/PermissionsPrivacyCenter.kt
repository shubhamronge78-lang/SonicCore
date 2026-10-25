package com.soniccore.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.diagnostics.OperationLog
import com.soniccore.app.diagnostics.OperationResult
import com.soniccore.app.permissions.PermissionCenter
import com.soniccore.app.permissions.PermissionEntry
import com.soniccore.app.permissions.PermissionState
import com.soniccore.app.permissions.PermissionTarget
import com.soniccore.app.ui.components.LiquidGlassDepth
import com.soniccore.app.ui.components.LiquidGlassSurface
import com.soniccore.app.ui.components.PlatformSectionLabel
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.components.StatusPill

/**
 * Permissions & Privacy Center.
 *
 * Every status shown here is read from the platform at render time — including
 * permissions SonicCore does not declare, which are reported as "Not declared"
 * rather than "Denied" so no settings link can promise a fix it cannot deliver.
 */
@Composable
fun PermissionsPrivacyCenter(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(PermissionCenter.probe(context)) }
    var refreshNote by remember { mutableStateOf("Read on open") }

    fun refresh() {
        entries = PermissionCenter.probe(context)
        val granted = entries.count { it.state == PermissionState.GRANTED }
        refreshNote = "Refreshed · $granted of ${entries.size} satisfied"
        OperationLog.record(
            category = "Permissions",
            message = "Re-read permission state ($granted/${entries.size} satisfied)",
            result = OperationResult.SUCCESS
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Summary
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Access summary")
                StatusPill(
                    "${entries.count { it.state == PermissionState.GRANTED }}/${entries.size}",
                    SonicCoreColors.Cyan
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Statuses are read live from Android. A normal permission is granted at install; a runtime permission needs your approval; a permission SonicCore never declares cannot be granted at all.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(refreshNote, fontSize = 10.sp, color = SonicCoreColors.SecondaryText)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { refresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Re-read permission status" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                    contentColor = SonicCoreColors.Cyan
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("REFRESH STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Optional entries first get their own note, then every entry renders the same way.
        entries.forEach { entry ->
            PermissionCard(
                entry = entry,
                onAction = {
                    val opened = openSettingsTarget(context, entry.target)
                    OperationLog.record(
                        category = "Permissions",
                        message = if (opened) "Opened settings for ${entry.title}" else "Settings unavailable for ${entry.title}",
                        result = if (opened) OperationResult.SUCCESS else OperationResult.UNAVAILABLE
                    )
                    if (!opened) {
                        Toast.makeText(context, "This settings screen is unavailable on this Android build", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Privacy explanation
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("Privacy", color = SonicCoreColors.Violet)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Captured audio is processed locally in the native DSP engine and written to the app's own AudioTrack.",
                "No audio is uploaded anywhere; the app declares no network permission for audio processing.",
                "Permission and Shizuku status is read on demand and never stored remotely.",
                "The operation log is session-scoped and in memory; it is written to a file only when you export a diagnostic report.",
                "System capture consent is granted by Android per session and cannot be remembered or bypassed by SonicCore."
            ).forEach { line ->
                Text(
                    "• $line",
                    fontSize = 11.sp,
                    color = SonicCoreColors.SecondaryText,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    entry: PermissionEntry,
    onAction: () -> Unit
) {
    LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    fontSize = 13.sp,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.optional) {
                    Text("Optional", fontSize = 9.sp, color = SonicCoreColors.SecondaryText)
                }
            }
            StatusPill(entry.state.label, stateColor(entry.state))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            entry.reason,
            fontSize = 11.sp,
            color = SonicCoreColors.SecondaryText,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            entry.detail,
            fontSize = 11.sp,
            color = SonicCoreColors.PrimaryText,
            lineHeight = 15.sp
        )
        if (entry.recovery != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                entry.recovery,
                fontSize = 10.sp,
                color = SonicCoreColors.Warning,
                lineHeight = 14.sp
            )
        }
        if (entry.target != PermissionTarget.NONE) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Open settings for ${entry.title}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SonicCoreColors.Violet.copy(alpha = 0.20f),
                    contentColor = SonicCoreColors.Violet
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("OPEN SETTINGS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun stateColor(state: PermissionState): Color = when (state) {
    PermissionState.GRANTED, PermissionState.SESSION_ACTIVE -> SonicCoreColors.Success
    PermissionState.DENIED -> SonicCoreColors.Error
    PermissionState.SESSION_PROMPTED -> SonicCoreColors.Cyan
    PermissionState.NOT_DECLARED -> SonicCoreColors.SecondaryText
    PermissionState.NOT_SUPPORTED -> SonicCoreColors.Warning
}

private fun openSettingsTarget(context: Context, target: PermissionTarget): Boolean {
    val intent = when (target) {
        PermissionTarget.APP_DETAILS -> Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        PermissionTarget.APP_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        PermissionTarget.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        PermissionTarget.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        PermissionTarget.BATTERY_OPTIMIZATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
        PermissionTarget.NONE -> return false
    }
    return try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}
