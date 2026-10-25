package com.soniccore.app.shizuku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the one elevated operation SonicCore actually implements: a read-only
 * Shizuku IPC probe. The button is only enabled when the operation is AVAILABLE;
 * otherwise the card shows the eligibility verdict and its reason.
 *
 * The call is executed off the main thread with a hard timeout, and every failure
 * (binder dead, denied, timeout, unsupported call) is shown as the structured
 * result it is — never as a success.
 */
@Composable
fun ShizukuDiagnosticsCard(
    environment: ShizukuEnvironment,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operation = remember { ShizukuOperations.OPERATIONS.first { it.id == ShizukuDiagnostics.OPERATION_ID } }
    val status = remember(environment) { ShizukuOperationEngine.evaluate(operation, environment) }

    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShizukuResult?>(null) }

    LiquidGlassSurface(modifier = modifier.fillMaxWidth(), depth = LiquidGlassDepth.Floating) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformSectionLabel("Shizuku IPC diagnostics")
            StatusPill(status.outcome.label, outcomeTint(status.outcome))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Reads the server version, privilege level, SELinux context and binder latency. Read-only: no setting, permission or audio route is touched.",
            fontSize = 11.sp,
            color = SonicCoreColors.SecondaryText
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                running = true
                scope.launch {
                    val fresh = withContext(Dispatchers.IO) {
                        ShizukuDiagnostics.run(ShizukuBridge.environment(context))
                    }
                    result = fresh
                    running = false
                    OperationLog.record(
                        category = "Shizuku",
                        message = "${operation.title}: ${fresh.outcome.label} — ${fresh.reason}",
                        result = when (fresh.outcome) {
                            ShizukuOutcome.AVAILABLE -> OperationResult.SUCCESS
                            ShizukuOutcome.BLOCKED -> OperationResult.UNAVAILABLE
                            ShizukuOutcome.UNSUPPORTED,
                            ShizukuOutcome.NOT_IMPLEMENTED -> OperationResult.FAILED
                        }
                    )
                }
            },
            enabled = status.outcome == ShizukuOutcome.AVAILABLE && !running,
            modifier = Modifier.semantics { contentDescription = "Run read-only Shizuku diagnostics" },
            colors = ButtonDefaults.buttonColors(
                containerColor = SonicCoreColors.Success.copy(alpha = 0.20f),
                contentColor = SonicCoreColors.Success,
                disabledContainerColor = SonicCoreColors.ElevatedSurface,
                disabledContentColor = SonicCoreColors.SecondaryText
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                when {
                    running -> "RUNNING…"
                    status.outcome == ShizukuOutcome.AVAILABLE -> "RUN DIAGNOSTICS"
                    else -> "UNAVAILABLE"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (status.outcome != ShizukuOutcome.AVAILABLE) {
            Spacer(Modifier.height(6.dp))
            Text(
                status.reason,
                fontSize = 10.sp,
                color = outcomeTint(status.outcome),
                lineHeight = 14.sp
            )
        }

        result?.let { outcome ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Last result",
                    fontSize = 11.sp,
                    color = SonicCoreColors.PrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                StatusPill(outcome.outcome.label, outcomeTint(outcome.outcome))
            }
            outcome.failure?.let { failure ->
                Text(
                    "Error code: ${failure.code}",
                    fontSize = 10.sp,
                    color = SonicCoreColors.Error
                )
            }
            if (outcome.details.isEmpty()) {
                Text(
                    outcome.reason,
                    fontSize = 10.sp,
                    color = SonicCoreColors.SecondaryText,
                    lineHeight = 14.sp
                )
            } else {
                outcome.details.forEach { detail ->
                    PlatformInfoRow(detail.label, detail.value)
                }
                Text(
                    "Completed in ${outcome.elapsedMs} ms.",
                    fontSize = 10.sp,
                    color = SonicCoreColors.SecondaryText,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun outcomeTint(outcome: ShizukuOutcome) = when (outcome) {
    ShizukuOutcome.AVAILABLE -> SonicCoreColors.Success
    ShizukuOutcome.BLOCKED -> SonicCoreColors.Warning
    ShizukuOutcome.UNSUPPORTED -> SonicCoreColors.Error
    ShizukuOutcome.NOT_IMPLEMENTED -> SonicCoreColors.Violet
}
