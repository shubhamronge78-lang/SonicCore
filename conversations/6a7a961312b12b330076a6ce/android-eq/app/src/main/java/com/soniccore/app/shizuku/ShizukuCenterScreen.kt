package com.soniccore.app.shizuku

import com.soniccore.app.ui.design.GlassRadius
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.soniccore.app.ui.design.LiquidGlassDepth
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.PlatformInfoRow
import com.soniccore.app.ui.design.PlatformSectionLabel
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.StatusPill
import rikka.shizuku.Shizuku

/**
 * Shizuku Center: the real connection lifecycle (binder received / dead,
 * authorization request and result), setup guidance, the operation registry with
 * its four-state outcome, and the read-only IPC diagnostic.
 *
 * Nothing here changes system state: the only executed operation is a read-only
 * binder probe. Operations SonicCore does not implement say so explicitly.
 */
@Composable
fun ShizukuCenterScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var environment by remember { mutableStateOf(ShizukuBridge.environment(context)) }
    var lastCheckLabel by remember { mutableStateOf("Checked on open") }

    fun refresh(trigger: String) {
        environment = ShizukuBridge.environment(context)
        lastCheckLabel = "Last check: $trigger"
        OperationLog.record(
            category = "Shizuku",
            message = "State probe: ${environment.state.label}",
            result = probeResultFor(environment.state)
        )
    }

    // Live binder lifecycle: Shizuku can be started, restarted or killed while this
    // screen is open, so the state follows the real client instead of a snapshot.
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener {
            environment = ShizukuBridge.environment(context)
            lastCheckLabel = "Last check: binder received"
        }
        val dead = Shizuku.OnBinderDeadListener {
            environment = ShizukuBridge.environment(context)
            lastCheckLabel = "Last check: binder died"
            OperationLog.record("Shizuku", "Binder died — connection lost", OperationResult.FAILED)
        }
        val permission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == ShizukuBridge.PERMISSION_REQUEST_CODE) {
                val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
                environment = ShizukuBridge.environment(context)
                lastCheckLabel = "Last check: authorization result"
                OperationLog.record(
                    category = "Shizuku",
                    message = if (granted) "Authorization granted" else "Authorization denied",
                    result = if (granted) OperationResult.SUCCESS else OperationResult.UNAVAILABLE
                )
            }
        }
        ShizukuBridge.addBinderReceivedListener(received)
        ShizukuBridge.addBinderDeadListener(dead)
        ShizukuBridge.addPermissionResultListener(permission)
        onDispose {
            ShizukuBridge.removeBinderReceivedListener(received)
            ShizukuBridge.removeBinderDeadListener(dead)
            ShizukuBridge.removePermissionResultListener(permission)
        }
    }

    val state = environment.state
    val operations = remember(environment) { ShizukuOperations.evaluate(environment) }
    val installName = ShizukuBridge.installedPackage(context) ?: "Not installed"

    // The launcher shell owns vertical scrolling; see DeviceDiagnosticsScreen.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Connection status
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformSectionLabel("Shizuku connection")
                StatusPill(state.label, shizukuColor(state))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                state.description,
                fontSize = 12.sp,
                color = SonicCoreColors.PrimaryText
            )
            Spacer(Modifier.height(8.dp))
            PlatformInfoRow("Install", installName)
            PlatformInfoRow(
                "Service",
                when (state) {
                    ShizukuState.CONNECTED -> "Running"
                    ShizukuState.PERMISSION_REQUIRED -> "Running"
                    ShizukuState.DISCONNECTED -> "Connection lost"
                    else -> "Not running"
                }
            )
            PlatformInfoRow(
                "Authorization",
                when (state) {
                    ShizukuState.CONNECTED -> "Granted to SonicCore"
                    ShizukuState.PERMISSION_REQUIRED ->
                        if (environment.permissionDeniedPermanently) "Denied — re-enable in Manager" else "Required"
                    else -> "Not requested"
                }
            )
            if (state == ShizukuState.CONNECTED) {
                PlatformInfoRow("Privilege", environment.privilege.label)
                PlatformInfoRow(
                    "Server API",
                    if (environment.serverVersion >= 0) "${environment.serverVersion}" else "Not reported"
                )
                environment.selinuxContext?.takeIf { it.isNotBlank() }?.let { seLinux ->
                    PlatformInfoRow("SELinux", seLinux)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                lastCheckLabel,
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))
            // Three actions on a narrow screen: the row scrolls sideways, matching the
            // action rows elsewhere in the app, so nothing is clipped at 400 dp.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { refresh("manual refresh") },
                    modifier = Modifier.semantics { contentDescription = "Re-check Shizuku status" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SonicCoreColors.Cyan.copy(alpha = 0.20f),
                        contentColor = SonicCoreColors.Cyan
                    ),
                    shape = RoundedCornerShape(GlassRadius.chip)
                ) {
                    Text("REFRESH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (state == ShizukuState.PERMISSION_REQUIRED) {
                    Button(
                        onClick = {
                            if (ShizukuBridge.requestPermission()) {
                                lastCheckLabel = "Last check: authorization requested"
                                OperationLog.record("Shizuku", "Authorization requested", OperationResult.INFO)
                            } else {
                                OperationLog.record("Shizuku", "Authorization request unavailable", OperationResult.UNAVAILABLE)
                                Toast.makeText(context, "Start the Shizuku service first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Request Shizuku authorization" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonicCoreColors.Warning.copy(alpha = 0.22f),
                            contentColor = SonicCoreColors.Warning
                        ),
                        shape = RoundedCornerShape(GlassRadius.chip)
                    ) {
                        Text("AUTHORIZE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (state != ShizukuState.NOT_INSTALLED) {
                    Button(
                        onClick = {
                            val intent = ShizukuBridge.getLaunchManagerIntent(context)
                            if (intent == null) {
                                OperationLog.record("Shizuku", "Manager launch intent unresolved", OperationResult.UNAVAILABLE)
                                Toast.makeText(context, "Shizuku Manager could not be opened", Toast.LENGTH_SHORT).show()
                            } else {
                                try {
                                    context.startActivity(intent)
                                    OperationLog.record("Shizuku", "Opened Shizuku Manager", OperationResult.SUCCESS)
                                } catch (_: Throwable) {
                                    OperationLog.record("Shizuku", "Manager launch failed", OperationResult.FAILED)
                                    Toast.makeText(context, "Shizuku Manager could not be opened", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Open Shizuku Manager" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SonicCoreColors.Violet.copy(alpha = 0.22f),
                            contentColor = SonicCoreColors.Violet
                        ),
                        shape = RoundedCornerShape(GlassRadius.chip)
                    ) {
                        Text("OPEN MANAGER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "SonicCore works fully without Shizuku. Elevated tools are optional and never required for audio capture or DSP.",
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
        }

        // 2. Read-only IPC diagnostics (the one elevated operation that really runs)
        ShizukuDiagnosticsCard(environment = environment)

        // 3. Setup guidance
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("Setup guide", color = SonicCoreColors.Violet)
            Spacer(Modifier.height(8.dp))
            ShizukuOperations.setupSteps(state).forEach { step ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        when {
                            step.done -> "✓"
                            step.blocked -> "•"
                            else -> "${step.index}."
                        },
                        fontSize = 12.sp,
                        color = when {
                            step.done -> SonicCoreColors.Success
                            step.blocked -> SonicCoreColors.SecondaryText
                            else -> SonicCoreColors.Cyan
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            step.title,
                            fontSize = 12.sp,
                            color = SonicCoreColors.PrimaryText,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            step.detail,
                            fontSize = 10.sp,
                            color = SonicCoreColors.SecondaryText,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // 4. Operation registry
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
            PlatformSectionLabel("Operation registry")
            Spacer(Modifier.height(4.dp))
            Text(
                "Every entry reports AVAILABLE, BLOCKED, UNSUPPORTED or NOT IMPLEMENTED. Only operations with a real implementation ever become runnable; the rest name the exact reason.",
                fontSize = 11.sp,
                color = SonicCoreColors.SecondaryText
            )
            Spacer(Modifier.height(10.dp))

            ShizukuGroup.values().forEach { group ->
                val groupOps = operations.filter { it.operation.group == group }
                if (groupOps.isNotEmpty()) {
                    Text(
                        group.label.uppercase(),
                        fontSize = 10.sp,
                        color = SonicCoreColors.Violet,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                    groupOps.forEach { status ->
                        OperationRow(status)
                    }
                }
            }
        }

        // 5. Safe error reporting
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), depth = LiquidGlassDepth.Inset) {
            PlatformSectionLabel("Error reporting", color = SonicCoreColors.Warning)
            Spacer(Modifier.height(6.dp))
            val shizukuLog = OperationLog.snapshot().filter { it.category == "Shizuku" }.take(5)
            if (shizukuLog.isEmpty()) {
                Text(
                    "No Shizuku operation has been recorded this session.",
                    fontSize = 11.sp,
                    color = SonicCoreColors.SecondaryText
                )
            } else {
                shizukuLog.forEach { entry ->
                    Text(
                        "${entry.timeLabel} · ${entry.result.label} · ${entry.message}",
                        fontSize = 10.sp,
                        color = when (entry.result) {
                            OperationResult.SUCCESS -> SonicCoreColors.Success
                            OperationResult.FAILED -> SonicCoreColors.Error
                            else -> SonicCoreColors.SecondaryText
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Errors are logged locally for this session only. Export a diagnostic report from the Diagnostics screen to share them.",
                fontSize = 10.sp,
                color = SonicCoreColors.SecondaryText
            )
        }
    }
}

@Composable
private fun OperationRow(status: ShizukuOperationStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                status.operation.title,
                fontSize = 12.sp,
                color = if (status.outcome.isRunnable) SonicCoreColors.PrimaryText else SonicCoreColors.SecondaryText,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            StatusPill(status.outcome.label, outcomeColor(status.outcome))
        }
        Text(
            status.operation.description,
            fontSize = 10.sp,
            color = SonicCoreColors.SecondaryText,
            lineHeight = 14.sp
        )
        Text(
            "${status.operation.access.label} · " +
                if (status.operation.readOnly) "read-only" else "changes system state",
            fontSize = 10.sp,
            color = SonicCoreColors.Violet,
            lineHeight = 14.sp
        )
        Text(
            status.reason,
            fontSize = 10.sp,
            color = outcomeColor(status.outcome),
            lineHeight = 14.sp
        )
    }
}

private fun probeResultFor(state: ShizukuState): OperationResult = when (state) {
    ShizukuState.CONNECTED -> OperationResult.SUCCESS
    ShizukuState.NOT_INSTALLED,
    ShizukuState.NOT_RUNNING,
    ShizukuState.DISCONNECTED,
    ShizukuState.PERMISSION_REQUIRED -> OperationResult.UNAVAILABLE
    ShizukuState.UNSUPPORTED -> OperationResult.FAILED
}

private fun shizukuColor(state: ShizukuState): Color = when (state) {
    ShizukuState.CONNECTED -> SonicCoreColors.Success
    ShizukuState.PERMISSION_REQUIRED -> SonicCoreColors.Warning
    ShizukuState.NOT_RUNNING -> SonicCoreColors.Warning
    ShizukuState.DISCONNECTED -> SonicCoreColors.Error
    ShizukuState.NOT_INSTALLED -> SonicCoreColors.SecondaryText
    ShizukuState.UNSUPPORTED -> SonicCoreColors.Error
}

private fun outcomeColor(outcome: ShizukuOutcome): Color = when (outcome) {
    ShizukuOutcome.AVAILABLE -> SonicCoreColors.Success
    ShizukuOutcome.BLOCKED -> SonicCoreColors.Warning
    ShizukuOutcome.UNSUPPORTED -> SonicCoreColors.Error
    ShizukuOutcome.NOT_IMPLEMENTED -> SonicCoreColors.Violet
}
