import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.abs

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
val GLB_BIN_CHUNK = 0x004E4942

private fun ByteArrayOutputStream.writeLeInt(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

@Suppress("UNCHECKED_CAST")
private fun addAvatarWhiteBaseLayer(document: MutableMap<String, Any?>, sourceBin: ByteArray): ByteArray {
    val nodes = document["nodes"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val meshes = document["meshes"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val accessors = document["accessors"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val bufferViews = document["bufferViews"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val buffers = document["buffers"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin

    val bodyNodeIndex = nodes.indexOfFirst { it["name"] == "Body" }
    if (bodyNodeIndex < 0) return sourceBin
    val bodyNode = nodes[bodyNodeIndex]
    val bodyMeshIndex = (bodyNode["mesh"] as? Number)?.toInt() ?: return sourceBin
    val bodyMesh = meshes.getOrNull(bodyMeshIndex) ?: return sourceBin
    val primitives = bodyMesh["primitives"] as? MutableList<MutableMap<String, Any?>> ?: return sourceBin
    val sourcePrimitive = primitives.firstOrNull() ?: return sourceBin
    val attributes = sourcePrimitive["attributes"] as? MutableMap<String, Any?> ?: return sourceBin
    val positionAccessorIndex = (attributes["POSITION"] as? Number)?.toInt() ?: return sourceBin
    val indexAccessorIndex = (sourcePrimitive["indices"] as? Number)?.toInt() ?: return sourceBin

    fun accessorBytes(index: Int): Triple<MutableMap<String, Any?>, MutableMap<String, Any?>, Int> {
        val accessor = accessors[index]
        val viewIndex = (accessor["bufferView"] as Number).toInt()
        val view = bufferViews[viewIndex]
        val offset = ((view["byteOffset"] as? Number)?.toInt() ?: 0) +
            ((accessor["byteOffset"] as? Number)?.toInt() ?: 0)
        return Triple(accessor, view, offset)
    }

    val (positionAccessor, positionView, positionOffset) = accessorBytes(positionAccessorIndex)
    if ((positionAccessor["componentType"] as? Number)?.toInt() != 5126 || positionAccessor["type"] != "VEC3") return sourceBin
    val vertexCount = (positionAccessor["count"] as? Number)?.toInt() ?: return sourceBin
    val positionStride = (positionView["byteStride"] as? Number)?.toInt() ?: 12
    val binBuffer = ByteBuffer.wrap(sourceBin).order(ByteOrder.LITTLE_ENDIAN)
    val positions = Array(vertexCount) { vertex ->
        val base = positionOffset + vertex * positionStride
        floatArrayOf(binBuffer.getFloat(base), binBuffer.getFloat(base + 4), binBuffer.getFloat(base + 8))
    }

    val (indexAccessor, _, indexOffset) = accessorBytes(indexAccessorIndex)
    val indexCount = (indexAccessor["count"] as? Number)?.toInt() ?: return sourceBin
    val componentType = (indexAccessor["componentType"] as? Number)?.toInt() ?: return sourceBin
    val componentSize = when (componentType) {
        5121 -> 1
        5123 -> 2
        5125 -> 4
        else -> return sourceBin
    }
    val indices = IntArray(indexCount) { i ->
        val p = indexOffset + i * componentSize
        when (componentType) {
            5121 -> sourceBin[p].toInt() and 0xFF
            5123 -> binBuffer.getShort(p).toInt() and 0xFFFF
            else -> binBuffer.getInt(p)
        }
    }

    val garmentIndices = ArrayList<Int>(indices.size / 4)
    var i = 0
    while (i + 2 < indices.size) {
        val ia = indices[i]
        val ib = indices[i + 1]
        val ic = indices[i + 2]
        if (ia in positions.indices && ib in positions.indices && ic in positions.indices) {
            val a = positions[ia]
            val b = positions[ib]
            val c = positions[ic]
            val avgY = (a[1] + b[1] + c[1]) / 3f
            val avgAbsX = (abs(a[0]) + abs(b[0]) + abs(c[0])) / 3f
            val fittedTop = avgY in 0.02f..0.52f && avgAbsX < 0.205f
            val fittedShorts = avgY in -0.46f..0.08f && avgAbsX < 0.265f
            if (fittedTop || fittedShorts) {
                garmentIndices += ia
                garmentIndices += ib
                garmentIndices += ic
            }
        }
        i += 3
    }
    if (garmentIndices.size < 300) return sourceBin

    val whiteMaterialIndex = materials.size
    materials += linkedMapOf<String, Any?>(
        "name" to "ALMI_BaseWhite",
        "pbrMetallicRoughness" to linkedMapOf<String, Any?>(
            "baseColorFactor" to listOf(0.985, 0.985, 0.99, 1.0),
            "metallicFactor" to 0.0,
            "roughnessFactor" to 0.72,
        ),
        "doubleSided" to false,
        "alphaMode" to "OPAQUE",
    )

    val alignedOffset = (sourceBin.size + 3) and -4
    val indexBytes = ByteArray(garmentIndices.size * 4)
    val indexBuffer = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN)
    garmentIndices.forEach { indexBuffer.putInt(it) }

    val newBin = ByteArray(alignedOffset + indexBytes.size)
    sourceBin.copyInto(newBin)
    indexBytes.copyInto(newBin, destinationOffset = alignedOffset)

    val garmentBufferViewIndex = bufferViews.size
    bufferViews += linkedMapOf<String, Any?>(
        "buffer" to 0,
        "byteOffset" to alignedOffset,
        "byteLength" to indexBytes.size,
        "target" to 34963,
    )
    val garmentAccessorIndex = accessors.size
    accessors += linkedMapOf<String, Any?>(
        "bufferView" to garmentBufferViewIndex,
        "byteOffset" to 0,
        "componentType" to 5125,
        "count" to garmentIndices.size,
        "type" to "SCALAR",
        "min" to listOf(garmentIndices.minOrNull() ?: 0),
        "max" to listOf(garmentIndices.maxOrNull() ?: 0),
    )

    val garmentPrimitive = linkedMapOf<String, Any?>(
        "attributes" to LinkedHashMap(attributes),
        "indices" to garmentAccessorIndex,
        "material" to whiteMaterialIndex,
        "mode" to ((sourcePrimitive["mode"] as? Number)?.toInt() ?: 4),
    )
    sourcePrimitive["targets"]?.let { garmentPrimitive["targets"] = it }

    val garmentMeshIndex = meshes.size
    val garmentMesh = linkedMapOf<String, Any?>(
        "name" to "ALMI_BaseLayerMesh",
        "primitives" to mutableListOf(garmentPrimitive),
    )
    bodyMesh["weights"]?.let { garmentMesh["weights"] = it }
    meshes += garmentMesh

    val garmentNodeIndex = nodes.size
    val garmentNode = linkedMapOf<String, Any?>(
        "name" to "ALMI_BaseLayer",
        "mesh" to garmentMeshIndex,
        "scale" to listOf(1.010, 1.003, 1.010),
    )
    bodyNode["skin"]?.let { garmentNode["skin"] = it }
    bodyNode["weights"]?.let { garmentNode["weights"] = it }
    nodes += garmentNode

    var parentFound = false
    nodes.take(garmentNodeIndex).forEach { node ->
        val children = node["children"] as? MutableList<Any?> ?: return@forEach
        if (children.any { (it as? Number)?.toInt() == bodyNodeIndex }) {
            children += garmentNodeIndex
            parentFound = true
            return@forEach
        }
    }
    if (!parentFound) {
        val scenes = document["scenes"] as? MutableList<MutableMap<String, Any?>>
        val sceneIndex = (document["scene"] as? Number)?.toInt() ?: 0
        val scene = scenes?.getOrNull(sceneIndex)
        val sceneNodes = scene?.get("nodes") as? MutableList<Any?>
        sceneNodes?.add(garmentNodeIndex)
    }

    buffers.firstOrNull()?.set("byteLength", newBin.size)
    return newBin
}

/** Preserve source geometry/rig/morphs while applying ALMI's stable, tintable PBR material. */
@Suppress("UNCHECKED_CAST")
private fun bakeAlmiMaterial(file: File, addAvatarBaseLayer: Boolean) {
    val source = file.readBytes()
    val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
    check(input.remaining() >= 20) { "ALMI body GLB is truncated" }
    check(input.int == GLB_MAGIC) { "ALMI body asset is not a GLB" }
    check(input.int == 2) { "ALMI body GLB must be version 2" }
    input.int

    var jsonChunk: ByteArray? = null
    var binChunk: ByteArray? = null
    val preservedChunks = mutableListOf<Pair<Int, ByteArray>>()
    while (input.remaining() >= 8) {
        val chunkLength = input.int
        val chunkType = input.int
        check(chunkLength >= 0 && chunkLength <= input.remaining()) { "Invalid ALMI GLB chunk" }
        val payload = ByteArray(chunkLength)
        input.get(payload)
        when (chunkType) {
            GLB_JSON_CHUNK -> jsonChunk = payload
            GLB_BIN_CHUNK -> binChunk = payload
            else -> preservedChunks += chunkType to payload
        }
    }

    val jsonBytes = checkNotNull(jsonChunk) { "ALMI GLB is missing its JSON chunk" }
    val rawJson = String(jsonBytes, StandardCharsets.UTF_8).trimEnd(' ', '\u0000', '\n', '\r', '\t')
    val document = JsonSlurper().parseText(rawJson) as MutableMap<String, Any?>

    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>>
        ?: error("ALMI GLB has no materials")
    val skinMaterial = materials.firstOrNull { it["name"] == "Skin" }
        ?: error("ALMI GLB Skin material was not found")

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
        ?: error("ALMI GLB has no nodes")
    nodes.firstOrNull { it["name"] == "LeftUpperArm" }?.set("rotation", listOf(0.0, 0.0, 0.5, 0.8660254))
    nodes.firstOrNull { it["name"] == "RightUpperArm" }?.set("rotation", listOf(0.0, 0.0, -0.5, 0.8660254))

    val finalBin = if (addAvatarBaseLayer) {
        addAvatarWhiteBaseLayer(document, checkNotNull(binChunk) { "Avatar GLB is missing BIN data" })
    } else {
        binChunk
    }

    val encodedJson = JsonOutput.toJson(document).toByteArray(StandardCharsets.UTF_8)
    val paddedJsonSize = (encodedJson.size + 3) and -4
    val paddedJson = ByteArray(paddedJsonSize) { 0x20.toByte() }
    encodedJson.copyInto(paddedJson)

    val chunks = buildList {
        finalBin?.let { add(GLB_BIN_CHUNK to it) }
        addAll(preservedChunks)
    }
    val totalLength = 12 + 8 + paddedJson.size + chunks.sumOf { 8 + it.second.size }
    val output = ByteArrayOutputStream(totalLength)
    output.writeLeInt(GLB_MAGIC)
    output.writeLeInt(2)
    output.writeLeInt(totalLength)
    output.writeLeInt(paddedJson.size)
    output.writeLeInt(GLB_JSON_CHUNK)
    output.write(paddedJson)
    chunks.forEach { (type, payload) ->
        output.writeLeInt(payload.size)
        output.writeLeInt(type)
        output.write(payload)
    }
    val result = output.toByteArray()
    check(result.size == totalLength) { "Could not rebuild ALMI GLB" }
    file.writeBytes(result)
}

data class Almi3dModel(
    val relativePath: String,
    val remoteUrl: String,
    val expectedSize: Long,
    val avatarBaseLayer: Boolean = false,
)

val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v9-3d-assets").get().asFile
val almi3dModels = listOf(
    Almi3dModel(
        relativePath = "almi3d/almi_humanoid.glb",
        remoteUrl = "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base.glb",
        expectedSize = 23_004_332L,
    ),
    Almi3dModel(
        relativePath = "almi3d/almi_avatar_lite.glb",
        remoteUrl = "https://raw.githubusercontent.com/gokulsenthilkumar3/Ultimate/f062df0bf969d034e3d8a9f76d688500fe38e587/growthtrack-ultimate/public/assets/models/humanoid-base-lite.glb",
        expectedSize = 5_278_868L,
        avatarBaseLayer = true,
    ),
)

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    outputs.upToDateWhen { false }
    doLast {
        almi3dModels.forEach { model ->
            val target = File(almi3dGeneratedAssetsDir, model.relativePath)
            val pristine = File(target.parentFile, "${target.name}.source")
            target.parentFile.mkdirs()

            if (!pristine.exists() || pristine.length() != model.expectedSize) {
                val temporary = File(target.parentFile, "${target.name}.download")
                val connection = URI(model.remoteUrl).toURL().openConnection().apply {
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    setRequestProperty("User-Agent", "ALMI-Android-v9-3d-build")
                }
                connection.getInputStream().use { inputStream ->
                    temporary.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
                }
                check(temporary.length() == model.expectedSize) {
                    "Unexpected size for ${model.relativePath}: ${temporary.length()} (expected ${model.expectedSize})"
                }
                if (pristine.exists()) pristine.delete()
                check(temporary.renameTo(pristine)) { "Could not cache pristine ${model.relativePath}" }
            }

            pristine.copyTo(target, overwrite = true)
            bakeAlmiMaterial(target, addAvatarBaseLayer = model.avatarBaseLayer)
            check(target.length() > 1_000_000L) { "Patched ${model.relativePath} is unexpectedly small" }
        }

        val notice = File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt")
        notice.parentFile.mkdirs()
        notice.writeText(
            "ALMI 3D assets are generated from MakeHuman HM08 source data.\n" +
                "MakeHuman bundled assets are CC0 1.0 Universal. Runtime asset source: gokulsenthilkumar3/Ultimate.\n" +
                "High-density body source blob: cad5c9ebf0bcf8a6788163951b100184d801a182.\n" +
                "Lite avatar source blob: f285526c0fdc9b20ca1b6f6b78e73876c34c5255.\n" +
                "The avatar build derives a white fitted base-layer top/short geometry from the same CC0 body mesh so it follows the same rig without adding another runtime model download.\n"
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
        versionName = "0.5.$ciRunNumber"
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
