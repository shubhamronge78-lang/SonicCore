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
import com.soniccore.app.ui.design.GlassNavItem
import com.soniccore.app.ui.design.GlassNavigation
import com.soniccore.app.ui.design.GlassTopBar
import com.soniccore.app.ui.design.SonicCoreColors
import com.soniccore.app.ui.design.SonicCoreTone
import com.soniccore.app.ui.design.color

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
 * The Audio Center's own sub-navigation.
 *
 * Audio is the largest workspace in SonicCore, so its thirteen destinations are
 * grouped into six sections that the Audio Center shows as tabs on a phone and
 * as a grouped list on a tablet. The groups are the app's own vocabulary: every
 * Audio tool belongs to exactly one of them, which `SonicCoreNavigationTest`
 * asserts.
 */
enum class SonicCoreAudioGroup(
    val title: String,
    val label: String,
    val summary: String,
    val icon: String
) {
    OVERVIEW(
        "Overview",
        "OVERVIEW",
        "Status, active processing, output device and quick controls",
        "◉"
    ),
    SOUND_CONTROLS(
        "Sound Controls",
        "SOUND",
        "Equalizer, tone shaping, stereo placement and dynamics",
        "∿"
    ),
    DSP_PROCESSING(
        "DSP Processing",
        "DSP",
        "Processing stages, parameters, bypass and signal flow",
        "⌘"
    ),
    PLAYBACK_CAPTURE(
        "Playback & Capture",
        "PLAYBACK",
        "Player transport, capture consent and session history",
        "▶"
    ),
    DEVICES_ROUTING(
        "Devices & Routing",
        "DEVICES",
        "Output routes, device capabilities, latency and signal path",
        "↗"
    ),
    PRESETS_DIAGNOSTICS(
        "Presets & Diagnostics",
        "PRESETS",
        "Profiles, counters, compatibility probes and report export",
        "☆"
    )
}

/**
 * Who owns a destination's vertical scrolling.
 *
 * The shell owns one scroll container for the whole app (`mainContent` in
 * `MainActivity.kt`), so a screen never has to know whether it is tall, and no
 * screen may nest a second same-axis scroll inside it: Compose rejects a
 * scrollable measured with the parent's infinite maximum height.
 *
 * A screen whose body *is* a lazy list is the one case that cannot use the
 * shell's container — a lazy list needs a bounded height to scroll in. Such a
 * screen declares [SCREEN], and the shell gives it the remaining height instead
 * of wrapping it in a scroll container. Exactly one owner either way.
 */
enum class ScrollOwner { SHELL, SCREEN }

/**
 * One tool inside a category. [destination] non-null reuses an existing
 * full-screen implementation; null means the tool has its own screen rendered
 * by the category tool router.
 *
 * [audioGroup] is set for Audio tools only — it is what places the tool in the
 * Audio Center's sub-navigation.
 *
 * [scrollOwner] is [ScrollOwner.SCREEN] only for a tool whose own screen is a
 * lazy list; everything else leaves the shell in charge.
 */
