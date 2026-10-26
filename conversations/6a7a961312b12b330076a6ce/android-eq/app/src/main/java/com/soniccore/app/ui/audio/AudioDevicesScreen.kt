package com.soniccore.app.ui.audio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.soniccore.app.capture.AudioCapabilityManager
import com.soniccore.app.engine.OutputContract
import com.soniccore.app.engine.OutputRoutes
import com.soniccore.app.ui.category.CapabilityLegend
import com.soniccore.app.ui.category.SonicCoreCapability
import com.soniccore.app.ui.category.SonicCoreDataRow

/**
 * Audio → Output & Devices.
 *
 * Every output route Android reports to this app, with the capabilities the
 * device itself publishes: maximum sample rate, channel count and the media
 * route SonicCore would use. Nothing is inferred and nothing is forced —
 * SonicCore cannot reroute another app's audio, so this screen reports state
 * and points at the platform's own settings for changing the route.
 */
@Composable
fun AudioDevicesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val snapshot = remember(tick) { OutputRoutes.buildSnapshot(context) }
    val suggestedRate = remember(tick) { runCatching { AudioCapabilityManager.suggestedSampleRate(context) }.getOrNull() }
    val framesPerBuffer = remember(tick) { runCatching { AudioCapabilityManager.framesPerBufferLine(context) }.getOrDefault("Not readable") }
    val wired = remember(tick) { runCatching { AudioCapabilityManager.wiredConnected(context) }.getOrDefault(false) }
    val bluetooth = remember(tick) { runCatching { AudioCapabilityManager.btConnected(context) }.getOrDefault(false) }
    val usb = remember(tick) { runCatching { AudioCapabilityManager.usbConnected(context) }.getOrDefault(false) }

    val routes = snapshot.routes
    val routeCapability = if (routes.isEmpty()) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CapabilityLegend(
            routeCapability,
            if (routes.isEmpty())
                "Android reported no output device for this app process, so no route is claimed."
            else
                "Routes come from the platform's own output device list for this app."
        )

        AudioActionRow(
            listOf(
                AudioAction(
                    label = "REFRESH ROUTES",
                    onClick = { tick++ },
                    enabled = true
                )
            )
        )

        AudioSection(
            title = "Active output",
            description = "The media route SonicCore's processed stream is heading to."
        ) {
            SonicCoreDataRow(
                label = "Route",
                value = snapshot.activeName,
                capability = if (snapshot.activeKey == null) SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Route category",
                value = routes.firstOrNull { it.isActive }?.let { OutputContract.categoryDetail(it.category) }
                    ?: "Not resolved",
                capability = if (routes.any { it.isActive }) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            snapshot.fallbackNote?.let { note ->
                if (note.isNotBlank()) {
                    SonicCoreDataRow(
                        label = "Route note",
                        value = note,
                        capability = SonicCoreCapability.UNKNOWN
                    )
                }
            }
        }

        AudioSection(
            title = "Available routes (${routes.size})",
            description = "Every output device Android exposes to apps, with its published capabilities."
        ) {
            if (routes.isEmpty()) {
                SonicCoreDataRow(
                    label = "Output devices",
                    value = "None reported",
                    note = "Connect a headset or start playback and refresh to see the routes Android publishes.",
                    capability = SonicCoreCapability.UNKNOWN
                )
            }
            routes.forEach { route ->
                val name = route.productName?.takeIf { it.isNotBlank() }
                    ?: OutputContract.fallbackName(route.key)
                val detail = buildString {
                    append(OutputContract.categoryDetail(route.category))
                    if (route.maxRate > 0) append(" · up to ${route.maxRate / 1000} kHz")
                    if (route.channels > 0) append(" · ${route.channels} ch")
                }
                SonicCoreDataRow(
                    label = name,
                    value = if (route.isActive) "Active" else "Available",
                    note = detail,
                    capability = SonicCoreCapability.AVAILABLE
                )
            }
        }

        AudioSection(
            title = "Connections",
            description = "Physical and wireless paths this device reports right now."
        ) {
            SonicCoreDataRow(
                label = "Bluetooth audio (A2DP)",
                value = if (bluetooth) "Connected" else "Not connected",
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Wired headphones",
                value = if (wired) "Connected" else "Not connected",
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "USB audio",
                value = if (usb) "Connected" else "Not connected",
                capability = SonicCoreCapability.AVAILABLE
            )
        }

        AudioSection(
            title = "Device capabilities",
            description = "What the platform publishes for this device. Anything it withholds stays unclaimed."
        ) {
            SonicCoreDataRow(
                label = "Suggested capture rate",
                value = suggestedRate?.let { "${it / 1000} kHz" } ?: "Not readable",
                capability = if (suggestedRate != null) SonicCoreCapability.AVAILABLE else SonicCoreCapability.UNKNOWN
            )
            SonicCoreDataRow(
                label = "Capture channels",
                value = AudioCapabilityManager.channelsLine(),
                capability = SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Frames per buffer",
                value = framesPerBuffer,
                capability = if (framesPerBuffer == "Not readable") SonicCoreCapability.UNKNOWN else SonicCoreCapability.AVAILABLE
            )
            SonicCoreDataRow(
                label = "Route forcing",
                value = "Not available",
                note = "An app process cannot move another app's stream. Change the route in Android's own audio settings.",
                capability = SonicCoreCapability.UNSUPPORTED
            )
        }

        AudioLimitNote(
            "SonicCore reports only the devices Android lists for it. A route the platform does not publish — " +
                "or one another app has claimed — is never shown as available."
        )
    }
}
