package com.soniccore.app.repository

/**
 * SonicCore's open-source repository inventory.
 *
 * This is the data behind the **Repository Manager** (Customization → Repository
 * Manager) and the single place where a third-party project is recorded before
 * anything is taken from it. It is deliberately pure Kotlin with no Android
 * imports so the JVM tests can assert its invariants — most importantly that a
 * copyleft, proprietary or unverified licence can never be marked as integrated
 * into this MIT-licensed application.
 *
 * Every field that claims a fact about upstream (licence, last push, stars,
 * maintenance) was read from the GitHub REST API on [RepositoryInventory.AUDIT_DATE]
 * and is recorded in `docs/repository-inventory.md`. A project is
 * [IntegrationStatus.INTEGRATED] only when its code or its official client is in
 * this repository **and** covered by tests; everything else says exactly what
 * would be needed.
 */
enum class InventoryCategory(val title: String, val icon: String) {
    AUDIO("Audio", "∿"),
    DEVICE("Device Center", "▣"),
    SHIZUKU("Shizuku Center", "⇄"),
    CUSTOMIZATION("Customization", "◈"),
    PRIVACY("Permissions & Privacy", "🔒"),
    SETTINGS("Settings", "≡")
}

/** What an upstream capability would need from the platform to work at all. */
enum class PrivilegeRequirement(val label: String) {
    NONE("Ordinary app APIs"),
    SHIZUKU("Shizuku bridge"),
    ROOT("Root shell")
}

/**
 * Licence compatibility for an application distributed under MIT.
 *
 * SonicCore is MIT (`LICENSE`, © 2026 Shubham Ronge), so permissive licences may
 * be linked or bundled with attribution and copyleft ones may not be bundled at
 * all.
 */
enum class LicenseCompatibility(val label: String, val summary: String) {
    PERMISSIVE(
        "Compatible",
        "MIT / Apache-2.0 / BSD — may be linked or bundled with attribution."
    ),
    WEAK_COPYLEFT(
        "Not bundled",
        "LGPL — SonicCore does not bundle it; it would need its own separate component."
    ),
    COPYLEFT(
        "Incompatible",
        "GPL / AGPL — cannot be distributed inside an MIT-licensed application."
    ),
    PROPRIETARY(
        "Ineligible",
        "Proprietary — needs a commercial licence and cannot be bundled."
    ),
    UNVERIFIED(
        "Unverified",
        "No machine-readable licence was found; nothing may be taken until that is resolved."
    )
}

/** The integration vocabulary the Repository Manager reports. */
enum class IntegrationStatus(val label: String, val detail: String) {
    INTEGRATED(
        "Integrated",
        "Code or the official client is in this repository and is covered by tests."
    ),
    READY_TO_INTEGRATE(
        "Ready to Integrate",
        "Audited and licence-compatible; waiting for its implementation batch."
    ),
    REFERENCE_ONLY(
        "Reference Only",
        "Audited for design or capability comparison; nothing was taken."
    ),
    REQUIRES_SHIZUKU(
        "Requires Shizuku",
        "Only works through the elevated bridge, which SonicCore cannot obtain from an app process."
    ),
    REQUIRES_ROOT(
        "Requires Root",
        "Needs a root shell; SonicCore ships no root code path."
    ),
    INCOMPATIBLE(
        "Incompatible",
        "Its licence or terms forbid use inside this application."
    ),
    UNSUPPORTED(
        "Unsupported",
        "Android's security model or SonicCore's own scope rules it out."
    )
}

/** How actively upstream is maintained, from its last push date. */
enum class Maintenance(val label: String) {
    ACTIVE("Active"),
    SLOW("Slow"),
    ARCHIVED("Archived")
}

/** How much of an upstream project's capability SonicCore actually covers. */
enum class Coverage(val label: String) {
    ADOPTED("Adopted"),
    PARTIAL("Partial"),
    REFERENCE("Studied"),
    NONE("None")
}

/**
 * One audited project.
 *
 * [capability] is what upstream offers; [sonicCoreUse] is what SonicCore
 * actually does about it — never a promise, and always "why not" when the answer
 * is that nothing was taken.
 */