data class SonicCoreTool(
    val id: String,
    val title: String,
    val summary: String,
    val icon: String,
    val category: SonicCoreCategory,
    val destination: SonicCoreDestination? = null,
    val keywords: String = "",
    val audioGroup: SonicCoreAudioGroup? = null,
    val scrollOwner: ScrollOwner = ScrollOwner.SHELL
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
        keywords: String = "",
        audioGroup: SonicCoreAudioGroup? = null,
        scrollOwner: ScrollOwner = ScrollOwner.SHELL
    ) = SonicCoreTool(id, title, summary, icon, category, destination, keywords, audioGroup, scrollOwner)

    /** Every internal tool, in the order it appears in its category. */
    val all: List<SonicCoreTool> = listOf(
        // ── AUDIO · OVERVIEW ──
        tool("audio.dashboard", SonicCoreCategory.AUDIO, "Audio Dashboard",
            "Current audio status, active processing, output device and quick controls",
            "◉", SonicCoreDestination.AUDIO_OVERVIEW, "meter peak spectrum engine status",
            SonicCoreAudioGroup.OVERVIEW),

        // ── AUDIO · SOUND CONTROLS ──
        tool("audio.equalizer", SonicCoreCategory.AUDIO, "Equalizer",
            "Graphic EQ, parametric bands, presets, preamp and per-band controls",
            "∿", SonicCoreDestination.EQUALIZER, "parametric q factor bands preamp graphic",
            SonicCoreAudioGroup.SOUND_CONTROLS),
        tool("audio.bass_treble", SonicCoreCategory.AUDIO, "Bass & Treble",
            "Independent low and high shelf gains with a frequency-response view",
            "⌁", null, "shelf low high tone bass treble response",
            SonicCoreAudioGroup.SOUND_CONTROLS),
        tool("audio.spatial", SonicCoreCategory.AUDIO, "Stereo & Spatial",
            "Stereo width, balance, channel swap and mono downmix",
            "⇄", null, "width balance swap mono channel stereo",
            SonicCoreAudioGroup.SOUND_CONTROLS),
        tool("audio.dynamics", SonicCoreCategory.AUDIO, "Dynamics",
            "Noise gate, limiter strength and anti-clip state; compressor parameters stay in DSP Studio",
            "✦", SonicCoreDestination.PRO_FX, "gate limiter dynamics threshold compressor anti clip",
            SonicCoreAudioGroup.SOUND_CONTROLS),

        // ── AUDIO · DSP PROCESSING ──
        tool("audio.dsp", SonicCoreCategory.AUDIO, "DSP Studio",
            "Processing stages, parameters, per-stage bypass and signal flow",
            "⌘", SonicCoreDestination.DSP_FX, "stage bypass clipping chain signal flow",
            SonicCoreAudioGroup.DSP_PROCESSING),

        // ── AUDIO · PLAYBACK & CAPTURE ──
        tool("audio.player", SonicCoreCategory.AUDIO, "Player",
            "Playback controls, track information and audio-session status",
            "▶", SonicCoreDestination.PLAYER, "play pause queue tone sine track",
            SonicCoreAudioGroup.PLAYBACK_CAPTURE),
        tool("audio.capture", SonicCoreCategory.AUDIO, "Audio Capture",
            "MediaProjection consent, capture status, source restrictions and recovery",
            "◎", SonicCoreDestination.SYSTEM_CAPTURE, "media projection consent eligibility recovery",
            SonicCoreAudioGroup.PLAYBACK_CAPTURE),
        tool("audio.sessions", SonicCoreCategory.AUDIO, "Audio Sessions",
            "Session history, session diagnostics and export",
            "▤", SonicCoreDestination.SESSIONS, "session history compare export verdict",
            SonicCoreAudioGroup.PLAYBACK_CAPTURE),

        // ── AUDIO · DEVICES & ROUTING ──
        tool("audio.devices", SonicCoreCategory.AUDIO, "Output & Devices",
            "Available output routes, Bluetooth, wired audio and device capabilities",
            "⇲", null, "output route bluetooth wired usb device capability",
            SonicCoreAudioGroup.DEVICES_ROUTING),
        tool("audio.signal_path", SonicCoreCategory.AUDIO, "Signal Path",
            "Source-to-output pipeline, frame counters, latency and diagnostics",
            "↗", SonicCoreDestination.SIGNAL_PATH, "route latency stage pipeline frames",
            SonicCoreAudioGroup.DEVICES_ROUTING),

        // ── AUDIO · PRESETS & DIAGNOSTICS ──
        tool("audio.presets", SonicCoreCategory.AUDIO, "Audio Presets",
            "Save, load, rename, duplicate, import and export profiles",
            "☆", null, "profile save load rename duplicate import export preset library",
            SonicCoreAudioGroup.PRESETS_DIAGNOSTICS),
        tool("audio.diagnostics", SonicCoreCategory.AUDIO, "Audio Diagnostics",
            "Compatibility probes, processing status, errors and test results",
            "⚑", null, "session counters underrun clip export report probe test",
            SonicCoreAudioGroup.PRESETS_DIAGNOSTICS),

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
        // The catalogue is a lazy list, so this one tool owns its vertical scroll
        // (see ScrollOwner): the shell hands it the remaining height instead of a
        // scroll container, which is what keeps a large inventory scalable.
        tool("custom.repositories", SonicCoreCategory.CUSTOMIZE, "Repository Manager",
            "Every audited open-source project with its licence, privilege and integration status",
            "⧉", null, "repository catalog inventory licence fork upstream attribution open source",
            null, ScrollOwner.SCREEN),

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

    /** The Audio tools of one Audio Center group, in registry order. */
    fun forAudioGroup(group: SonicCoreAudioGroup): List<SonicCoreTool> =
        all.filter { it.audioGroup == group }

    /**
     * Every Audio tool grouped in the Audio Center's own order — the shape the
     * hub and the phone/tablet layouts both render from.
     */
    fun audioToolsByGroup(): List<Pair<SonicCoreAudioGroup, List<SonicCoreTool>>> =
        SonicCoreAudioGroup.entries.map { it to forAudioGroup(it) }

    /** Group of an Audio tool — null for every other category. */
    fun groupOf(tool: SonicCoreTool): SonicCoreAudioGroup? = tool.audioGroup

    /** All Audio tools, in the order their groups are listed. */
    fun audioTools(): List<SonicCoreTool> = audioToolsByGroup().flatMap { it.second }
}

@Composable
fun SonicCoreScreenHeader(
    destination: SonicCoreDestination,
    onSettingsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Delegates to the shared top bar so every screen's header — back control,
    // title scale, action button — is the same component (defect GL-04/GL-06).
    GlassTopBar(
        title = destination.title,
        subtitle = destination.subtitle,
        modifier = modifier,
        onBack = onBackClick,
        actionIcon = if (onBackClick == null) "\u2699" else null,
        actionDescription = "Open settings",
        onAction = if (onBackClick == null) onSettingsClick else null
    )
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
    // One shared dock component (GlassNavigation) rather than a bespoke pill
    // per surface: it carries the glass tokens, the minimum touch target, the
    // navigation-bar inset and a real disabled state.
    val items = SonicCoreCategory.entries.map { category ->
        GlassNavItem(
            label = category.label,
            icon = category.icon,
            selected = selected == category,
            onClick = { onSelect(category) }
        )
    }
    val toneByIndex = SonicCoreCategory.entries.map { it.color() }
    GlassNavigation(
        items = items,
        modifier = modifier,
        landscape = landscape,
        accentOf = { item -> toneByIndex.getOrElse(items.indexOf(item)) { SonicCoreColors.Cyan } }
    )
}
