import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
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

val GLB_MAGIC = 0x46546C67
val GLB_JSON_CHUNK = 0x4E4F534A

private fun ByteArrayOutputStream.writeLeInt(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

/**
 * Legacy-only material patch kept for rollback validation. v12 realistic bodies deliberately retain
 * their authored skin texture/PBR maps; destroying those maps would defeat the model upgrade.
 */
@Suppress("UNCHECKED_CAST")
private fun bakeLegacyMedicalMaterial(file: File) {
    val source = file.readBytes()
    val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
    check(input.remaining() >= 20) { "ALMI legacy body GLB is truncated" }
    check(input.int == GLB_MAGIC) { "ALMI legacy body asset is not a GLB" }
    check(input.int == 2) { "ALMI legacy body GLB must be version 2" }
    input.int

    var jsonChunk: ByteArray? = null
    val preservedChunks = mutableListOf<Pair<Int, ByteArray>>()
    while (input.remaining() >= 8) {
        val chunkLength = input.int
        val chunkType = input.int
        check(chunkLength >= 0 && chunkLength <= input.remaining()) { "Invalid ALMI legacy GLB chunk" }
        val payload = ByteArray(chunkLength)
        input.get(payload)
        if (chunkType == GLB_JSON_CHUNK) jsonChunk = payload else preservedChunks += chunkType to payload
    }

    val rawJson = String(checkNotNull(jsonChunk), StandardCharsets.UTF_8)
        .trimEnd(' ', '\u0000', '\n', '\r', '\t')
    val document = JsonSlurper().parseText(rawJson) as MutableMap<String, Any?>
    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>>
        ?: error("Legacy ALMI GLB has no materials")
    val skinMaterial = materials.firstOrNull { it["name"] == "Skin" }
        ?: error("Legacy ALMI Skin material was not found")

    skinMaterial["pbrMetallicRoughness"] = linkedMapOf<String, Any>(
        "baseColorFactor" to listOf(0.82, 0.91, 1.00, 1.0),
        "metallicFactor" to 0.0,
        "roughnessFactor" to 0.54,
    )
    skinMaterial["emissiveFactor"] = listOf(0.010, 0.020, 0.038)
    skinMaterial["doubleSided"] = false
    skinMaterial["alphaMode"] = "OPAQUE"
    skinMaterial.remove("alphaCutoff")
    skinMaterial.remove("normalTexture")
    skinMaterial.remove("occlusionTexture")
    skinMaterial.remove("emissiveTexture")
    skinMaterial.remove("extensions")

    val nodes = document["nodes"] as? MutableList<MutableMap<String, Any?>>
        ?: error("Legacy ALMI GLB has no nodes")
    nodes.firstOrNull { it["name"] == "LeftUpperArm" }?.set("rotation", listOf(0.0, 0.0, 0.5, 0.8660254))
    nodes.firstOrNull { it["name"] == "RightUpperArm" }?.set("rotation", listOf(0.0, 0.0, -0.5, 0.8660254))

    val encodedJson = JsonOutput.toJson(document).toByteArray(StandardCharsets.UTF_8)
    val paddedJsonSize = (encodedJson.size + 3) and -4
    val paddedJson = ByteArray(paddedJsonSize) { 0x20.toByte() }
    encodedJson.copyInto(paddedJson)

    val totalLength = 12 + 8 + paddedJson.size + preservedChunks.sumOf { 8 + it.second.size }
    val output = ByteArrayOutputStream(totalLength)
    output.writeLeInt(GLB_MAGIC)
    output.writeLeInt(2)
    output.writeLeInt(totalLength)
    output.writeLeInt(paddedJson.size)
    output.writeLeInt(GLB_JSON_CHUNK)
    output.write(paddedJson)
    preservedChunks.forEach { (type, payload) ->
        output.writeLeInt(payload.size)
        output.writeLeInt(type)
        output.write(payload)
    }
    file.writeBytes(output.toByteArray())
}

data class Almi3dAsset(
    val relativePath: String,
    val remoteUrl: String,
    val expectedSize: Long,
    val patchLegacy: Boolean = false,
)

val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v12-3d-assets").get().asFile

// The v12 Body Map uses two compact, textured, full-body MPFB2/MakeHuman exports from vsim.
// They keep authored 1024px skin/PBR data and the game_engine rig. The old 23MB HM08 remains only
// as an internal rollback asset until v12 device validation is complete.
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
    Almi3dAsset(
        relativePath = "almi3d/almi_humanoid.glb",
        remoteUrl = "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base.glb",
        expectedSize = 23_004_332L,
        patchLegacy = true,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    outputs.upToDateWhen { false }
    doLast {
        almi3dAssets.forEach { asset ->
            val target = File(almi3dGeneratedAssetsDir, asset.relativePath)
            val pristine = File(target.parentFile, "${target.name}.source")
            target.parentFile.mkdirs()

            if (!pristine.exists() || pristine.length() != asset.expectedSize) {
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
                if (pristine.exists()) pristine.delete()
                check(temporary.renameTo(pristine)) { "Could not cache pristine ${asset.relativePath}" }
            }

            pristine.copyTo(target, overwrite = true)
            if (asset.patchLegacy) bakeLegacyMedicalMaterial(target)
            check(target.length() > 1_000_000L) { "${asset.relativePath} is unexpectedly small" }
        }

        File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt").apply {
            parentFile.mkdirs()
            writeText(
                "ALMI v12 3D ASSETS\n\n" +
                    "Body Map female: vsim packages/assets/library/human.glb, pinned repository commit 3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91.\n" +
                    "Body Map male: vsim packages/assets/library/man.glb, same pinned commit.\n" +
                    "These realistic rigged bodies are generated with MPFB2/MakeHuman; vsim CREDITS documents the generated humans and MakeHuman system skin assets as CC0.\n" +
                    "v12 deliberately preserves their embedded skin/PBR maps and game_engine skeleton.\n\n" +
                    "Avatar lite and legacy rollback body originate from MakeHuman HM08 source data in gokulsenthilkumar3/Ultimate (CC0 source family).\n" +
                    "The legacy 23MB body remains packaged temporarily for rollback/device comparison and is not the v12 Body Map target.\n"
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