data class RepositoryEntry(
    val name: String,
    val title: String,
    val url: String,
    val category: InventoryCategory,
    val license: String,
    val compatibility: LicenseCompatibility,
    val status: IntegrationStatus,
    val privilege: PrivilegeRequirement,
    val maintenance: Maintenance,
    /** Last push date (`yyyy-MM-dd`) as reported by the GitHub API. */
    val lastPushed: String,
    val stars: Int,
    val coverage: Coverage,
    val capability: String,
    val sonicCoreUse: String,
    /** Android API requirement, or "not verified" when upstream does not state one. */
    val apiNote: String = "not verified",
    /** Set when this project is a fork or downstream edition of another entry. */
    val derivedFrom: String? = null
) {
    val id: String get() = name.lowercase()

    /** Text the Repository Manager search matches against. */
    val searchText: String
        get() = "$name $title $capability $sonicCoreUse $license " +
            "${category.title} ${status.label} ${privilege.label}"
}

object RepositoryInventory {

    /** Date every upstream fact below was read. */
    const val AUDIT_DATE = "2026-10-25"

    /** Where the facts came from, so the audit can be repeated instead of trusted. */
    const val AUDIT_SOURCE = "GitHub REST API — /repos/{owner}/{repo} and /repos/{owner}/{repo}/license"

    /**
     * Latest published GitHub release for the entries that are licence-compatible
     * or already adopted, read from `/repos/{owner}/{repo}/releases/latest` on
     * [AUDIT_DATE]. A project with no entry here publishes no GitHub release (or
     * was not queried in this pass), and the Repository Manager then shows the
     * last-push date as the only availability evidence instead of a guess.
     */
    val latestReleases: Map<String, String> = mapOf(
        "RikkaApps/Shizuku-API" to "v12.1.0 (2022-10-07) — Maven artifacts used here are 13.1.5",
        "androidx/androidx" to "no GitHub release (published through the AndroidX release train)",
        "xiph/speexdsp" to "SpeexDSP-1.2.1 (2022-06-17)",
        "jiixyj/libebur128" to "v1.2.6 (2021-02-14)",
        "google/oboe" to "1.11.0 (2026-09-15)",
        "androidx/media" to "1.11.1 (2026-09-11)",
        "chrisbanes/haze" to "2.0.1 (2026-09-29)",
        "google/material-design-icons" to "Material Icons 4.0.0 (2020-08-31)",
        "patrykandpatrick/vico" to "v3.3.1 (2026-08-28)",
        "coil-kt/coil" to "3.6.3 (2026-09-18)",
        "jaakkopasanen/AutoEq" to "no GitHub release (data repository, updated by commit)",
        "material-foundation/material-color-utilities" to "no GitHub release (consumed by commit or via Maven)"
    )

    private fun gh(name: String) = "https://github.com/$name"

