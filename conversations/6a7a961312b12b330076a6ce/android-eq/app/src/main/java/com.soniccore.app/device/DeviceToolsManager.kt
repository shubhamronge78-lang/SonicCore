package com.soniccore.app.tools

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class ToolShortcut(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val permissionRequirement: String,
    val intentBuilder: (Context) -> Intent?
)

object DeviceToolsManager {

    val SHORTCUTS: List<ToolShortcut> = listOf(
        ToolShortcut(
            id = "sound_settings",
            title = "Sound & Vibration Settings",
            subtitle = "System sound profiles, media volume, and vibration controls",
            category = "System Audio",
            permissionRequirement = "None (Public Intent)",
            intentBuilder = { Intent(Settings.ACTION_SOUND_SETTINGS) }
        ),
        ToolShortcut(
            id = "volume_panel",
            title = "System Volume Panel",
            subtitle = "Floating volume slider panel for media, ring, and alarms",
            category = "System Audio",
            permissionRequirement = "None (API 29+ panel intent)",
            intentBuilder = {
                if (Build.VERSION.SDK_INT >= 29) {
                    Intent(Settings.Panel.ACTION_VOLUME)
                } else {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                }
            }
        ),
        ToolShortcut(
            id = "system_equalizer",
            title = "System Audio Effect Panel",
            subtitle = "OEM built-in audio effects or Dolby Atmos / Dirac panels",
            category = "Audio Processing",
            permissionRequirement = "None (AudioEffect control intent)",
            intentBuilder = { ctx ->
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, ctx.packageName)
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                }
            }
        ),
        ToolShortcut(
            id = "bluetooth_settings",
            title = "Bluetooth Device Settings",
            subtitle = "Pair, connect, or configure wireless earbuds and headsets",
            category = "Connectivity",
            permissionRequirement = "None (Public Intent)",
            intentBuilder = { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) }
        ),
        ToolShortcut(
            id = "battery_optimization",
            title = "Battery Optimization Exemption",
            subtitle = "Prevent Android from killing the background EQ service",
            category = "Stability",
            permissionRequirement = "Optional: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            intentBuilder = { ctx ->
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                    } catch (_: Throwable) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }
                } else {
                    Intent(Settings.ACTION_SETTINGS)
                }
            }
        ),
        ToolShortcut(
            id = "app_notifications",
            title = "App Notification Settings",
            subtitle = "Configure foreground EQ service playback notifications",
            category = "Notifications",
            permissionRequirement = "None (Public Settings intent)",
            intentBuilder = { ctx ->
                if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    }
                }
            }
        ),
        ToolShortcut(
            id = "app_permissions",
            title = "App Info & Permissions",
            subtitle = "Manage Record Audio, Nearby Devices, and Storage permissions",
            category = "Privacy & Permissions",
            permissionRequirement = "None (Direct app info link)",
            intentBuilder = { ctx ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
            }
        ),
        ToolShortcut(
            id = "dev_options",
            title = "Developer Options",
            subtitle = "Access Bluetooth Audio Codec, sample rate, and bits-per-sample",
            category = "Advanced Audio",
            permissionRequirement = "Requires Developer Options enabled in Android Settings",
            intentBuilder = { Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
        )
    )

    fun launchShortcut(context: Context, shortcut: ToolShortcut): Boolean {
        return try {
            val intent = shortcut.intentBuilder(context) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Safely probes audio focus using official Android public API.
     * Evaluates transient ducking focus and safely abandons it.
     */
    fun probeAudioFocus(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AudioManager unavailable"

        return try {
            if (Build.VERSION.SDK_INT >= 26) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                val result = am.requestAudioFocus(focusRequest)
                val status = when (result) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "FOCUS GRANTED (Transient Ducking OK)"
                    AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FOCUS FAILED (Blocked by active session)"
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "FOCUS DELAYED (System queued request)"
                    else -> "FOCUS CODE $result"
                }
                am.abandonAudioFocusRequest(focusRequest)
                status
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                val status = if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "FOCUS GRANTED" else "FOCUS FAILED"
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
                status
            }
        } catch (t: Throwable) {
            "Probe error: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    fun getAudioModeName(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Unknown"
        return when (am.mode) {
            AudioManager.MODE_NORMAL -> "Normal (Media & general audio)"
            AudioManager.MODE_RINGTONE -> "Ringtone"
            AudioManager.MODE_IN_CALL -> "In-Call (Voice call active)"
            AudioManager.MODE_IN_COMMUNICATION -> "In-Communication (VoIP / chat active)"
            else -> "Mode ${am.mode}"
        }
    }

    fun getRingerModeName(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "Unknown"
        return when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Mode ${am.ringerMode}"
        }
    }
}
