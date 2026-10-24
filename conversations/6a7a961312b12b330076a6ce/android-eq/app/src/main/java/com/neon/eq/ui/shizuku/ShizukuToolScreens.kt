package com.neon.eq.ui.shizuku

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neon.eq.diagnostics.OperationLog
import com.neon.eq.diagnostics.OperationResult
import com.neon.eq.shizuku.ShizukuBridge
import com.neon.eq.shizuku.ShizukuDiagnosticsCard
import com.neon.eq.shizuku.ShizukuEnvironment
import com.neon.eq.shizuku.ShizukuFailure
import com.neon.eq.shizuku.ShizukuGroup
import com.neon.eq.shizuku.ShizukuOperations
import com.neon.eq.shizuku.ShizukuOutcome
import com.neon.eq.shizuku.ShizukuState
import com.neon.eq.ui.category.CapabilityBadge
import com.neon.eq.ui.category.CapabilityLegend
import com.neon.eq.ui.category.LimitNote
import com.neon.eq.ui.category.SonicCoreCapability
import com.neon.eq.ui.category.SonicCoreDataRow
import com.neon.eq.ui.category.SonicCoreToolHost
import com.neon.eq.ui.category.toSonicCoreCapability
import com.neon.eq.ui.design.GlassCard
import com.neon.eq.ui.design.LiquidGlassSurface
import com.neon.eq.ui.design.SonicCoreColors
import com.neon.eq.ui.design.color
import com.neon.eq.ui.navigation.SonicCoreCategory
import rikka.shizuku.Shizuku

private val shizukuTone = SonicCoreCategory.SHIZUKU.tone

private fun stateCapability(state: ShizukuState): SonicCoreCapability = when (state) {
    ShizukuState.CONNECTED -> SonicCoreCapability.AVAILABLE
    ShizukuState.PERMISSION_REQUIRED -> SonicCoreCapability.NEEDS_PERMISSION
    ShizukuState.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
    ShizukuState.NOT_INSTALLED, ShizukuState.NOT_RUNNING, ShizukuState.DISCONNECTED -> SonicCoreCapability.BLOCKED
}

/** Live Shizuku state, refreshed by the real binder listeners. */
@Composable
private fun rememberShizukuState(): Pair<ShizukuState, ShizukuEnvironment?> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(runCatching { ShizukuBridge.checkState(context) }.getOrDefault(ShizukuState.NOT_INSTALLED)) }
    var environment by remember { mutableStateOf<ShizukuEnvironment?>(runCatching { ShizukuBridge.environment(context) }.getOrNull()) }

    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener {
            state = runCatching { ShizukuBridge.checkState(context) }.getOrDefault(state)
            environment = runCatching { ShizukuBridge.environment(context) }.getOrNull()
        }
        val dead = Shizuku.OnBinderDeadListener {
            state = runCatching { ShizukuBridge.checkState(context) }.getOrDefault(state)
            environment = runCatching { ShizukuBridge.environment(context) }.getOrNull()
        }
        runCatching {
            ShizukuBridge.addBinderReceivedListener(received)
            ShizukuBridge.addBinderDeadListener(dead)
        }
        onDispose {
            runCatching {
                ShizukuBridge.removeBinderReceivedListener(received)
                ShizukuBridge.removeBinderDeadListener(dead)
            }
        }
    }
    return state to environment
}