    /** Every audited project, grouped by category in the app's own order. */
    val all: List<RepositoryEntry> = listOf(

        // ── AUDIO ──────────────────────────────────────────────────────────────
        RepositoryEntry(
            "jaakkopasanen/AutoEq", "AutoEq", gh("jaakkopasanen/AutoEq"),
            InventoryCategory.AUDIO, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-20", 16377, Coverage.NONE,
            "Headphone measurement database plus target curves, used to derive correction filters.",
            "Licence-compatible and scheduled for a later batch: the generated filters are ordinary parametric bands that the existing EQ already applies, so no engine change is needed."
        ),
        RepositoryEntry(
            "xiph/speexdsp", "SpeexDSP", gh("xiph/speexdsp"),
            InventoryCategory.AUDIO, "BSD-3-Clause", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-08", 743, Coverage.NONE,
            "Resampling, echo cancellation and preprocessor DSP in C (upstream mirror; development happens on Xiph's GitLab).",
            "Permissive licence and a real candidate for the resampling stage; the native engine currently does its own rate conversion, so nothing was taken yet."
        ),
        RepositoryEntry(
            "jiixyj/libebur128", "libebur128", gh("jiixyj/libebur128"),
            InventoryCategory.AUDIO, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2023-06-25", 493, Coverage.NONE,
            "EBU R128 / ITU-R BS.1770 loudness measurement in C.",
            "The loudness-matching feature is not shipped; this is the audited implementation to build it on, and its MIT licence permits it."
        ),
        RepositoryEntry(
            "google/oboe", "Oboe", gh("google/oboe"),
            InventoryCategory.AUDIO, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-21", 4128, Coverage.NONE,
            "C++ wrapper over AAudio and OpenSL ES for low-latency audio streams.",
            "SonicCore drives its own JNI engine over AudioTrack/AudioRecord; Oboe would be the low-latency replacement for that transport if the latency budget needs it.",
            apiNote = "AAudio needs API 26+; OpenSL ES fallback covers API 16+"
        ),
        RepositoryEntry(
            "androidx/media", "Jetpack Media3", gh("androidx/media"),
            InventoryCategory.AUDIO, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-23", 3054, Coverage.NONE,
            "ExoPlayer and the media session libraries, with a playback-capture-aware audio sink.",
            "The built-in player is SonicCore's own path; adopting Media3 would add codec coverage and is the audited alternative, not a dependency today."
        ),
        RepositoryEntry(
            "google/ExoPlayer", "ExoPlayer (legacy)", gh("google/ExoPlayer"),
            InventoryCategory.AUDIO, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2025-12-23", 21939, Coverage.REFERENCE,
            "The original standalone player library, now deprecated and stale.",
            "Kept as the fork/alternative comparison for androidx/media: upstream itself points new work at Media3, so this entry exists to stop anyone integrating the deprecated line.",
            derivedFrom = "Superseded by androidx/media"
        ),
        RepositoryEntry(
            "jaakkopasanen/Impulcifer", "Impulcifer", gh("jaakkopasanen/Impulcifer"),
            InventoryCategory.AUDIO, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2023-11-25", 292, Coverage.REFERENCE,
            "Offline generation of binaural impulse responses for headphone surround.",
            "It is a desktop measurement tool, so it cannot run inside an Android app; studied as the source of the impulse responses a future convolver stage would load."
        ),
        RepositoryEntry(
            "timschneeb/RootlessJamesDSP", "RootlessJamesDSP", gh("timschneeb/RootlessJamesDSP"),
            InventoryCategory.AUDIO, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-08-31", 1712, Coverage.NONE,
            "JamesDSP effects (ViPER-DDC, convolver, crossfeed, AutoEQ) applied to other apps' playback without root.",
            "No code, DSP tables or impulse responses were taken. The capability it targets — capture system playback into a native DSP chain — is already SonicCore's own pre-existing pipeline."
        ),
        RepositoryEntry(
            "alienware377/RootlessViPER4Android", "RootlessViPER4Android", gh("alienware377/RootlessViPER4Android"),
            InventoryCategory.AUDIO, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 98, Coverage.NONE,
            "ViPER4Android effect set ported into a RootlessJamesDSP fork (no Magisk module).",
            "Audited only for the set of effects a modern EQ exposes; the same GPL-3.0 blocker applies, and SonicCore's limiter, compressor, shelves and parametric bands are its own implementation.",
            derivedFrom = "timschneeb/RootlessJamesDSP"
        ),
        RepositoryEntry(
            "james34602/JamesDSPManager", "JamesDSPManager", gh("james34602/JamesDSPManager"),
            InventoryCategory.AUDIO, "not detected at the repository root", LicenseCompatibility.UNVERIFIED,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.ROOT,
            Maintenance.SLOW, "2025-06-18", 984, Coverage.NONE,
            "The original system-framework DSP effects pack that RootlessJamesDSP links against.",
            "No licence file is published, so no permission to redistribute exists; it is recorded so the dependency chain behind the rootless forks is visible."
        ),
        RepositoryEntry(
            "pittvandewitt/Wavelet", "Wavelet", gh("pittvandewitt/Wavelet"),
            InventoryCategory.AUDIO, "not detected at the repository root", LicenseCompatibility.UNVERIFIED,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-05-15", 896, Coverage.REFERENCE,
            "AutoEQ-based system equalizer with a parametric EQ, bass boost and reverberation.",
            "Studied for how it presents AutoEQ correction to a normal user. Nothing may be taken: the default branch publishes no licence file, so the position is unresolved."
        ),
        RepositoryEntry(
            "bearinmindcat/Equalizer314", "Equalizer314", gh("bearinmindcat/Equalizer314"),
            InventoryCategory.AUDIO, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-12", 505, Coverage.REFERENCE,
            "Parametric EQ built on Android's DynamicsProcessing and Visualizer APIs.",
            "Its documentation of the session-0 limitation of those platform APIs matches what SonicCore's capture path already reports; no code was taken."
        ),
        RepositoryEntry(
            "HEnquist/camilladsp", "CamillaDSP", gh("HEnquist/camilladsp"),
            InventoryCategory.AUDIO, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-24", 1091, Coverage.NONE,
            "Cross-platform IIR and FIR engine for crossovers, convolution and room correction.",
            "A desktop engine under a copyleft licence; it informed the signal-flow vocabulary only, and SonicCore's stage list and bypass model are unchanged by it."
        ),
        RepositoryEntry(
            "werman/noise-suppression-for-voice", "RNNoise-based suppression", gh("werman/noise-suppression-for-voice"),
            InventoryCategory.AUDIO, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 6941, Coverage.NONE,
            "Neural noise suppression for voice as a plugin.",
            "Not eligible, and not needed: SonicCore's noise gate is an existing native stage with its own controls."
        ),
        RepositoryEntry(
            "breakfastquay/rubberband", "Rubber Band", gh("breakfastquay/rubberband"),
            InventoryCategory.AUDIO, "GPL-2.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2025-03-03", 798, Coverage.NONE,
            "Time-stretching and pitch-shifting library for playback speed control.",
            "Audited while considering variable-speed playback; GPL-2.0 forbids bundling it in an MIT application, so the feature stays unimplemented rather than half-shipped."
        ),
        RepositoryEntry(
            "chirlu/soxr", "SoX Resampler", gh("chirlu/soxr"),
            InventoryCategory.AUDIO, "LGPL-2.1-or-later", LicenseCompatibility.WEAK_COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2023-11-14", 184, Coverage.NONE,
            "High-quality one-dimensional sample-rate conversion.",
            "LGPL is compatible with an MIT app only under dynamic-linking and relinking conditions an Android APK cannot guarantee, so it is classed as not bundleable rather than quietly shipped."
        ),
        RepositoryEntry(
            "superpoweredSDK/Low-Latency-Android-iOS-Linux-Windows-tvOS-macOS-Interactive-Audio-Platform",
            "Superpowered Audio SDK", gh("superpoweredSDK/Low-Latency-Android-iOS-Linux-Windows-tvOS-macOS-Interactive-Audio-Platform"),
            InventoryCategory.AUDIO, "proprietary — Master License Agreement",
            LicenseCompatibility.PROPRIETARY,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-14", 1446, Coverage.NONE,
            "Low-latency C++ interactive audio SDK for Android and other platforms (effects, time-stretching, networking).",
            "The repository publishes no licence file and the SDK is governed by Superpowered's own Master License Agreement, so it is ineligible here; SonicCore's latency work stays on its own JNI engine."
        ),

        // ── DEVICE CENTER ──────────────────────────────────────────────────────
        RepositoryEntry(
            "LibChecker/LibChecker", "LibChecker", gh("LibChecker/LibChecker"),
            InventoryCategory.DEVICE, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-23", 7282, Coverage.REFERENCE,
            "Inspection of the native and Java libraries an installed app contains.",
            "Audited for how it reads native library metadata. SonicCore's own native-bridge and ABI cards already report what a third-party app may read, so nothing was taken."
        ),
        RepositoryEntry(
            "VREMSoftwareDevelopment/WiFiAnalyzer", "WiFiAnalyzer", gh("VREMSoftwareDevelopment/WiFiAnalyzer"),
            InventoryCategory.DEVICE, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-03", 5043, Coverage.REFERENCE,
            "Wi-Fi signal analysis with channel graphs and access-point detail.",
            "Studied for the shape of the network card; copyleft, and Android's location gating hides SSID/BSSID from SonicCore anyway, which the network card reports as restricted."
        ),
        RepositoryEntry(
            "zhanghai/MaterialFiles", "Material Files", gh("zhanghai/MaterialFiles"),
            InventoryCategory.DEVICE, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-15", 9316, Coverage.REFERENCE,
            "File manager with storage analysis and root/document-provider browsing.",
            "Storage analysis was compared against SonicCore's storage card; copyleft, and scoped storage limits what either app can enumerate without user-granted tree access."
        ),
        RepositoryEntry(
            "MuntashirAkon/AppManager", "App Manager", gh("MuntashirAkon/AppManager"),
            InventoryCategory.DEVICE, "GPL-3.0-or-later", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-19", 9336, Coverage.REFERENCE,
            "Full package manager: app inventory, components, permissions and per-app data.",
            "Audited for how a third-party package manager enumerates apps. SonicCore's App Manager is its own implementation on the public launcher-intent queries declaration, so it needs no QUERY_ALL_PACKAGES."
        ),
        RepositoryEntry(
            "aosp-mirror/platform_frameworks_base", "AOSP framework mirror", gh("aosp-mirror/platform_frameworks_base"),
            InventoryCategory.DEVICE, "Apache-2.0 (AOSP platform, per-file headers)",
            LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ARCHIVED, "2025-11-12", 10821, Coverage.REFERENCE,
            "Read-only mirror of the Android framework source: audio policy, AudioFlinger and the settings provider.",
            "Read as the authoritative reference for what an ordinary app may call (and what only a system app may) — the basis of every Bounded and Unavailable verdict in docs/feature-matrix.md."
        ),

        // ── SHIZUKU CENTER ─────────────────────────────────────────────────────
        RepositoryEntry(
            "RikkaApps/Shizuku-API", "Shizuku API (official client)", gh("RikkaApps/Shizuku-API"),
            InventoryCategory.SHIZUKU, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.INTEGRATED, PrivilegeRequirement.SHIZUKU,
            Maintenance.ACTIVE, "2025-05-29", 2699, Coverage.PARTIAL,
            "Official client for the elevated bridge: binder hand-off provider, install/running/authorized state, permission request and read-only diagnostics calls.",
            "dev.rikka.shizuku:api + :provider 13.1.5 are real dependencies. ShizukuBridge reports binder state and ShizukuDiagnostics performs the one read-only elevated call; every other operation is recorded as NOT_IMPLEMENTED in docs/shizuku-operations.md."
        ),
        RepositoryEntry(
            "RikkaApps/Shizuku", "Shizuku (manager app)", gh("RikkaApps/Shizuku"),
            InventoryCategory.SHIZUKU, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.SHIZUKU,
            Maintenance.ACTIVE, "2025-06-18", 32137, Coverage.REFERENCE,
            "The elevated-bridge manager the user installs and starts over wireless debugging or root.",
            "Never bundled and never shipped in the APK: the user installs it themselves, which is also why its licence does not propagate into this repository."
        ),
        RepositoryEntry(
            "topjohnwu/libsu", "libsu", gh("topjohnwu/libsu"),
            InventoryCategory.SHIZUKU, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REQUIRES_ROOT, PrivilegeRequirement.ROOT,
            Maintenance.ACTIVE, "2026-03-27", 2122, Coverage.NONE,
            "Root shell access and a privileged service API for apps.",
            "Licence-compatible, and deliberately not added: SonicCore ships no root code path, so root-only work stays outside the app rather than behind a hidden switch."
        ),
        RepositoryEntry(
            "RikkaApps/Sui", "Sui", gh("RikkaApps/Sui"),
            InventoryCategory.SHIZUKU, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.ROOT,
            Maintenance.SLOW, "2024-08-12", 4334, Coverage.NONE,
            "Shizuku-compatible superuser implementation for rooted devices.",
            "Both blockers apply: copyleft and root-only. It is recorded so the elevated-tooling landscape is complete, including which projects are root-only forks of the Shizuku idea."
        ),
        RepositoryEntry(
            "topjohnwu/Magisk", "Magisk", gh("topjohnwu/Magisk"),
            InventoryCategory.SHIZUKU, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.ROOT,
            Maintenance.ACTIVE, "2026-10-25", 63478, Coverage.NONE,
            "Systemless root, module framework and Zygisk injection.",
            "Copyleft and root-only. No module, script or binary is executed or bundled: SonicCore never installs or drives rooting tools."
        ),
        RepositoryEntry(
            "tiann/KernelSU", "KernelSU", gh("tiann/KernelSU"),
            InventoryCategory.SHIZUKU, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.ROOT,
            Maintenance.ACTIVE, "2026-10-25", 19641, Coverage.NONE,
            "Kernel-based root solution with per-app su profiles.",
            "Copyleft and root-only; listed to keep the root-solution comparison honest rather than implying SonicCore could use any of them."
        ),
        RepositoryEntry(
            "bmax121/APatch", "APatch", gh("bmax121/APatch"),
            InventoryCategory.SHIZUKU, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.ROOT,
            Maintenance.ACTIVE, "2026-10-23", 8088, Coverage.NONE,
            "Kernel-patching root solution (KernelPatch + Magisk-style modules).",
            "Copyleft and root-only, same verdict as the other root solutions; nothing is taken or executed."
        ),

        // ── PERMISSIONS & PRIVACY ──────────────────────────────────────────────
        RepositoryEntry(
            "8enet/AppOpsX", "AppOpsX", gh("8enet/AppOpsX"),
            InventoryCategory.PRIVACY, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REQUIRES_SHIZUKU, PrivilegeRequirement.SHIZUKU,
            Maintenance.SLOW, "2020-09-21", 630, Coverage.NONE,
            "Front end for the AppOpsService: reads and writes per-operation app-ops state through an adb or Shizuku shell.",
            "Reading app-ops state is already covered by SonicCore's own permission center. The write path is the blocker: the Shizuku v13 client cannot spawn a shell from an app process, which is why docs/shizuku-operations.md records those operations as NOT_IMPLEMENTED."
        ),
        RepositoryEntry(
            "Exodus-Privacy/exodus-android-app", "εxodus", gh("Exodus-Privacy/exodus-android-app"),
            InventoryCategory.PRIVACY, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2025-11-20", 1056, Coverage.REFERENCE,
            "Tracker detection across installed apps using the Exodus database.",
            "Copyleft, and out of scope by design: SonicCore declares no INTERNET permission, so it cannot fetch the tracker database and captured audio cannot leave the device."
        ),
        RepositoryEntry(
            "permissions-dispatcher/PermissionsDispatcher", "PermissionsDispatcher", gh("permissions-dispatcher/PermissionsDispatcher"),
            InventoryCategory.PRIVACY, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2023-11-10", 11147, Coverage.REFERENCE,
            "Annotation processor that generates the runtime-permission request plumbing.",
            "SonicCore's permission center needs its own declared-vs-granted model and recovery screens, so it calls checkSelfPermission directly; the generator would only duplicate that."
        ),
        RepositoryEntry(
            "seedvault-app/seedvault", "Seedvault", gh("seedvault-app/seedvault"),
            InventoryCategory.PRIVACY, "not detected at the repository root (LICENSES/ directory)",
            LicenseCompatibility.UNVERIFIED,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-16", 1875, Coverage.REFERENCE,
            "AOSP-integrated backup application for apps and settings.",
            "A design reference for configuration backup and export. Nothing may be taken until its licence position is resolved, and SonicCore's own export path uses FileProvider rather than a backup transport."
        ),

        // ── CUSTOMIZATION ──────────────────────────────────────────────────────
        RepositoryEntry(
            "chrisbanes/haze", "Haze", gh("chrisbanes/haze"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 2626, Coverage.NONE,
            "Background blurring for Compose with a documented downlevel fallback.",
            "The audited first candidate if a real blur layer is ever added. It is not a dependency today: LiquidGlassSurface is translucency plus gradient edges on purpose, so low-end devices keep their frame rate."
        ),
        RepositoryEntry(
            "material-foundation/material-color-utilities", "Material Color Utilities", gh("material-foundation/material-color-utilities"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-19", 2293, Coverage.NONE,
            "Material You colour science: tonal palettes and dynamic colour schemes from one seed colour.",
            "The twelve themes and the custom-accent swatch are SonicCore's own palettes today; this is the audited path to generating a full tonal scheme from a user seed colour."
        ),
        RepositoryEntry(
            "google/material-design-icons", "Material Symbols", gh("google/material-design-icons"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-23", 54159, Coverage.NONE,
            "The official Material icon set as vectors and fonts.",
            "Permissive and usable, but not adopted: SonicCore ships its own adaptive cyan/violet monogram and draws its UI glyphs as text, so no icon font is bundled."
        ),
        RepositoryEntry(
            "Kyant0/AndroidLiquidGlass", "Android Liquid Glass", gh("Kyant0/AndroidLiquidGlass"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-08-26", 4288, Coverage.REFERENCE,
            "Compose Multiplatform liquid-glass effect with refraction-style edge treatment.",
            "Studied while rebuilding SonicCore's glass vocabulary; nothing was copied, and LiquidGlassSurface applies the same layering idea with native Compose primitives only."
        ),
        RepositoryEntry(
            "skydoves/Cloudy", "Cloudy", gh("skydoves/Cloudy"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-09-13", 1289, Coverage.REFERENCE,
            "Blur and liquid-glass effects for Compose with GPU acceleration and a CPU fallback.",
            "Compared against Haze and AndroidLiquidGlass for the same job. Reference only: adopting any blur library would be a dependency decision this project has not made."
        ),
        RepositoryEntry(
            "KieronQuinn/MonetCompat", "MonetCompat", gh("KieronQuinn/MonetCompat"),
            InventoryCategory.CUSTOMIZATION, "MIT", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.SLOW, "2022-01-23", 187, Coverage.REFERENCE,
            "App-level Material You palette generation back to Android 5.0, including wallpaper colour extraction.",
            "SonicCore's Dynamic Wallpaper theme reads WallpaperManager.getWallpaperColors itself and states when the platform refuses it; this library was compared and not adopted."
        ),
        RepositoryEntry(
            "LawnchairLauncher/lawnchair", "Lawnchair", gh("LawnchairLauncher/lawnchair"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0 (LICENSE.txt)", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.UNSUPPORTED, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 13778, Coverage.NONE,
            "Customizable home screen with icon, grid and theme packs.",
            "A home app cannot be embedded in another app — Android requires the user to select a launcher — so no launcher project can be integrated into SonicCore, whatever its licence."
        ),
        RepositoryEntry(
            "NeoApplications/Neo-Launcher", "Neo Launcher", gh("NeoApplications/Neo-Launcher"),
            InventoryCategory.CUSTOMIZATION, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-09-26", 2152, Coverage.NONE,
            "Launcher with icon-pack support and an AOSP-style app drawer.",
            "Copyleft and a home app: neither can be used here. Listed so the launcher comparison shows both blockers rather than only the licence."
        ),
        RepositoryEntry(
            "MM2-0/Kvaesitso", "Kvaesitso", gh("MM2-0/Kvaesitso"),
            InventoryCategory.CUSTOMIZATION, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-22", 5306, Coverage.NONE,
            "Search-focused launcher with widget and icon-pack support.",
            "Copyleft and a home app; nothing taken."
        ),
        RepositoryEntry(
            "FossifyOrg/Launcher", "Fossify Launcher", gh("FossifyOrg/Launcher"),
            InventoryCategory.CUSTOMIZATION, "GPL-3.0", LicenseCompatibility.COPYLEFT,
            IntegrationStatus.INCOMPATIBLE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-20", 718, Coverage.NONE,
            "Simple, customizable launcher and app drawer.",
            "Copyleft and a home app; nothing taken."
        ),
        RepositoryEntry(
            "google/fonts", "Google Fonts", gh("google/fonts"),
            InventoryCategory.CUSTOMIZATION, "not detected at the repository root (per-family OFL files)",
            LicenseCompatibility.UNVERIFIED,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 20680, Coverage.REFERENCE,
            "The Google Fonts catalogue, including variable fonts.",
            "A font picker would need per-family OFL attribution, and the repository publishes no single root licence to check; SonicCore uses the system font stack instead of bundling a family."
        ),
        RepositoryEntry(
            "patrykandpatrick/vico", "Vico", gh("patrykandpatrick/vico"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 3198, Coverage.NONE,
            "Extensible chart library for Compose (line, column, candle and more).",
            "The spectrum, meters and response curves are drawn with SonicCore's own Canvas code; Vico is the audited option if the number of chart types ever justifies a dependency."
        ),
        RepositoryEntry(
            "PhilJay/MPAndroidChart", "MPAndroidChart", gh("PhilJay/MPAndroidChart"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-09", 38190, Coverage.REFERENCE,
            "The classic View-based chart library.",
            "Kept as the alternative comparison against Vico: it targets the View system, not Compose, so it would need an interop wrapper SonicCore has no reason to add."
        ),
        RepositoryEntry(
            "airbnb/lottie-android", "Lottie", gh("airbnb/lottie-android"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-02-15", 35757, Coverage.REFERENCE,
            "Renders After Effects animations natively from JSON.",
            "The splash is drawn in Compose and needs no animation asset; adopting Lottie would add a dependency and a resource to keep in sync for no visual gain."
        ),
        RepositoryEntry(
            "google/accompanist", "Accompanist", gh("google/accompanist"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2025-08-18", 7849, Coverage.REFERENCE,
            "Compose extension libraries (permissions, pager, flow layout and others).",
            "Its libraries are being folded into AndroidX, so depending on the standalone project would add churn; SonicCore uses Compose and AndroidX directly."
        ),
        RepositoryEntry(
            "coil-kt/coil", "Coil", gh("coil-kt/coil"),
            InventoryCategory.CUSTOMIZATION, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.READY_TO_INTEGRATE, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 11951, Coverage.NONE,
            "Coroutine-based image loading for Android and Compose, with artwork and palette support.",
            "The audited choice for album artwork in the player if artwork ever ships; today the player shows track information without loading images, so no image loader is bundled."
        ),

        // ── SETTINGS ───────────────────────────────────────────────────────────
        RepositoryEntry(
            "androidx/androidx", "AndroidX / Jetpack Compose", gh("androidx/androidx"),
            InventoryCategory.SETTINGS, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.INTEGRATED, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-25", 6121, Coverage.ADOPTED,
            "core-ktx, appcompat, Compose BOM + Material3, activity-compose and lifecycle-runtime-ktx.",
            "Declared dependencies in app/build.gradle.kts and the foundation of every screen in this app; tools/license_check.py reads their licences from the resolved dependency graph."
        ),
        RepositoryEntry(
            "google/gson", "Gson", gh("google/gson"),
            InventoryCategory.SETTINGS, "Apache-2.0", LicenseCompatibility.PERMISSIVE,
            IntegrationStatus.REFERENCE_ONLY, PrivilegeRequirement.NONE,
            Maintenance.ACTIVE, "2026-10-24", 24237, Coverage.REFERENCE,
            "JSON serialization for Java and Kotlin objects.",
            "SonicCore's preset import/export and report files use Android's platform JSON implementation, which needs no extra dependency."
        )
    )

    /** Total audited projects. */
    val auditedCount: Int get() = all.size

    /** Projects whose licence permits use inside this MIT application. */
    fun compatible(): List<RepositoryEntry> =
        all.filter { it.compatibility == LicenseCompatibility.PERMISSIVE }

    /** Audited, licence-compatible and still awaiting an implementation batch. */
    fun readyToIntegrate(): List<RepositoryEntry> =
        all.filter { it.status == IntegrationStatus.READY_TO_INTEGRATE }

    /** Projects SonicCore ships or declares today. */
    fun integrated(): List<RepositoryEntry> =
        all.filter { it.status == IntegrationStatus.INTEGRATED }

    fun forCategory(category: InventoryCategory): List<RepositoryEntry> =
        all.filter { it.category == category }

    /** How many entries carry each status, in declaration order. */
    fun countByStatus(): List<Pair<IntegrationStatus, Int>> =
        IntegrationStatus.entries.map { status -> status to all.count { it.status == status } }

    /** How many entries carry each licence verdict, in declaration order. */
    fun countByCompatibility(): List<Pair<LicenseCompatibility, Int>> =
        LicenseCompatibility.entries.map { verdict -> verdict to all.count { it.compatibility == verdict } }

    /**
     * Filter for the Repository Manager: a free-text query plus an optional
     * category and status. A blank query keeps everything, so the screen opens
     * on the full inventory.
     */
    fun search(
        query: String = "",
        category: InventoryCategory? = null,
        status: IntegrationStatus? = null
    ): List<RepositoryEntry> {
        val trimmed = query.trim()
        return all.filter { entry ->
            (category == null || entry.category == category) &&
                (status == null || entry.status == status) &&
                (trimmed.isEmpty() || entry.searchText.contains(trimmed, ignoreCase = true))
        }
    }

    /**
     * The compliance rule as code: a project may only be reported as integrated
     * when its licence permits it. `RepositoryInventoryTest` asserts this over
     * the whole inventory, so a new entry cannot quietly break it.
     */
    fun isIntegrable(entry: RepositoryEntry): Boolean =
        entry.compatibility == LicenseCompatibility.PERMISSIVE
}
