package com.soniccore.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soniccore.app.ui.design.GlassIconButton
import com.soniccore.app.ui.design.LiquidGlassSurface
import com.soniccore.app.ui.design.LocalGlassIntensity
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.SonicCoreTone
import com.soniccore.app.ui.design.color
import com.soniccore.app.ui.design.liquidGlassMaterialSpec

/**
 * SonicCore navigation model.
 *
 * The app has exactly SIX top-level categories, shown as cards on Home. Every
 * category owns its own internal navigation: a hub screen plus the tools it
 * contains. Tools either route to a long-standing full-screen destination
 * ([SonicCoreDestination]) or to a tool screen dedicated to this category.
 *
 * The bottom dock exposes the six categories only — never internal tools — so
 * the dock stays a stable "where am I" control instead of a flat tool list.
 */
enum class SonicCoreDestination(
    val title: String,
    val label: String,
    val subtitle: String,
    val icon: String
) {
    // Audio
    EQUALIZER("Equalizer", "EQ", "Graphic and parametric equalization", "∿"),
    PLAYER("Player", "PLAYER", "Supported local playback and transport", "▶"),
    DSP_FX("DSP Studio", "DSP", "Processing stages, bypass and parameters", "⌘"),
    SYSTEM_CAPTURE("System Capture", "CAPTURE", "Capture eligibility, consent and status", "◎"),
    SIGNAL_PATH("Signal Path", "PATH", "Output route and measured pipeline status", "↗"),
    AUDIO_OVERVIEW("Live Overview", "OVERVIEW", "Live meters, spectrum and engine health", "◉"),
    SESSIONS("Sessions", "SESSIONS", "Recorded capture sessions and verdicts", "▤"),
    PRO_FX("Pro FX", "PRO FX", "Noise gate and limiter controls", "✦"),

    // Device / platform
    DIAGNOSTICS("Device Diagnostics", "DIAG", "Memory, battery, thermal and service health", "⚙"),
    CAPABILITIES("Capabilities", "CAPS", "Capability registry and platform restrictions", "✓"),
    DEVICE_TOOLS("Device Tools", "TOOLS", "System audio shortcuts and settings", "🛠"),
    APP_MANAGER("App Manager", "APPS", "App profiles, package visibility and launch", "㗊"),

    // Other workspaces
    SHIZUKU("Shizuku Center", "SHIZUKU", "Elevated bridge status and operation registry", "⇄"),
    CUSTOMIZATION("Customization Center", "CUSTOM", "Themes, glass intensity and appearance", "◈"),
    PERMISSIONS("Permissions & Privacy", "PERMS", "Runtime permissions, consent and privacy", "🔒"),
    SETTINGS("Settings", "SET", "Preferences hub and app information", "≡");

    companion object {
        /** Every destination that still has a dedicated full-screen body. */
        val all: List<SonicCoreDestination> = entries.toList()
    }
}

/** The six top-level Home categories. */
enum class SonicCoreCategory(
    val title: String,
    val label: String,
    val subtitle: String,
    val icon: String,
    val tone: SonicCoreTone
) {
    AUDIO(
        "Audio",
        "AUDIO",
        "Equalizer, DSP studio, player, presets and capture",
        "∿",
        SonicCoreTone.CYAN
    ),
    DEVICE(
        "Device Center",
        "DEVICE",
        "Hardware, memory, storage, battery, sensors and network",
        "▣",
        SonicCoreTone.BLUE
    ),
    SHIZUKU(
        "Shizuku Center",
        "SHIZUKU",
        "Connection, setup, authorization and elevated operations",
        "⇄",
        SonicCoreTone.VIOLET
    ),
    CUSTOMIZE(
        "Customization",
        "THEME",
        "Themes, accent, glass, layout, density and motion",
        "◈",
        SonicCoreTone.MAGENTA
    ),
    PRIVACY(
        "Permissions & Privacy",
        "PRIVACY",
        "Runtime permissions, consent, local data and exports",
        "🔒",
        SonicCoreTone.GREEN
    ),
    SETTINGS(
        "Settings",
        "SETTINGS",
        "Preferences, notifications, storage, diagnostics and about",
        "≡",
        SonicCoreTone.SLATE
    )
}

/** Per-workspace colour identity lives in the design layer ([SonicCoreTone]). */
fun SonicCoreCategory.color(): Color = tone.color()

