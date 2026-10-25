package com.soniccore.app.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.soniccore.app.capture.AudioCapabilityManager
import com.soniccore.app.dsp.NeonDsp
import com.soniccore.app.shizuku.ShizukuBridge
import com.soniccore.app.shizuku.ShizukuState

enum class CapabilityCategory(val displayName: String) {
    PLATFORM("Platform & System"),
    AUDIO_CAPTURE("Audio Playback Capture"),
    ROUTING_OUTPUT("Routing & Output"),
    DSP_ENGINE("Native DSP Engine"),
    PERMISSIONS("Runtime Permissions"),
    DEVICE_INTEGRATION("Device Integration")
}

enum class SupportState(val label: String) {
    SUPPORTED("Supported"),
    PARTIALLY_SUPPORTED("Partial"),
    UNSUPPORTED("Unsupported"),
    PERMISSION_REQUIRED("Permission Needed"),
    RESTRICTED("Restricted")
}

data class CapabilityItem(
    val id: String,
    val name: String,
    val category: CapabilityCategory,
    val state: SupportState,
    val summary: String,
    val technicalDetails: String,
    val restrictionsAndNotes: String,
    val actionableGuidance: String? = null
)

object CapabilityRegistry {

    fun evaluateAll(context: Context): List<CapabilityItem> {
        val list = mutableListOf<CapabilityItem>()
        val api = Build.VERSION.SDK_INT

        // 1. Android Platform Capture
        val captureSupported = api >= 29
        list.add(
            CapabilityItem(
                id = "cap_playback_capture",
                name = "AudioPlaybackCapture API",
                category = CapabilityCategory.AUDIO_CAPTURE,
                state = if (captureSupported) SupportState.SUPPORTED else SupportState.UNSUPPORTED,
                summary = if (captureSupported) "Supported on Android 10+ (API $api)" else "Requires Android 10+ (Current API $api)",
                technicalDetails = "Android AudioRecord AudioPlaybackCaptureConfiguration",
                restrictionsAndNotes = "Source applications can opt out using android:allowAudioPlaybackCapture=\"false\" or AudioAttributes.ALLOW_CAPTURE_BY_NONE. Voice calls, VoIP, and DRM-protected streams are exempt from capture by platform policy.",
                actionableGuidance = if (!captureSupported) "System audio capture is not possible below Android 10." else null
            )
        )

        // 2. MediaProjection Consent Service
        val mpState = AudioCapabilityManager.mediaProjection(context)
        list.add(
            CapabilityItem(
                id = "cap_media_projection",
                name = "MediaProjection Token Service",
                category = CapabilityCategory.AUDIO_CAPTURE,
                state = if (mpState.toString() == "SUPPORTED") SupportState.SUPPORTED else SupportState.UNSUPPORTED,
                summary = "System service for acquiring playback capture token",
                technicalDetails = "MediaProjectionManager system service",
                restrictionsAndNotes = "Requires explicit user consent via system prompt on each session startup. The token can be invalidated if the user stops screen/audio capture or system kills the projection.",
                actionableGuidance = "Accept the system prompt when starting System Capture."
            )
        )

        // 3. AudioRecord PCM Ingestion
        val recordPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val arState = AudioCapabilityManager.audioRecord()
        val recordSupport = if (!recordPerm) {
            SupportState.PERMISSION_REQUIRED
        } else if (arState.toString() == "SUPPORTED") {
            SupportState.SUPPORTED
        } else {
            SupportState.PARTIALLY_SUPPORTED
        }
        list.add(
            CapabilityItem(
                id = "cap_audio_record",
                name = "AudioRecord PCM Input",
                category = CapabilityCategory.AUDIO_CAPTURE,
                state = recordSupport,
                summary = if (recordPerm) "AudioRecord initialized and operational" else "RECORD_AUDIO permission is required",
                technicalDetails = "android.media.AudioRecord pull stream (16-bit PCM)",
                restrictionsAndNotes = "RECORD_AUDIO permission is mandated by Android framework even for internal playback capture. Pull reads must not block the capture thread.",
                actionableGuidance = if (!recordPerm) "Grant microphone/audio recording permission in App Info." else null
            )
        )

        // 4. AudioTrack Low-Latency Streaming
        val sr = AudioCapabilityManager.suggestedSampleRate(context)
        val atState = AudioCapabilityManager.audioTrack(sr)
        list.add(
            CapabilityItem(
                id = "cap_audio_track",
                name = "AudioTrack Output Streaming",
                category = CapabilityCategory.ROUTING_OUTPUT,
                state = if (atState.toString() == "SUPPORTED") SupportState.SUPPORTED else SupportState.UNSUPPORTED,
                summary = "Dynamic streaming output @ ${sr} Hz",
                technicalDetails = "android.media.AudioTrack streaming mode, PCM 16-bit stereo",
                restrictionsAndNotes = "AudioTrack writes must be tracked with write() return values to handle partial buffer writes and avoid underruns.",
                actionableGuidance = null
            )
        )

        // 5. High-Resolution Audio Output
        val at96 = AudioCapabilityManager.audioTrack(96000)
        list.add(
            CapabilityItem(
                id = "cap_high_res",
                name = "High-Res 96kHz Output",
                category = CapabilityCategory.ROUTING_OUTPUT,
                state = if (at96.toString() == "SUPPORTED") SupportState.SUPPORTED else SupportState.PARTIALLY_SUPPORTED,
                summary = if (at96.toString() == "SUPPORTED") "Device supports 96kHz playback path" else "Resampled to 44.1k/48k standard rates",
                technicalDetails = "AudioTrack sample rate support @ 96000 Hz",
                restrictionsAndNotes = "High-res DAC support depends on the attached audio output hardware (USB DAC vs built-in speaker).",
                actionableGuidance = null
            )
        )

        // 6. Native C++ NEON DSP Pipeline
        val dspAvail = NeonDsp.available
        list.add(
            CapabilityItem(
                id = "cap_native_dsp",
                name = "Native C++ DSP Pipeline",
                category = CapabilityCategory.DSP_ENGINE,
                state = if (dspAvail) SupportState.SUPPORTED else SupportState.UNSUPPORTED,
                summary = if (dspAvail) "C++ ARM NEON DSP engine active" else "Native library failed to load: ${NeonDsp.loadError}",
                technicalDetails = "libneondsp.so, 10-band graphic, biquad PEQ, soft limiter, compressor, stereo widener",
                restrictionsAndNotes = "Clamping safeguards active: NaN/Infinity protection, gain limits, subnormal/denormal flushing, 0dB unity guarantee.",
                actionableGuidance = if (!dspAvail) "Ensure device CPU ABI matches armeabi-v7a or arm64-v8a." else null
            )
        )

        // 7. Output Routing & Device Detection
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val hasBt = AudioCapabilityManager.btConnected(context)
        val hasWired = AudioCapabilityManager.wiredConnected(context)
        val hasUsb = AudioCapabilityManager.usbConnected(context)
        val routesDesc = mutableListOf("Speaker")
        if (hasBt) routesDesc.add("Bluetooth")
        if (hasWired) routesDesc.add("Wired")
        if (hasUsb) routesDesc.add("USB")

        list.add(
            CapabilityItem(
                id = "cap_output_routing",
                name = "Audio Device Routing",
                category = CapabilityCategory.ROUTING_OUTPUT,
                state = SupportState.SUPPORTED,
                summary = "Active detected routes: ${routesDesc.joinToString(", ")}",
                technicalDetails = "AudioManager.getDevices(GET_DEVICES_OUTPUTS)",
                restrictionsAndNotes = "Bluetooth codec (LDAC/aptX/AAC) details are restricted from public non-privileged APIs on standard Android.",
                actionableGuidance = null
            )
        )

        // 8. Bluetooth Connect Permission (API 31+)
        if (api >= 31) {
            val btPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            list.add(
                CapabilityItem(
                    id = "cap_bt_connect_perm",
                    name = "Bluetooth Connect Permission",
                    category = CapabilityCategory.PERMISSIONS,
                    state = if (btPerm) SupportState.SUPPORTED else SupportState.PERMISSION_REQUIRED,
                    summary = if (btPerm) "Granted (Bluetooth device details accessible)" else "Needed to query Bluetooth device names on Android 12+",
                    technicalDetails = "android.permission.BLUETOOTH_CONNECT (API 31+)",
                    restrictionsAndNotes = "Without this permission, Bluetooth device friendly names fall back to generic 'Bluetooth Audio'.",
                    actionableGuidance = if (!btPerm) "Grant Nearby Devices permission in App Info." else null
                )
            )
        }

        // 9. Post Notifications Permission (API 33+)
        if (api >= 33) {
            val notifPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            list.add(
                CapabilityItem(
                    id = "cap_notif_perm",
                    name = "Post Notifications Permission",
                    category = CapabilityCategory.PERMISSIONS,
                    state = if (notifPerm) SupportState.SUPPORTED else SupportState.PERMISSION_REQUIRED,
                    summary = if (notifPerm) "Granted (Foreground service notification displayed)" else "Required for foreground service on Android 13+",
                    technicalDetails = "android.permission.POST_NOTIFICATIONS (API 33+)",
                    restrictionsAndNotes = "Android 13+ requires notification permission for foreground services to display persistent controls.",
                    actionableGuidance = if (!notifPerm) "Enable notifications in App Info." else null
                )
            )
        }

        // 10. Thermal Throttling Monitoring (API 29+)
        val thermalSupported = api >= 29
        list.add(
            CapabilityItem(
                id = "cap_thermal_monitor",
                name = "Thermal Throttling Monitor",
                category = CapabilityCategory.PLATFORM,
                state = if (thermalSupported) SupportState.SUPPORTED else SupportState.UNSUPPORTED,
                summary = if (thermalSupported) "PowerManager thermal status listener supported" else "Requires Android 10+ (API 29)",
                technicalDetails = "PowerManager.getCurrentThermalStatus()",
                restrictionsAndNotes = "Monitors device CPU/GPU thermal load to predict audio buffer underruns during severe device heating.",
                actionableGuidance = null
            )
        )

        // 11. Battery Optimization Exemption
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBattery = if (api >= 23) {
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else true
        list.add(
            CapabilityItem(
                id = "cap_battery_exemption",
                name = "Background Battery Exemption",
                category = CapabilityCategory.DEVICE_INTEGRATION,
                state = if (isIgnoringBattery) SupportState.SUPPORTED else SupportState.PARTIALLY_SUPPORTED,
                summary = if (isIgnoringBattery) "Exempt from battery optimization (stable background EQ)" else "Optimization active; system may throttle background service",
                technicalDetails = "PowerManager.isIgnoringBatteryOptimizations",
                restrictionsAndNotes = "Aggressive OEM battery savers (e.g. MIUI, EMUI, ColorOS) may terminate background audio capture if not exempt.",
                actionableGuidance = if (!isIgnoringBattery) "Disable battery optimization in Device Tools or system settings." else null
            )
        )

        // 12. Shizuku Privileged Bridge
        val shizukuState = ShizukuBridge.checkState(context)
        val shizState = when (shizukuState) {
            ShizukuState.CONNECTED -> SupportState.SUPPORTED
            ShizukuState.PERMISSION_REQUIRED -> SupportState.PERMISSION_REQUIRED
            ShizukuState.NOT_RUNNING -> SupportState.PARTIALLY_SUPPORTED
            ShizukuState.DISCONNECTED -> SupportState.PARTIALLY_SUPPORTED
            ShizukuState.NOT_INSTALLED -> SupportState.UNSUPPORTED
            ShizukuState.UNSUPPORTED -> SupportState.UNSUPPORTED
        }
        list.add(
            CapabilityItem(
                id = "cap_shizuku_bridge",
                name = "Optional Shizuku IPC Bridge",
                category = CapabilityCategory.DEVICE_INTEGRATION,
                state = shizState,
                summary = shizukuState.description,
                technicalDetails = "moe.shizuku.privileged.api Binder IPC",
                restrictionsAndNotes = "Optional. Enables rootless elevated diagnostics and restricted audio queries. Standard SonicCore features operate completely without Shizuku.",
                actionableGuidance = when (shizukuState) {
                    ShizukuState.NOT_INSTALLED -> "Optional: Install Shizuku if elevated diagnostics are desired."
                    ShizukuState.NOT_RUNNING -> "Start Shizuku service via wireless debugging or adb."
                    ShizukuState.PERMISSION_REQUIRED -> "Authorize SonicCore in the Shizuku Manager app."
                    ShizukuState.DISCONNECTED -> "The connection was lost. Restart the Shizuku service, then refresh."
                    else -> null
                }
            )
        )

        // 13. Package Visibility (API 30+)
        list.add(
            CapabilityItem(
                id = "cap_package_visibility",
                name = "Audio App Visibility",
                category = CapabilityCategory.PLATFORM,
                state = SupportState.SUPPORTED,
                summary = "Explicit launcher & audio effects query configuration declared",
                technicalDetails = "AndroidManifest <queries> element for audio and media players",
                restrictionsAndNotes = "Android 11+ limits broad package scanning. SonicCore declares explicit intents for media apps and launcher activities.",
                actionableGuidance = null
            )
        )

        return list
    }
}
