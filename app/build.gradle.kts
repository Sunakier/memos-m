import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

val gitShortHash: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

val gitTag: Provider<String> = providers.exec {
    commandLine("git", "describe", "--tags", "--abbrev=0")
}.standardOutput.asText.map { it.trim() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("androidx.room") version libs.versions.roomRuntime.get()

    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "org.example.memosm"
    compileSdk = 37
    compileSdkMinor = 1
    
    defaultConfig {
        applicationId = "top.lazyerpaper.memosm"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GIT_HASH", "\"${gitShortHash.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Local signing credentials come from local.properties (gitignored); CI keeps using env vars.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { ins -> this@apply.load(ins) }
    }
    // Non-blank variant for identity fields (path/alias): a blank value is a
    // placeholder, so fall through to the env var. Password fields below use
    // localOrEnv directly - a blank password is meaningful (passwordless
    // keystore) and must not fall through.
    fun localOrEnvNonBlank(prop: String, env: String): String? =
        localProps.getProperty(prop)?.takeIf { it.isNotBlank() }
            ?: providers.environmentVariable(env).orNull
    fun localOrEnv(prop: String, env: String): String? =
        localProps.getProperty(prop) ?: providers.environmentVariable(env).orNull

    signingConfigs {
        create("release") {
            storeFile = localOrEnvNonBlank("keystore.path", "KEYSTORE_PATH")
                ?.let {
                    // Relative values in local.properties are repo-root
                    // relative (mirroring the fallback below); file() alone
                    // would resolve them against the app/ module dir.
                    val moduleFile = file(it)
                    if (moduleFile.exists()) moduleFile else rootProject.file(it)
                } ?: rootProject.file("app/release.keystore").takeIf { it.exists() }

            storePassword = localOrEnv("keystore.password", "KEYSTORE_PASSWORD")
            keyAlias = localOrEnvNonBlank("keystore.alias", "KEY_ALIAS") ?: "key0"
            keyPassword = localOrEnv("keystore.keyPassword", "KEY_PASSWORD")
                ?: localOrEnv("keystore.password", "KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug-${gitShortHash.get()}"
            manifestPlaceholders["appLabel"] = "MemosM (Debug)"
        }
        create("canary") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // x86/x86_64 devices are effectively nonexistent outside emulators
            // (which run debug builds); shipping them doubles the MapLibre
            // native library size for no real users.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            applicationIdSuffix = ".canary"

            val timestamp = SimpleDateFormat("yyyyMMddHHmm").format(Date())
            versionNameSuffix = "-canary-$timestamp-${gitShortHash.get()}"

            manifestPlaceholders["appLabel"] = "MemosM"
            signingConfig = signingConfigs.getByName("release")
        }
        create("insider") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            applicationIdSuffix = ".insider"

            // Tag format is likely v1.1.1, so we strip the 'v' prefix
            // versionName assignment moved to applicationVariants.all block below
            versionNameSuffix = "-insider"

            manifestPlaceholders["appLabel"] = "MemosM (Insider)"
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appLabel"] = "MemosM"
        }
    }
    androidResources {
        // Only the UI languages this project maintains; drops library translations.
        localeFilters += listOf("en", "de", "ja", "ko", "pl", "zh-rCN", "zh-rTW")
    }

    packaging {
        jniLibs {
            // Store native libraries deflated; saves ~16 MB in the universal APK.
            // Trade-off: install extracts them, so install size/time grows slightly.
            useLegacyPackaging = true
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets {
        named("main") {
        }
        named("androidTest") {
            assets.directories.add("$projectDir/schemas")
        }
        named("canary") {
            res.directories.add(("src/canary/res"))
        }
        named("insider") {
            res.directories.add(("src/insider/res"))
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

androidComponents {
    onVariants(selector().withBuildType("insider")) { variant ->
        variant.outputs.forEach { output ->
            output.versionName.set(gitTag.map { it.removePrefix("v") })
        }
    }
}



dependencies {
    implementation(libs.msgpack.core)
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    // ----------------------------
    // Koin
    // ----------------------------
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // ----------------------------
    // Android / Compose
    // ----------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.kolor)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)

    // REST Networking (Retrofit)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.java.net.cookiejar)


    // Location
    implementation(libs.androidx.collection)
    implementation(libs.google.play.services.location)
    implementation(libs.maplibre.android)

    // About Libraries (OSS Licenses)
    implementation(libs.aboutlibraries.compose)

    // Markdown
    implementation(libs.markdown)
    implementation(libs.highlights)
    implementation(libs.androidmath)

    // Media
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    // Tests
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // The Compose test activity shares the app's classloader with Espresso.
    // Match its test core/monitor version to the instrumentation APK.
    debugImplementation(libs.androidx.core)
    testImplementation(libs.testcontainers)
    testImplementation(libs.jna)
    testImplementation(libs.slf4j.simple)


    // ROOM
    implementation(libs.androidx.room.runtime)

    // Cross-process durable sync retries
    implementation(libs.androidx.work.runtime.ktx)

    // If this project uses any Kotlin source, use Kotlin Symbol Processing (KSP)
    // See Add the KSP plugin to your project
    ksp(libs.androidx.room.compiler)

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor(libs.androidx.room.compiler)

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)

    // optional - Test helpers
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.room.testing)

    // optional - Paging 3 Integration
    implementation(libs.androidx.room.paging)

}
