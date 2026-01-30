import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date

val gitShortHash: Provider<String> = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.buf)
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}

android {
    namespace = "org.example.memosm"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.example.memosm"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("KEYSTORE_PATH")
            storeFile = keystorePath.map { file(it) }.orNull

            storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("KEY_ALIAS").orElse("key0").orNull
            keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug-${gitShortHash.get()}"
            manifestPlaceholders["appLabel"] = "MemosM (Debug)"
        }
        create("canary") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".canary"
            
            val timestamp = SimpleDateFormat("yyyyMMddHHmm").format(Date())
            versionNameSuffix = "-canary-$timestamp-${gitShortHash.get()}"
            
            manifestPlaceholders["appLabel"] = "MemosM"
            signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["appLabel"] = "MemosM"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        named("main") {
            java.directories.add(("build/bufbuild/generated/java"))
        }
        named("canary") {
            res.directories.add(("src/canary/res"))
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        // Add the buf generated directory as a static source directory
        // This replaces: sourceSets.named("main") { java.directories.add(...) }
        variant.sources.java?.addStaticSourceDirectory("build/bufbuild/generated/java")
    }
}

buf {
    configFileLocation = file("buf.yaml")
    generate {
        includeImports = false
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("bufGenerate")
}

dependencies {
    coreLibraryDesugaring(libs.android.desugarJdkLibs)

    // ----------------------------
    // Android / Compose
    // ----------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // ----------------------------
    // REST Networking (Retrofit)
    // ----------------------------
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // ----------------------------
    // Connect RPC / Protobuf dependencies
    // ----------------------------
    implementation(libs.connect.kotlin.okhttp)
    // Java specific dependencies.
    implementation(libs.connect.kotlin.google.java.ext)
    implementation(libs.protobuf.java)
    implementation(libs.google.common.protos)

    // ----------------------------
    // Other app deps
    // ----------------------------
    implementation(libs.google.play.services.location)

    implementation(libs.multiplatform.markdown.renderer.android)
    implementation(libs.multiplatform.markdown.renderer.m3)
    implementation(libs.multiplatform.markdown.renderer.coil3)
    implementation(libs.multiplatform.markdown.renderer.code)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.datasource.okhttp)

    // ----------------------------
    // Tests
    // ----------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    // ROOM

    implementation(libs.androidx.room.runtime)

    // If this project uses any Kotlin source, use Kotlin Symbol Processing (KSP)
    // See Add the KSP plugin to your project
    ksp(libs.androidx.room.compiler)

    // If this project only uses Java source, use the Java annotationProcessor
    // No additional plugins are necessary
    annotationProcessor(libs.androidx.room.compiler)

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)

    // optional - RxJava2 support for Room
    implementation(libs.androidx.room.rxjava2)

    // optional - RxJava3 support for Room
    implementation(libs.androidx.room.rxjava3)

    // optional - Guava support for Room, including Optional and ListenableFuture
    implementation(libs.androidx.room.guava)

    // optional - Test helpers
    testImplementation(libs.androidx.room.testing)

    // optional - Paging 3 Integration
    implementation(libs.androidx.room.paging)

}