/**
 * One tool inside a category. [destination] non-null reuses an existing
 * full-screen implementation; null means the tool has its own screen rendered
 * by the category tool router.
 */
data class SonicCoreTool(
    val id: String,
    val title: String,
    val summary: String,
    val icon: String,
    val category: SonicCoreCategory,
    val destination: SonicCoreDestination? = null,
    val keywords: String = ""
) {
    /** Text used by Home search — title, summary and the tool's category. */
    val searchText: String get() = "$title $summary $keywords ${category.title}"
}

object SonicCoreToolRegistry {

    private fun tool(
        id: String,
        category: SonicCoreCategory,
        title: String,
        summary: String,
        icon: String,
        destination: SonicCoreDestination? = null,
        keywords: String = ""
    ) = SonicCoreTool(id, title, summary, icon, category, destination, keywords)

    /** Every internal tool, in the order it appears in its category. */
    val all: List<SonicCoreTool> = listOf(
        // ── AUDIO ──
        tool("audio.overview", SonicCoreCategory.AUDIO, "Live Overview",
            "Real-time meters, spectrum, engine health and performance counters",
            "◉", SonicCoreDestination.AUDIO_OVERVIEW, "meter peak spectrum engine"),
        tool("audio.equalizer", SonicCoreCategory.AUDIO, "Equalizer",
            "Graphic EQ, parametric bands, frequency, gain, Q, preamp, bass and treble",
            "∿", SonicCoreDestination.EQUALIZER, "parametric q factor bands preamp"),
        tool("audio.dsp", SonicCoreCategory.AUDIO, "DSP Studio",
            "Processing stages, stage bypass, parameters and clipping metrics",
            "⌘", SonicCoreDestination.DSP_FX, "stage bypass clipping chain compressor"),
        tool("audio.player", SonicCoreCategory.AUDIO, "Audio Player",
            "Supported local playback, transport controls, queue and test signals",
            "▶", SonicCoreDestination.PLAYER, "play pause queue tone sine"),
        tool("audio.presets", SonicCoreCategory.AUDIO, "Presets",
            "Built-in profiles, custom profiles, save, import and export",
            "☆", null, "profile save import export preset library"),
        tool("audio.capture", SonicCoreCategory.AUDIO, "System Capture",
            "Capture eligibility, user consent, capture status and source restrictions",
            "◎", SonicCoreDestination.SYSTEM_CAPTURE, "media projection consent eligibility"),
        tool("audio.output", SonicCoreCategory.AUDIO, "Output & Signal Path",
            "Detected output route, pipeline stages and measured processing status",
            "↗", SonicCoreDestination.SIGNAL_PATH, "route latency stage pipeline"),
        tool("audio.diagnostics", SonicCoreCategory.AUDIO, "Audio Diagnostics",
            "Audio session state, available counters, errors and report export",
            "⚑", null, "session counters underrun clip export report"),
        tool("audio.sessions", SonicCoreCategory.AUDIO, "Capture Sessions",
            "Recorded session records, their verdicts, comparison and per-app profiles",
            "▤", SonicCoreDestination.SESSIONS, "session history compare app profile verdict"),
        tool("audio.profx", SonicCoreCategory.AUDIO, "Pro FX",
            "Noise gate and limiter controls on the capture pipeline",
            "✦", SonicCoreDestination.PRO_FX, "gate limiter dynamics threshold"),

        // ── DEVICE CENTER ──
        tool("device.overview", SonicCoreCategory.DEVICE, "Device Overview",
            "Model, build, SoC, Android version and overall capability summary",
            "▣", null, "model build soc android version"),
        tool("device.cpu", SonicCoreCategory.DEVICE, "CPU & Hardware",
            "Core count, ABIs, hardware name, readable frequencies and native bridge",
            "▤", null, "cores abi frequency hardware jni"),
        tool("device.memory", SonicCoreCategory.DEVICE, "Memory & App Usage",
            "System memory state, this app's footprint and background limits",
            "▥", null, "ram footprint pss low memory"),
        tool("device.storage", SonicCoreCategory.DEVICE, "Storage",
            "Data partition totals, free space and SonicCore's own cache footprint",
            "▦", null, "disk free cache partition"),
        tool("device.battery", SonicCoreCategory.DEVICE, "Battery & Thermal",
            "Charge level, power source, thermal status and throttling signals",
            "▧", null, "charge temperature throttling power"),
        tool("device.display", SonicCoreCategory.DEVICE, "Display",
            "Resolution, density, refresh rate and font-scale behaviour",
            "▨", null, "resolution density refresh hz font"),
        tool("device.sensors", SonicCoreCategory.DEVICE, "Sensors",
            "Sensor coverage, which tracked sensors are present and which are missing",
            "▩", null, "accelerometer gyroscope light proximity"),
        tool("device.network", SonicCoreCategory.DEVICE, "Network & Connectivity",
            "Active transport, metering, link estimate and VPN state",
            "⇅", null, "wifi cellular metered vpn link"),
        tool("device.health", SonicCoreCategory.DEVICE, "Device Health Summary",
            "Consolidated health checks with restricted and unavailable data labelled",
            "✚", null, "health summary report redacted"),
        tool("device.diagnostics", SonicCoreCategory.DEVICE, "Device Diagnostics",
            "Full diagnostics console: memory, battery, thermal, services and report export",
            "⚙", SonicCoreDestination.DIAGNOSTICS, "diagnostics console report thermal services"),
        tool("device.capabilities", SonicCoreCategory.DEVICE, "Capability Registry",
            "Every declared capability with its real support state and restriction",
            "✓", SonicCoreDestination.CAPABILITIES, "capability registry support restriction"),
        tool("device.tools", SonicCoreCategory.DEVICE, "System Audio Shortcuts",
            "Platform audio, equalizer and output settings that affect SonicCore",
            "🛠", SonicCoreDestination.DEVICE_TOOLS, "system audio settings shortcut"),
        tool("device.apps", SonicCoreCategory.DEVICE, "App Manager & Profiles",
            "Installed apps, package visibility and per-app preset profiles",
            "㗊", SonicCoreDestination.APP_MANAGER, "apps packages per-app profile"),

        // ── SHIZUKU CENTER ──
        tool("shizuku.connection", SonicCoreCategory.SHIZUKU, "Connection Status",
            "Binder state, server version, server UID and SELinux context",
            "⇄", null, "binder version uid selinux running"),
        tool("shizuku.setup", SonicCoreCategory.SHIZUKU, "Setup Guide",
            "Step-by-step setup state, including which steps are still blocked",
            "☰", null, "install guide steps start service"),
        tool("shizuku.authorization", SonicCoreCategory.SHIZUKU, "Authorization & Permissions",
            "Request or release the Shizuku API permission and read the real result",
            "🔑", null, "permission request grant deny rationale"),
        tool("shizuku.operations", SonicCoreCategory.SHIZUKU, "Supported Operations",
            "Every declared operation with its real availability and requirements",
            "✓", null, "registry availability requirements implemented"),
        tool("shizuku.history", SonicCoreCategory.SHIZUKU, "Results & History",
            "Operation log plus the outcome, detail and timing of recent operations",
            "▤", null, "log history result timing outcome"),
        tool("shizuku.troubleshooting", SonicCoreCategory.SHIZUKU, "Troubleshooting",
            "Failure codes mapped to causes, with what each one would actually need",
            "⚠", null, "failure error code cause restricted"),
        tool("shizuku.console", SonicCoreCategory.SHIZUKU, "Elevated Bridge Console",
            "Bridge state, the whole operation registry and recent results on one screen",
            "⌸", SonicCoreDestination.SHIZUKU, "console binder registry results elevated"),

        // ── CUSTOMIZATION ──
        tool("custom.themes", SonicCoreCategory.CUSTOMIZE, "Theme Gallery",
            "Every built-in theme with a live swatch and one-tap apply",
            "◈", SonicCoreDestination.CUSTOMIZATION, "theme palette gallery skin"),
        tool("custom.accent", SonicCoreCategory.CUSTOMIZE, "Color & Accent",
            "Choose the accent used for active audio elements and controls",
            "◐", null, "accent color tint hue"),
        tool("custom.glass", SonicCoreCategory.CUSTOMIZE, "Glass Appearance",
            "Liquid-glass intensity, opacity and edge definition",
            "◫", null, "glass blur intensity translucency"),
        tool("custom.layout", SonicCoreCategory.CUSTOMIZE, "Home Screen Layout",
            "Home card density, column count and section order",
            "⊞", null, "grid columns layout cards"),
        tool("custom.cards", SonicCoreCategory.CUSTOMIZE, "Card Style & Density",
            "Card corner radius, border weight and information density",
            "▢", null, "corner radius border density spacing"),
        tool("custom.motion", SonicCoreCategory.CUSTOMIZE, "Animation & Motion",
            "Transition speed and a reduced-motion switch that really turns motion off",
            "≋", null, "animation reduced motion transition speed"),
        tool("custom.preview", SonicCoreCategory.CUSTOMIZE, "Preview & Reset",
            "Live preview of the current appearance, with a full reset",
            "◉", null, "preview reset restore default"),

        // ── PERMISSIONS & PRIVACY ──
        tool("privacy.runtime", SonicCoreCategory.PRIVACY, "Runtime Permissions",
            "Real grant state for every declared runtime permission, with reasons",
            "🔒", SonicCoreDestination.PERMISSIONS, "grant deny runtime microphone notification"),
        tool("privacy.special", SonicCoreCategory.PRIVACY, "Special App Access",
            "Battery optimisation, usage access and other non-runtime exemptions",
            "⚿", null, "battery optimization usage access overlay"),
        tool("privacy.consent", SonicCoreCategory.PRIVACY, "Audio Capture Consent",
            "Media projection consent state and how to revoke it",
            "◎", null, "media projection consent revoke capture"),
        tool("privacy.notifications", SonicCoreCategory.PRIVACY, "Notification & Background Access",
            "Notification permission, foreground service state and battery restrictions",
            "🔔", null, "notification foreground service background"),
        tool("privacy.info", SonicCoreCategory.PRIVACY, "Privacy Information",
            "What SonicCore reads, what it never sends, and where data stays",
            "ⓘ", null, "privacy policy data local"),
        tool("privacy.exports", SonicCoreCategory.PRIVACY, "Local Data & Diagnostic Exports",
            "What is stored locally and exactly what an exported report contains",
            "⇱", null, "export report share local files"),
        tool("privacy.reset", SonicCoreCategory.PRIVACY, "Clear History & Reset Data",
            "Clear the operation log, session history and locally stored preferences",
            "⌫", null, "clear delete reset history log"),

        // ── SETTINGS ──
        tool("settings.general", SonicCoreCategory.SETTINGS, "General Preferences",
            "Appearance mode, startup behaviour and the main engine settings sheet",
            "≡", SonicCoreDestination.SETTINGS, "preferences general startup engine"),
        tool("settings.audio", SonicCoreCategory.SETTINGS, "Audio Defaults",
            "Default preset, band count, preamp behaviour and startup state",
            "♪", null, "default preset bands preamp startup"),
        tool("settings.navigation", SonicCoreCategory.SETTINGS, "Navigation Preferences",
            "Default category, back-stack behaviour and dock visibility",
            "⇱", null, "navigation dock back stack default"),
        tool("settings.notifications", SonicCoreCategory.SETTINGS, "Notifications",
            "Ongoing engine notification, quick settings tile and widget state",
            "🔔", null, "notification tile widget ongoing"),
        tool("settings.behavior", SonicCoreCategory.SETTINGS, "App Behavior & Background",
            "Background operation, start on boot and battery restriction state",
            "⏻", null, "background boot battery behavior"),
        tool("settings.storage", SonicCoreCategory.SETTINGS, "Storage & Data Management",
            "Local footprint of preferences, cache and recorded session logs",
            "▦", null, "storage cache data usage files"),
        tool("settings.diagnostics", SonicCoreCategory.SETTINGS, "Diagnostics Preferences",
            "Operation logging, report redaction and diagnostic detail level",
            "⚙", null, "diagnostics logging redaction report"),
        tool("settings.about", SonicCoreCategory.SETTINGS, "About & Licenses",
            "Version, build information, ABI support and open-source notices",
            "ⓘ", null, "about version build license open source")
    )

