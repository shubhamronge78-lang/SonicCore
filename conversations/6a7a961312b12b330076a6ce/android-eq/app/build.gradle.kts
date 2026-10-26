plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.soniccore.app"
    compileSdk = 34

    // CI (GitHub Actions) runs on a fresh VM every time, so without a fixed keystore
    // AGP auto-generates a brand-new random debug key each build. That means every
    // "new" APK has a different signature than the last install, and Android's
    // package installer silently refuses to update — you end up stuck on an old,
    // stale build without any clear error. Pinning a checked-in keystore fixes that
    // for good: every future build is signed identically, so installs always update
    // cleanly (no more "uninstall the old app first").
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore.p12")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "PKCS12"
        }
    }

    defaultConfig {
        applicationId = "com.soniccore.app"
        minSdk = 26
        targetSdk = 34
        // Tie versionCode/versionName to the CI run so it's easy to confirm on-device
        // (Settings > Apps > Neon EQ > version) that you're actually running the
        // build you think you're running.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
        versionName = "3.6.0"
        // On-device tap tests (app/src/androidTest): the only place the launcher's
        // interactions can actually be pressed. Run with
        // `./gradlew :app:connectedDebugAndroidTest` on a connected device.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    // Build #116: native NeonDsp engine (playback-capture pipeline)
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            // CMake toolchain file is injected via the cmake wrapper script
            // (/opt/soniccore-tools/cmake-3.22.6/bin/cmake) which adds
            // -DCMAKE_TOOLCHAIN_FILE and -DCMAKE_SYSTEM_NAME=Linux after AGP's
            // arguments, overriding AGP's NDK x86_64 toolchain and Android platform.
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    // The rendered-screen test (app/src/test/.../ScreenRenderSnapshotTest.kt)
    // runs the real Compose screens on the JVM under Robolectric and writes PNGs
    // of what they actually paint, because this project has no emulator and the
    // pixel result otherwise cannot be checked at all. It needs the app's real
    // resources (the launcher theme, the glass background) for that.
    // Test-only: nothing here reaches the shipped APK.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Official Shizuku API (RikkaApps/Shizuku-API, MIT). `api` holds the Shizuku
    // client (binder lifecycle, permission requests, ShizukuBinderWrapper) and
    // `provider` is the ContentProvider Shizuku uses to hand the binder to apps.
    // Both are required: without `provider` the binder is never received.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Build #136 hardening (spec 26): real JVM unit tests for preset validation.
    testImplementation("junit:junit:4.13.2")
    // org.json runs on the JVM for tests (framework stubs throw on-device mocks)
    testImplementation("org.json:json:20240303")
    // Device-free rendering: Robolectric runs the app's real Compose screens on
    // the JVM so `ScreenRenderSnapshotTest` can capture their pixels as PNGs.
    // Test-only — neither artifact is packaged into the APK.
    testImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.robolectric:robolectric:4.11.1")
    // On-device tap tests for the launcher (app/src/androidTest) — Compose UI
    // testing needs its own BOM and the manifest that supplies the host activity.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
