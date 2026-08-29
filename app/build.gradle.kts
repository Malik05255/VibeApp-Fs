import java.io.File
import java.net.URI
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val encodedSigningStore = rootProject.file(".github/almi_ai_dev_keystore.b64")
val generatedSigningDir = rootProject.layout.buildDirectory.dir("generated/almi-signing").get().asFile
val almiSigningStore = File(generatedSigningDir, "almi-ai-dev.p12")

if (!almiSigningStore.exists() && encodedSigningStore.exists()) {
    generatedSigningDir.mkdirs()
    almiSigningStore.writeBytes(Base64.getMimeDecoder().decode(encodedSigningStore.readText().trim()))
}

// BODY MAP is the only Filament surface in ALMI. A single complete human GLB avoids multi-asset
// lifecycle complexity and contains named anthropometric morph targets.
val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v8-body-assets").get().asFile
val almi3dModels = listOf(
    Triple(
        "almi3d/almi_humanoid.glb",
        "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base-lite.glb",
        5_278_868L,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    doLast {
        almi3dModels.forEach { (relativePath, remoteUrl, expectedSize) ->
            val target = File(almi3dGeneratedAssetsDir, relativePath)
            if (!target.exists() || target.length() != expectedSize) {
                target.parentFile.mkdirs()
                val temporary = File(target.parentFile, "${target.name}.download")
                val connection = URI(remoteUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "ALMI-Android-v8-body-build")
                }
                connection.getInputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                check(temporary.length() == expectedSize) {
                    "Unexpected size for $relativePath: ${temporary.length()} (expected $expectedSize)"
                }
                if (target.exists()) target.delete()
                check(temporary.renameTo(target)) { "Could not finalize $relativePath" }
            }
        }
        val notice = File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt")
        notice.parentFile.mkdirs()
        notice.writeText(
            "ALMI BODY MAP humanoid-base-lite.glb is generated from MakeHuman HM08 source data.\n" +
                "MakeHuman bundled assets are CC0 1.0 Universal. Runtime asset source: gokulsenthilkumar3/Ultimate.\n"
        )
    }
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) || it.name.contains("Lint", ignoreCase = true)
}.configureEach { dependsOn(prepareAlmi3dAssets) }

android {
    namespace = "com.almi.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.almi.ai"
        minSdk = 29
        targetSdk = 36
        versionCode = 30_000 + ciRunNumber
        versionName = "0.4.$ciRunNumber"
        vectorDrawables.useSupportLibrary = true
    }

    androidResources {
        localeFilters += listOf("en", "ar")
        noCompress += "glb"
    }

    sourceSets.getByName("main").assets.srcDir(almi3dGeneratedAssetsDir)

    signingConfigs {
        create("almiDev") {
            storeFile = almiSigningStore
            storePassword = "almi-dev-2026"
            keyAlias = "almi_ai_dev"
            keyPassword = "almi-dev-2026"
            storeType = "PKCS12"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("almiDev")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "META-INF/INDEX.LIST",
        "META-INF/io.netty.versions.properties",
    )

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)

    // Filament is intentionally restricted to BodyMeasurementActivity.
    implementation("com.google.android.filament:filament-android:1.71.0")
    implementation("com.google.android.filament:gltfio-android:1.71.0")
    implementation("com.google.android.filament:filament-utils-android:1.71.0")

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.jsoup)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation(libs.androidx.ui.tooling)
}