    private val byId: Map<String, SonicCoreTool> = all.associateBy { it.id }

    /** The tool that renders [destination], or null when no tool owns it. */
    private val byDestination: Map<SonicCoreDestination, SonicCoreTool> =
        all.mapNotNull { tool -> tool.destination?.let { it to tool } }.toMap()

    fun forCategory(category: SonicCoreCategory): List<SonicCoreTool> =
        all.filter { it.category == category }

    fun find(id: String): SonicCoreTool? = byId[id]

    fun firstFor(destination: SonicCoreDestination): SonicCoreTool? = byDestination[destination]

    /** Tools that have no dedicated screen of their own. */
    fun withoutDestination(): List<SonicCoreTool> = all.filter { it.destination == null }

    /** Case-insensitive search across every category — never flattens the tree. */
    fun search(query: String): List<SonicCoreTool> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return all.filter { it.searchText.contains(trimmed, ignoreCase = true) }
    }

    fun countFor(category: SonicCoreCategory): Int = forCategory(category).size
}

@Composable
fun SonicCoreScreenHeader(
    destination: SonicCoreDestination,
    onSettingsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                destination.title.uppercase(),
                color = SonicCoreColors.PrimaryText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
            Text(
                destination.subtitle,
                color = SonicCoreColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (onBackClick != null) {
            GlassIconButton(
                contentDescription = "Back to previous screen",
                onClick = onBackClick
            ) {
                Text("‹", color = SonicCoreColors.Cyan, fontSize = 28.sp)
            }
        } else {
            GlassIconButton(
                contentDescription = "Open settings",
                onClick = onSettingsClick
            ) {
                Text("⚙", color = SonicCoreColors.Cyan, fontSize = 20.sp)
            }
        }
    }
}

