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

data class Almi3dAsset(
    val relativePath: String,
    val remoteUrl: String,
    val expectedSize: Long,
)

val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v12-3d-assets").get().asFile

// v12 ships only assets reachable from the new Worlds experience. The former 23MB HM08 body was
// used by legacy measurement activities and is intentionally excluded from the release package.
val almi3dAssets = listOf(
    Almi3dAsset(
        relativePath = "almi3d/almi_body_female_v12.glb",
        remoteUrl = "https://raw.githubusercontent.com/kunalkushwaha/vsim/3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91/packages/assets/library/human.glb",
        expectedSize = 2_767_576L,
    ),
    Almi3dAsset(
        relativePath = "almi3d/almi_body_male_v12.glb",
        remoteUrl = "https://raw.githubusercontent.com/kunalkushwaha/vsim/3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91/packages/assets/library/man.glb",
        expectedSize = 2_889_028L,
    ),
    Almi3dAsset(
        relativePath = "almi3d/almi_avatar_lite.glb",
        remoteUrl = "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base-lite.glb",
        expectedSize = 5_278_868L,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    outputs.upToDateWhen { false }
    doLast {
        // Always recreate the generated directory so a removed model cannot survive from a previous
        // local build cache and silently re-enter the APK.
        if (almi3dGeneratedAssetsDir.exists()) almi3dGeneratedAssetsDir.deleteRecursively()
        almi3dGeneratedAssetsDir.mkdirs()

        almi3dAssets.forEach { asset ->
            val target = File(almi3dGeneratedAssetsDir, asset.relativePath)
            target.parentFile.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.download")
            if (temporary.exists()) temporary.delete()

            val connection = URI(asset.remoteUrl).toURL().openConnection().apply {
                connectTimeout = 30_000
                readTimeout = 180_000
                setRequestProperty("User-Agent", "ALMI-Android-v12-3d-build")
            }
            connection.getInputStream().use { inputStream ->
                temporary.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }
            check(temporary.length() == asset.expectedSize) {
                "Unexpected size for ${asset.relativePath}: ${temporary.length()} (expected ${asset.expectedSize})"
            }
            check(temporary.renameTo(target)) { "Could not install ${asset.relativePath}" }
            check(target.length() > 1_000_000L) { "${asset.relativePath} is unexpectedly small" }
        }

        File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt").apply {
            parentFile.mkdirs()
            writeText(
                "ALMI v12 3D ASSETS\n\n" +
                    "Body Map female: vsim packages/assets/library/human.glb, pinned repository commit 3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91.\n" +
                    "Body Map male: vsim packages/assets/library/man.glb, same pinned commit.\n" +
                    "These realistic rigged bodies are generated with MPFB2/MakeHuman; vsim CREDITS documents the generated humans and MakeHuman system skin assets as CC0.\n" +
                    "v12 preserves their embedded skin/PBR maps, game_engine skeleton, and animation clips.\n\n" +
                    "Avatar lite currently originates from MakeHuman HM08 source data in gokulsenthilkumar3/Ultimate (CC0 source family) while the v12 avatar model transition is validated.\n" +
                    "The former 23MB legacy measurement body is not packaged in v12.\n"
            )
        }
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
        versionName = "0.7.$ciRunNumber"
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
