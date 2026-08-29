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
    almiSigningStore.writeBytes(
        Base64.getMimeDecoder().decode(encodedSigningStore.readText().trim())
    )
}

// ALMI v7 deliberately ships its high-fidelity digital-human pack locally. The user prioritizes
// realism and reliability over APK size, so the renderer never depends on a runtime model download.
// Multiple hair meshes are bundled so Create Your Avatar can switch actual 3D geometry, not just
// change a 2D thumbnail or prompt label.
val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v7-assets").get().asFile
val almi3dModels = listOf(
    Triple(
        "almi3d/vitruvian_body.glb",
        "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_body.glb",
        6_879_364L,
    ),
    Triple(
        "almi3d/vitruvian_head.glb",
        "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_head.glb",
        10_189_832L,
    ),
    Triple(
        "almi3d/vitruvian_hair_rigged.glb",
        "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_hair_rigged.glb",
        37_694_332L,
    ),
    Triple(
        "almi3d/vitruvian_hair.glb",
        "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/vitruvian_hair.glb",
        21_189_248L,
    ),
    Triple(
        "almi3d/hairtool_cards.glb",
        "https://raw.githubusercontent.com/ibrews/VitruvianGodot/main/godot_project/hairtool_cards.glb",
        14_839_096L,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    doLast {
        val root = almi3dGeneratedAssetsDir
        almi3dModels.forEach { (relativePath, remoteUrl, expectedSize) ->
            val target = File(root, relativePath)
            if (!target.exists() || target.length() != expectedSize) {
                target.parentFile.mkdirs()
                val temporary = File(target.parentFile, "${target.name}.download")
                val connection = URI(remoteUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "ALMI-Android-v7-build")
                }
                connection.getInputStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                check(temporary.length() == expectedSize) {
                    "Unexpected size for $relativePath: ${temporary.length()} (expected $expectedSize)"
                }
                if (target.exists()) target.delete()
                check(temporary.renameTo(target)) { "Could not finalize $relativePath" }
            }
        }
        val notice = File(root, "almi3d/ASSET_NOTICE.txt")
        notice.parentFile.mkdirs()
        notice.writeText(
            "ALMI v7 digital-human assets are sourced from ibrews/VitruvianGodot.\n" +
                "The upstream project states that the digital human is fully CC0 / EULA-free.\n" +
                "Source: https://github.com/ibrews/VitruvianGodot\n"
        )
    }
}

// Generated assets are part of the main source set, so every consumer that models assets must
// explicitly depend on the producer under Gradle 9 strict task validation. This includes both the
// packaging merge tasks and AGP's lint model/vital tasks.
tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareAlmi3dAssets)
}

android {
    namespace = "com.almi.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.almi.ai"
        minSdk = 29
        targetSdk = 36
        versionCode = 30_000 + ciRunNumber
        versionName = "0.3.$ciRunNumber"
        vectorDrawables.useSupportLibrary = true
    }

    androidResources {
        localeFilters += listOf("en", "ar")
        noCompress += "glb"
    }

    // AGP 9 rejects Provider instances in SourceSet APIs. This is a concrete static directory;
    // merge/lint consumers depend on prepareAlmi3dAssets above, so generated GLBs are ready first.
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
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("almiDev")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    implementation("io.github.sceneview:sceneview:4.33.0")

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.jsoup)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation(libs.androidx.ui.tooling)
}