/** Shizuku → Connection Status. */
@Composable
fun ShizukuConnectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (state, environment) = rememberShizukuState()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(stateCapability(state), state.description)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("BINDER", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("State", state.label, stateCapability(state))
            SonicCoreDataRow(
                "Manager installed",
                if (runCatching { ShizukuBridge.isInstalled(context) }.getOrDefault(false)) "Yes" else "No",
                if (runCatching { ShizukuBridge.isInstalled(context) }.getOrDefault(false)) SonicCoreCapability.AVAILABLE else SonicCoreCapability.BLOCKED
            )
            SonicCoreDataRow(
                "Client protocol supported",
                if (runCatching { ShizukuBridge.isSupportedClient() }.getOrDefault(false)) "Yes" else "No",
                if (runCatching { ShizukuBridge.isSupportedClient() }.getOrDefault(false)) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNSUPPORTED
            )
            environment?.let { env ->
                SonicCoreDataRow("Server version", if (env.serverVersion > 0) "v${env.serverVersion}" else "Not reported",
                    if (env.serverVersion > 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
                SonicCoreDataRow("Server patch version", if (env.serverPatchVersion > 0) env.serverPatchVersion.toString() else "Not reported",
                    if (env.serverPatchVersion > 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
                SonicCoreDataRow("Server UID", if (env.serverUid >= 0) env.serverUid.toString() else "Not reported",
                    if (env.serverUid >= 0) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
                SonicCoreDataRow("Privilege level", env.privilege.label,
                    if (env.isElevated) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION)
                SonicCoreDataRow("SELinux context", env.selinuxContext ?: "Not reported",
                    if (env.selinuxContext != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN)
                SonicCoreDataRow("Authorization", if (env.permissionGranted) "Granted" else "Not granted",
                    if (env.permissionGranted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION)
                if (env.permissionDeniedPermanently) {
                    LimitNote("Shizuku authorization was permanently denied. It must be granted again from the Shizuku Manager app.", shizukuTone)
                }
            }
            LimitNote(
                "A running service alone is never reported as connected: SonicCore also verifies that the binder is alive in this process.",
                shizukuTone
            )
        }
    }
}

/** Shizuku → Setup Guide. */
@Composable
fun ShizukuSetupGuideScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (state, _) = rememberShizukuState()
    val steps = remember(state) { ShizukuOperations.setupSteps(state) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(stateCapability(state), "Setup progress is derived from the real client state, not from a stored flag.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SETUP PROGRESS", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            steps.forEach { step ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        if (step.done) "✓" else if (step.blocked) "⛔" else "${step.index}",
                        color = if (step.done) SonicCoreColors.Success else if (step.blocked) SonicCoreColors.Error else SonicCoreColors.Cyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(step.title, color = SonicCoreColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(step.detail, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall)
                    }
                    CapabilityBadge(
                        when {
                            step.done -> SonicCoreCapability.AVAILABLE
                            step.blocked -> SonicCoreCapability.BLOCKED
                            else -> SonicCoreCapability.NEEDS_PERMISSION
                        },
                        compact = true
                    )
                }
            }
            ShizukuManagerButton(context)
        }
        LimitNote(
            "SonicCore cannot install or start Shizuku: Android gives no third-party app that ability. Everything here links to the real setup.",
            shizukuTone
        )
    }
}

@Composable
private fun ShizukuManagerButton(context: android.content.Context) {
    val intent = remember { runCatching { ShizukuBridge.getLaunchManagerIntent(context) }.getOrNull() }
    Text(
        if (intent != null) "OPEN SHIZUKU MANAGER" else "SHIZUKU MANAGER NOT AVAILABLE",
        color = if (intent != null) SonicCoreColors.Cyan else SonicCoreColors.SecondaryText,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .clickable(enabled = intent != null) {
                runCatching { context.startActivity(intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(horizontal = 11.dp, vertical = 11.dp)
            .semantics { contentDescription = "Open the Shizuku Manager application" }
    )
}

/** Shizuku → Authorization & Permissions. */
@Composable
fun ShizukuAuthorizationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val (state, environment) = rememberShizukuState()
    var lastResult by remember { mutableStateOf<String?>(null) }
    val granted = environment?.permissionGranted == true

    DisposableEffect(Unit) {
        val listener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            lastResult = "Shizuku returned result code $grantResult for this request."
            val text = if (grantResult == 0) "Authorization granted." else "Authorization denied (code $grantResult)."
            OperationLog.record(
                category = "Shizuku",
                message = "Permission request: $text",
                result = if (grantResult == 0) OperationResult.SUCCESS else OperationResult.FAILED
            )
        }
        runCatching { ShizukuBridge.addPermissionResultListener(listener) }
        onDispose { runCatching { ShizukuBridge.removePermissionResultListener(listener) } }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (granted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION,
            if (granted) "SonicCore holds the Shizuku API permission." else "Shizuku authorization has not been granted to SonicCore."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("AUTHORIZATION", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Client state", state.label, stateCapability(state))
            SonicCoreDataRow("API permission", if (granted) "Granted" else "Not granted",
                if (granted) SonicCoreCapability.AVAILABLE else SonicCoreCapability.NEEDS_PERMISSION)
            SonicCoreDataRow(
                "Rationale required",
                if (runCatching { ShizukuBridge.shouldShowRequestPermissionRationale() }.getOrDefault(false)) "Yes" else "No",
                SonicCoreCapability.AVAILABLE
            )
            Text(
                if (granted) "REVOKE FROM SHIZUKU MANAGER" else "REQUEST AUTHORIZATION",
                color = SonicCoreColors.Cyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
                    .border(0.8.dp, SonicCoreColors.Cyan.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                    .clickable(enabled = !granted && state != ShizukuState.NOT_INSTALLED) {
                        val shown = runCatching { ShizukuBridge.requestPermission() }.getOrDefault(false)
                        OperationLog.record(
                            category = "Shizuku",
                            message = if (shown) "Authorization dialog requested" else "Authorization dialog not shown",
                            result = if (shown) OperationResult.INFO else OperationResult.UNAVAILABLE
                        )
                        lastResult = if (shown) "Request sent — waiting for the Shizuku result."
                        else "The request could not be sent from this state (${state.label})."
                    }
                    .padding(horizontal = 11.dp, vertical = 11.dp)
                    .semantics { contentDescription = "Request Shizuku authorization" }
            )
            lastResult?.let {
                Text(it, color = SonicCoreColors.SecondaryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        LimitNote(
            "Authorization can only be revoked inside the Shizuku Manager app; Android gives SonicCore no way to do it.",
            shizukuTone
        )
    }
}

/** Shizuku → Supported Operations. */
@Composable
fun ShizukuOperationsScreen(modifier: Modifier = Modifier) {
    val (state, environment) = rememberShizukuState()
    val env = environment
    val statuses = remember(env, state) {
        if (env != null) ShizukuOperations.evaluate(env)
        else ShizukuOperations.evaluate(state, android.os.Build.VERSION.SDK_INT)
    }
    val runnable = statuses.count { it.outcome.isRunnable }
    val notImplemented = statuses.count { it.outcome == ShizukuOutcome.NOT_IMPLEMENTED }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            if (runnable > 0) SonicCoreCapability.AVAILABLE else stateCapability(state),
            "$runnable of ${statuses.size} declared operations are runnable right now · $notImplemented not implemented."
        )
        if (env != null) {
            ShizukuDiagnosticsCard(environment = env)
        }
        ShizukuGroup.entries.forEach { group ->
            val groupStatuses = statuses.filter { it.operation.group == group }
            if (groupStatuses.isEmpty()) return@forEach
            LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
                Text(
                    group.label.uppercase(),
                    color = SonicCoreColors.SecondaryText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                groupStatuses.forEach { status ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                status.operation.title,
                                color = SonicCoreColors.PrimaryText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            CapabilityBadge(status.availability.toSonicCoreCapability(), compact = true)
                        }
                        Text(
                            status.reason,
                            color = SonicCoreColors.SecondaryText,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "access: ${status.operation.access.label} · ${if (status.operation.readOnly) "read-only" else "changes state"}",
                            color = shizukuTone.color(),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
        LimitNote(
            "An operation that SonicCore has not implemented is listed as NOT IMPLEMENTED. It is never presented as working.",
            shizukuTone
        )
    }
}

/** Shizuku → Results & History. */
@Composable
fun ShizukuHistoryScreen(host: SonicCoreToolHost, modifier: Modifier = Modifier) {
    var tick by remember { mutableStateOf(0) }
    val entries = remember(tick) { OperationLog.snapshot() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(SonicCoreCapability.AVAILABLE, "Operations recorded in this process, newest first.")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "RECORDED OPERATIONS (${entries.size})",
                color = SonicCoreColors.SecondaryText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            if (entries.isEmpty()) {
                Text(
                    "Nothing recorded yet. State probes, authorization requests and elevated operations appear here as they run.",
                    color = SonicCoreColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                entries.take(20).forEach { entry ->
                    SonicCoreDataRow(
                        label = "${entry.timeLabel} · ${entry.category}",
                        value = entry.message,
                        note = entry.result.label
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShizukuAction("CLEAR LOG", Modifier.weight(1f)) {
                    host.onClearOperationLog()
                    tick++
                }
                ShizukuAction("EXPORT LOG", Modifier.weight(1f)) {
                    host.onShareText("SonicCore operation log", OperationLog.format(OperationLog.snapshot()))
                }
            }
        }
        LimitNote(
            "The log is in-memory only: nothing is written to storage unless the user exports it.",
            shizukuTone
        )
    }
}

/** Shizuku → Troubleshooting. */
@Composable
fun ShizukuTroubleshootingScreen(modifier: Modifier = Modifier) {
    val (state, _) = rememberShizukuState()
    val steps = remember(state) { ShizukuOperations.setupSteps(state) }
    val blockedStep = steps.firstOrNull { !it.done }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CapabilityLegend(
            stateCapability(state),
            if (blockedStep == null) "Setup is complete on this device." else "Next required step: ${blockedStep.title}."
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("CURRENT STATE", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            SonicCoreDataRow("Shizuku", state.label, stateCapability(state), state.description)
            blockedStep?.let {
                SonicCoreDataRow("Blocked at", it.title, SonicCoreCapability.BLOCKED, it.detail)
            }
        }
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 14.dp, shape = RoundedCornerShape(18.dp)) {
            Text("FAILURE CODES", color = SonicCoreColors.SecondaryText, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            ShizukuFailure.entries.forEach { failure ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(failure.code, color = shizukuTone.color(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        failure.reason,
                        color = SonicCoreColors.SecondaryText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityBadge(failure.outcome.toCapability(), compact = true)
                }
            }
        }
        LimitNote(
            "Failures are classified from the real exception type and message. An unclassified failure is reported as unknown, never as available.",
            shizukuTone
        )
    }
}

private fun ShizukuOutcome.toCapability(): SonicCoreCapability = when (this) {
    ShizukuOutcome.AVAILABLE -> SonicCoreCapability.AVAILABLE
    ShizukuOutcome.BLOCKED -> SonicCoreCapability.BLOCKED
    ShizukuOutcome.UNSUPPORTED -> SonicCoreCapability.UNSUPPORTED
    ShizukuOutcome.NOT_IMPLEMENTED -> SonicCoreCapability.NOT_IMPLEMENTED
}

@Composable
private fun ShizukuAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = SonicCoreColors.Cyan,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SonicCoreColors.Cyan.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label }
    )
    Spacer(Modifier.height(2.dp))
}