/**
 * Breadcrumb header for a category hub or a tool screen:
 * `SONICCORE / AUDIO / Equalizer`. Tap the category to go back one level.
 */
@Composable
fun SonicCoreBreadcrumbHeader(
    category: SonicCoreCategory,
    tool: SonicCoreTool?,
    onBack: () -> Unit,
    onCategoryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                contentDescription = "Back to previous screen",
                onClick = onBack
            ) {
                Text("‹", color = SonicCoreColors.Cyan, fontSize = 28.sp)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(
                    if (tool == null) category.title.uppercase() else category.title.uppercase(),
                    color = SonicCoreColors.PrimaryText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                    modifier = if (onCategoryClick != null && tool != null) {
                        Modifier
                            .semantics { contentDescription = "Back to ${category.title}" }
                            .then(Modifier)
                    } else Modifier
                )
                Text(
                    tool?.title ?: category.subtitle,
                    color = category.color(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Text(
            if (tool == null) "SONICCORE / ${category.label}"
            else "SONICCORE / ${category.label} / ${tool.title.uppercase()}",
            color = SonicCoreColors.SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 6.dp, top = 2.dp)
        )
    }
}

/**
 * Contextual dock: the SIX categories, nothing else. Internal tools are reached
 * through their category, so the dock never turns into a flat tool list.
 */
