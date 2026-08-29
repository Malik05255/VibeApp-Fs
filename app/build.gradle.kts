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

/** Preserve source geometry/rig/morphs while applying ALMI's smooth clinical appearance. */
@Suppress("UNCHECKED_CAST")
private fun bakeAlmiMedicalMaterial(file: File) {
    val source = file.readBytes()
    val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
    check(input.remaining() >= 20) { "ALMI body GLB is truncated" }
    check(input.int == GLB_MAGIC) { "ALMI body asset is not a GLB" }
    check(input.int == 2) { "ALMI body GLB must be version 2" }
    input.int

    var jsonChunk: ByteArray? = null
    val preservedChunks = mutableListOf<Pair<Int, ByteArray>>()
    while (input.remaining() >= 8) {
        val chunkLength = input.int
        val chunkType = input.int
        check(chunkLength >= 0 && chunkLength <= input.remaining()) { "Invalid ALMI GLB chunk" }
        val payload = ByteArray(chunkLength)
        input.get(payload)
        if (chunkType == GLB_JSON_CHUNK) jsonChunk = payload else preservedChunks += chunkType to payload
    }

    val jsonBytes = checkNotNull(jsonChunk) { "ALMI GLB is missing its JSON chunk" }
    val rawJson = String(jsonBytes, StandardCharsets.UTF_8)
        .trimEnd(' ', '\u0000', '\n', '\r', '\t')
    val document = JsonSlurper().parseText(rawJson) as MutableMap<String, Any?>

    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>>
        ?: error("ALMI GLB has no materials")
    val skinMaterial = materials.firstOrNull { it["name"] == "Skin" }
        ?: error("ALMI GLB Skin material was not found")

    // The previous build kept the source normal/AO/metallic textures. On the test handset those
    // textures produced the black torso and hard white ribbing visible in the screenshot. HM08 has
    // enough geometric density to shade smoothly without those maps, so the measurement twin now
    // uses clean geometry-driven lighting. This is also cheaper at runtime and avoids texture
    // sampling artifacts while rotating/zooming.
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

    // A true tailoring A-pose: arms are lowered substantially from the source T-pose so the body
    // fits a portrait phone without shrinking the torso and so sleeve/shoulder measurements read
    // naturally. 60 degrees around Z leaves a clear gap between arm and torso.
    val nodes = document["nodes"] as? MutableList<MutableMap<String, Any?>>
        ?: error("ALMI GLB has no nodes")
    nodes.firstOrNull { it["name"] == "LeftUpperArm" }?.set(
        "rotation",
        listOf(0.0, 0.0, 0.5, 0.8660254),
    )
    nodes.firstOrNull { it["name"] == "RightUpperArm" }?.set(
        "rotation",
        listOf(0.0, 0.0, -0.5, 0.8660254),
    )

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
    val result = output.toByteArray()
    check(result.size == totalLength) { "Could not rebuild ALMI GLB" }
    file.writeBytes(result)
}

val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v8-body-assets").get().asFile
val almi3dModels = listOf(
    Triple(
        "almi3d/almi_humanoid.glb",
        "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base.glb",
        23_004_332L,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    outputs.upToDateWhen { false }
    doLast {
        almi3dModels.forEach { (relativePath, remoteUrl, expectedSize) ->
            val target = File(almi3dGeneratedAssetsDir, relativePath)
            val pristine = File(target.parentFile, "${target.name}.source")
            target.parentFile.mkdirs()

            if (!pristine.exists() || pristine.length() != expectedSize) {
                val temporary = File(target.parentFile, "${target.name}.download")
                val connection = URI(remoteUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    setRequestProperty("User-Agent", "ALMI-Android-v8-body-build")
                }
                connection.getInputStream().use { inputStream ->
                    temporary.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                }
                check(temporary.length() == expectedSize) {
                    "Unexpected size for $relativePath: ${temporary.length()} (expected $expectedSize)"
                }
                if (pristine.exists()) pristine.delete()
                check(temporary.renameTo(pristine)) { "Could not cache pristine $relativePath" }
            }

            pristine.copyTo(target, overwrite = true)
            bakeAlmiMedicalMaterial(target)
            check(target.length() > 1_000_000L) { "Patched $relativePath is unexpectedly small" }
        }

        val notice = File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt")
        notice.parentFile.mkdirs()
        notice.writeText(
            "ALMI BODY MAP humanoid-base.glb is generated from MakeHuman HM08 source data.\n" +
                "MakeHuman bundled assets are CC0 1.0 Universal. Runtime asset source: gokulsenthilkumar3/Ultimate.\n" +
                "Pinned source blob: cad5c9ebf0bcf8a6788163951b100184d801a182.\n" +
                "Build step preserves the high-density geometry, rig and morphs, removes unstable source shading maps, applies a smooth icy clinical material, and bakes a tailoring A-pose.\n"
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
