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
 * Rewrites only the body material metadata while preserving the source GLB geometry, embedded
 * normal/AO textures, skinning, rig and morph data byte-for-byte. Earlier visibility debugging
 * stripped the rig and microdetail; the SurfaceView composition bug is now fixed, so the authored
 * MakeHuman data is kept and only its appearance is translated into ALMI's clinical body-map look.
 */
@Suppress("UNCHECKED_CAST")
private fun bakeAlmiMedicalMaterial(file: File) {
    val source = file.readBytes()
    val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
    check(input.remaining() >= 20) { "ALMI body GLB is truncated" }
    check(input.int == GLB_MAGIC) { "ALMI body asset is not a GLB" }
    check(input.int == 2) { "ALMI body GLB must be version 2" }
    input.int // original total length

    var jsonChunk: ByteArray? = null
    val preservedChunks = mutableListOf<Pair<Int, ByteArray>>()
    while (input.remaining() >= 8) {
        val chunkLength = input.int
        val chunkType = input.int
        check(chunkLength >= 0 && chunkLength <= input.remaining()) { "Invalid ALMI GLB chunk" }
        val payload = ByteArray(chunkLength)
        input.get(payload)
        if (chunkType == GLB_JSON_CHUNK) {
            jsonChunk = payload
        } else {
            preservedChunks += chunkType to payload
        }
    }

    val jsonBytes = checkNotNull(jsonChunk) { "ALMI GLB is missing its JSON chunk" }
    val rawJson = String(jsonBytes, StandardCharsets.UTF_8)
        .trimEnd(' ', '\u0000', '\n', '\r', '\t')
    val document = JsonSlurper().parseText(rawJson) as MutableMap<String, Any?>

    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>>
        ?: error("ALMI GLB has no materials")
    val skinMaterial = materials.firstOrNull { it["name"] == "Skin" }
        ?: error("ALMI GLB Skin material was not found")
    val sourcePbr = skinMaterial["pbrMetallicRoughness"] as? Map<String, Any?>

    val medicalPbr = linkedMapOf<String, Any>(
        "baseColorFactor" to listOf(0.30, 0.48, 0.70, 0.82),
        "metallicFactor" to 0.02,
        "roughnessFactor" to 0.38,
    )
    sourcePbr?.get("metallicRoughnessTexture")?.let {
        medicalPbr["metallicRoughnessTexture"] = it
    }
    skinMaterial["pbrMetallicRoughness"] = medicalPbr
    skinMaterial["emissiveFactor"] = listOf(0.010, 0.028, 0.070)
    skinMaterial["doubleSided"] = true
    skinMaterial["alphaMode"] = "BLEND"
    skinMaterial.remove("alphaCutoff")

    // Intentionally retain normalTexture, occlusionTexture, JOINTS_0 / WEIGHTS_0 and skins.
    // Those carry the authored surface detail and rig data needed for a human rather than a flat
    // diagnostic silhouette.

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

// BODY MAP is the only Filament surface in ALMI. Keep the full pinned MakeHuman HM08 geometry and
// its rig/morph data; only the body material is adapted for the dark clinical measurement scene.
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
                "Build step preserves geometry, rig, morphs and embedded normal/AO detail and applies ALMI's clinical material metadata.\n"
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

    // Filament remains the dedicated native 3D renderer for the measurement body.
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