@Composable
fun SonicCoreBottomNavigation(
    selected: SonicCoreCategory?,
    onSelect: (SonicCoreCategory) -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier
) {
    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = if (landscape) 18.dp else 10.dp, vertical = 8.dp),
        contentPadding = 7.dp,
        shape = RoundedCornerShape(30.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SonicCoreCategory.entries.forEach { category ->
                val active = selected == category
                val material = liquidGlassMaterialSpec(LocalGlassIntensity.current)
                val tone = category.color()
                val activeSurface by animateColorAsState(
                    targetValue = if (active) tone.copy(alpha = material.tintAlpha + 0.10f)
                    else Color.Transparent,
                    label = "dockSelection"
                )
                Surface(
                    onClick = { onSelect(category) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics {
                            contentDescription = category.title
                            stateDescription = if (active) "Selected" else "Not selected"
                        },
                    shape = RoundedCornerShape(22.dp),
                    color = activeSurface,
                    contentColor = if (active) tone else SonicCoreColors.SecondaryText,
                    border = BorderStroke(
                        0.8.dp,
                        (if (active) tone else SonicCoreColors.Border)
                            .copy(alpha = if (active) material.edgeAlpha else 0f)
                    ),
                    shadowElevation = if (active) 4.dp else 0.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            category.icon,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = if (active) tone else SonicCoreColors.SecondaryText
                        )
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            color = if (active) SonicCoreColors.PrimaryText else SonicCoreColors.SecondaryText
                        )
                    }
                }
            }
        }
    }
}
