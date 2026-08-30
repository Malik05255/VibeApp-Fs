import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.imageio.ImageIO

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

private val GLB_MAGIC = 0x46546C67
private val GLB_JSON_CHUNK = 0x4E4F534A
private val GLB_BIN_CHUNK = 0x004E4942

private fun ByteArrayOutputStream.writeLeInt(value: Int) {
    write(value and 0xFF)
    write((value ushr 8) and 0xFF)
    write((value ushr 16) and 0xFF)
    write((value ushr 24) and 0xFF)
}

private data class ParsedGlb(
    val document: MutableMap<String, Any?>,
    val binary: ByteArray,
    val otherChunks: List<Pair<Int, ByteArray>>,
)

@Suppress("UNCHECKED_CAST")
private fun readGlb(file: File): ParsedGlb {
    val source = file.readBytes()
    val input = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN)
    check(input.remaining() >= 20) { "${file.name} is truncated" }
    check(input.int == GLB_MAGIC) { "${file.name} is not a GLB" }
    check(input.int == 2) { "${file.name} must be glTF 2" }
    input.int

    var jsonChunk: ByteArray? = null
    var binChunk: ByteArray? = null
    val otherChunks = mutableListOf<Pair<Int, ByteArray>>()
    while (input.remaining() >= 8) {
        val length = input.int
        val type = input.int
        check(length >= 0 && length <= input.remaining()) { "Invalid chunk in ${file.name}" }
        val payload = ByteArray(length)
        input.get(payload)
        when (type) {
            GLB_JSON_CHUNK -> jsonChunk = payload
            GLB_BIN_CHUNK -> binChunk = payload
            else -> otherChunks += type to payload
        }
    }
    val rawJson = String(checkNotNull(jsonChunk), StandardCharsets.UTF_8)
        .trimEnd(' ', '\u0000', '\n', '\r', '\t')
    return ParsedGlb(
        document = JsonSlurper().parseText(rawJson) as MutableMap<String, Any?>,
        binary = checkNotNull(binChunk),
        otherChunks = otherChunks,
    )
}

private fun writeGlb(
    file: File,
    document: MutableMap<String, Any?>,
    binary: ByteArray,
    otherChunks: List<Pair<Int, ByteArray>>,
) {
    val encodedJson = JsonOutput.toJson(document).toByteArray(StandardCharsets.UTF_8)
    val paddedJsonSize = (encodedJson.size + 3) and -4
    val paddedJson = ByteArray(paddedJsonSize) { 0x20.toByte() }
    encodedJson.copyInto(paddedJson)

    val paddedBinarySize = (binary.size + 3) and -4
    val paddedBinary = if (paddedBinarySize == binary.size) {
        binary
    } else {
        ByteArray(paddedBinarySize).also { binary.copyInto(it) }
    }
    val chunks = buildList {
        add(GLB_JSON_CHUNK to paddedJson)
        add(GLB_BIN_CHUNK to paddedBinary)
        addAll(otherChunks)
    }
    val totalLength = 12 + chunks.sumOf { 8 + it.second.size }
    val output = ByteArrayOutputStream(totalLength)
    output.writeLeInt(GLB_MAGIC)
    output.writeLeInt(2)
    output.writeLeInt(totalLength)
    chunks.forEach { (type, payload) ->
        output.writeLeInt(payload.size)
        output.writeLeInt(type)
        output.write(payload)
    }
    file.writeBytes(output.toByteArray())
}

@Suppress("UNCHECKED_CAST")
private fun mutableObjectList(
    document: MutableMap<String, Any?>,
    key: String,
): MutableList<MutableMap<String, Any?>> {
    val existing = document[key]
    if (existing != null) return existing as MutableList<MutableMap<String, Any?>>
    return mutableListOf<MutableMap<String, Any?>>().also { document[key] = it }
}

private fun mergeHairDiffuseAndOpacity(diffuseFile: File, opacityFile: File): ByteArray {
    val diffuse = checkNotNull(ImageIO.read(diffuseFile)) { "Could not decode ${diffuseFile.name}" }
    val opacity = checkNotNull(ImageIO.read(opacityFile)) { "Could not decode ${opacityFile.name}" }
    check(diffuse.width == opacity.width && diffuse.height == opacity.height) {
        "Hair diffuse/opacity dimensions do not match"
    }
    val result = BufferedImage(diffuse.width, diffuse.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until diffuse.height) {
        for (x in 0 until diffuse.width) {
            val rgb = diffuse.getRGB(x, y)
            val mask = opacity.getRGB(x, y)
            val alpha = (mask ushr 16) and 0xFF
            result.setRGB(x, y, (alpha shl 24) or (rgb and 0x00FFFFFF))
        }
    }
    diffuse.flush()
    opacity.flush()
    val output = ByteArrayOutputStream()
    check(ImageIO.write(result, "png", output)) { "Could not encode v12 hair texture" }
    result.flush()
    return output.toByteArray()
}

@Suppress("UNCHECKED_CAST")
private fun patchVitruvianTextures(file: File, profile: String, sourceDir: File) {
    val parsed = readGlb(file)
    val document = parsed.document
    val materials = document["materials"] as? MutableList<MutableMap<String, Any?>>
        ?: error("${file.name} has no materials")
    val buffers = document["buffers"] as? MutableList<MutableMap<String, Any?>>
        ?: error("${file.name} has no buffers")
    val bufferViews = mutableObjectList(document, "bufferViews")
    val images = mutableObjectList(document, "images")
    val textures = mutableObjectList(document, "textures")
    val binary = ByteArrayOutputStream(parsed.binary.size + 64 * 1024 * 1024)
    binary.write(parsed.binary)

    fun source(name: String): File = File(sourceDir, name).also {
        check(it.isFile && it.length() > 50_000L) { "Missing v12 source texture $name" }
    }

    fun embedPng(name: String, bytes: ByteArray): Int {
        while (binary.size() % 4 != 0) binary.write(0)
        val offset = binary.size()
        binary.write(bytes)
        val viewIndex = bufferViews.size
        bufferViews += linkedMapOf<String, Any?>(
            "buffer" to 0,
            "byteOffset" to offset,
            "byteLength" to bytes.size,
        )
        val imageIndex = images.size
        images += linkedMapOf<String, Any?>(
            "name" to name,
            "bufferView" to viewIndex,
            "mimeType" to "image/png",
        )
        val textureIndex = textures.size
        textures += linkedMapOf<String, Any?>("source" to imageIndex)
        return textureIndex
    }

    val textureCache = mutableMapOf<String, Int>()
    fun embedFile(name: String): Int = textureCache.getOrPut(name) {
        embedPng(name.substringBeforeLast('.'), source(name).readBytes())
    }

    fun material(name: String): MutableMap<String, Any?> =
        materials.firstOrNull { it["name"] == name }
            ?: error("${file.name} is missing material $name")

    fun pbr(material: MutableMap<String, Any?>): MutableMap<String, Any?> {
        val existing = material["pbrMetallicRoughness"]
        if (existing != null) return existing as MutableMap<String, Any?>
        return linkedMapOf<String, Any?>().also { material["pbrMetallicRoughness"] = it }
    }

    fun applyPbr(
        materialName: String,
        baseColor: Int? = null,
        normal: Int? = null,
        roughness: Int? = null,
        occlusion: Int? = null,
        baseFactor: List<Double> = listOf(1.0, 1.0, 1.0, 1.0),
        roughnessFactor: Double = 1.0,
    ) {
        val target = material(materialName)
        val targetPbr = pbr(target)
        targetPbr["baseColorFactor"] = baseFactor
        targetPbr["metallicFactor"] = 0.0
        targetPbr["roughnessFactor"] = roughnessFactor
        baseColor?.let {
            targetPbr["baseColorTexture"] = linkedMapOf<String, Any?>("index" to it)
        }
        normal?.let {
            target["normalTexture"] = linkedMapOf<String, Any?>("index" to it, "scale" to 1.0)
        }
        roughness?.let {
            targetPbr["metallicRoughnessTexture"] = linkedMapOf<String, Any?>("index" to it)
        }
        occlusion?.let {
            target["occlusionTexture"] = linkedMapOf<String, Any?>("index" to it, "strength" to .72)
        }
    }

    when (profile) {
        "body" -> {
            val bodyColor = embedFile("vit_body_bc.png")
            val bodyNormal = embedFile("vit_body_n.png")
            val bodyRough = embedFile("vit_body_rough.png")
            val fabricNormal = embedFile("vit_fabric_n.png")
            applyPbr("VitBody", bodyColor, bodyNormal, bodyRough)
            applyPbr(
                "VitShirt",
                normal = fabricNormal,
                baseFactor = listOf(.985, .985, .99, 1.0),
                roughnessFactor = .74,
            )
            applyPbr(
                "VitPants",
                normal = fabricNormal,
                baseFactor = listOf(.985, .985, .99, 1.0),
                roughnessFactor = .74,
            )
            applyPbr(
                "VitShoes",
                baseFactor = listOf(.97, .975, .985, 1.0),
                roughnessFactor = .48,
            )

            val nodes = document["nodes"] as? List<Map<String, Any?>> ?: emptyList()
            check(nodes.any { it["name"] == "mixamorig:Head" }) {
                "Vitruvian body head bone changed"
            }
            val animations = document["animations"] as? List<Map<String, Any?>> ?: emptyList()
            val animationNames = animations.mapNotNull { it["name"] as? String }
            check(animationNames.any { it.equals("Idle", true) }) {
                "Vitruvian body lost Idle animation"
            }
            check(animationNames.any { it.equals("Walk", true) }) {
                "Vitruvian body lost Walk animation"
            }
        }

        "head" -> {
            val faceColor = embedFile("vit_face_bc.png")
            val faceNormal = embedFile("vit_face_n.png")
            val faceRough = embedFile("vit_face_rough.png")
            val iris = embedFile("vit_iris.png")
            val sclera = embedFile("vit_sclera.png")
            val mouth = embedFile("vit_mouth.png")

            applyPbr("VitSkin", faceColor, faceNormal, faceRough)
            applyPbr("VitMouth", baseColor = mouth, roughnessFactor = .46)
            listOf("VitIris", "VitIris.001").forEach {
                applyPbr(it, baseColor = iris, roughnessFactor = .28)
            }
            listOf("VitSclera", "VitSclera.001").forEach {
                applyPbr(it, baseColor = sclera, roughnessFactor = .30)
            }
            listOf("VitCornea2", "VitCornea2.001").forEach { name ->
                val cornea = material(name)
                val corneaPbr = pbr(cornea)
                corneaPbr["baseColorFactor"] = listOf(1.0, 1.0, 1.0, .08)
                corneaPbr["metallicFactor"] = 0.0
                corneaPbr["roughnessFactor"] = .035
                cornea["alphaMode"] = "BLEND"
                cornea["doubleSided"] = false
            }

            val meshes = document["meshes"] as? List<Map<String, Any?>> ?: emptyList()
            val morphNames = meshes.flatMap { mesh ->
                (mesh["extras"] as? Map<*, *>)?.get("targetNames") as? List<String>
                    ?: emptyList()
            }
            check(morphNames.any { it.equals("Happy", true) }) {
                "Vitruvian head lost FACS Happy"
            }
            check(morphNames.any { it.equals("Eyes_Closed_Max", true) }) {
                "Vitruvian head lost blink FACS"
            }
        }

        "hair" -> {
            val mergedHair = mergeHairDiffuseAndOpacity(
                source("vit_hair_diffuse.png"),
                source("vit_hair_opacity.png"),
            )
            val hairColor = embedPng("vit_hair_rgba", mergedHair)
            val hairNormal = embedFile("vit_hair_normal.png")
            val hairAo = embedFile("vit_hair_ao.png")
            applyPbr(
                "VitHair",
                baseColor = hairColor,
                normal = hairNormal,
                occlusion = hairAo,
                roughnessFactor = .62,
            )
            val hairMaterial = material("VitHair")
            hairMaterial["alphaMode"] = "MASK"
            hairMaterial["alphaCutoff"] = .36
            hairMaterial["doubleSided"] = true
        }

        else -> error("Unknown Vitruvian patch profile: $profile")
    }

    val finalBin = binary.toByteArray()
    buffers.first()["byteLength"] = finalBin.size
    writeGlb(file, document, finalBin, parsed.otherChunks)

    val verification = readGlb(file).document
    val verifiedMaterials = verification["materials"] as? List<Map<String, Any?>> ?: emptyList()
    when (profile) {
        "body" -> check(
            verifiedMaterials.first { it["name"] == "VitBody" }
                .let { it["pbrMetallicRoughness"] as Map<*, *> }
                .containsKey("baseColorTexture")
        ) { "Body 4K base-color texture was not embedded" }

        "head" -> check(
            verifiedMaterials.first { it["name"] == "VitSkin" }
                .let { it["pbrMetallicRoughness"] as Map<*, *> }
                .containsKey("baseColorTexture")
        ) { "Face 4K base-color texture was not embedded" }

        "hair" -> check(verifiedMaterials.first { it["name"] == "VitHair" }["alphaMode"] == "MASK") {
            "Hair opacity mask was not embedded"
        }
    }
}

data class Almi3dAsset(
    val relativePath: String,
    val remoteUrl: String,
    val expectedSize: Long,
    val vitruvianProfile: String? = null,
)

data class AlmiTextureSource(
    val fileName: String,
    val expectedSize: Long,
)

val almi3dGeneratedAssetsDir = layout.buildDirectory.dir("generated/almi-v12-3d-assets").get().asFile
val almi3dTextureSourceDir = layout.buildDirectory.dir("generated/almi-v12-texture-sources").get().asFile
val vitruvianSourceBase =
    "https://raw.githubusercontent.com/ibrews/VitruvianGodot/bdecdcd537b4031fdd0fb299b7e4f93f084fffa0/godot_project"

val almiTextureSources = listOf(
    AlmiTextureSource("vit_body_bc.png", 6_961_109L),
    AlmiTextureSource("vit_body_n.png", 4_877_720L),
    AlmiTextureSource("vit_body_rough.png", 9_418_824L),
    AlmiTextureSource("vit_fabric_n.png", 694_037L),
    AlmiTextureSource("vit_face_bc.png", 7_835_475L),
    AlmiTextureSource("vit_face_n.png", 5_950_785L),
    AlmiTextureSource("vit_face_rough.png", 10_064_534L),
    AlmiTextureSource("vit_hair_diffuse.png", 1_691_178L),
    AlmiTextureSource("vit_hair_normal.png", 6_131_884L),
    AlmiTextureSource("vit_hair_opacity.png", 3_962_134L),
    AlmiTextureSource("vit_hair_ao.png", 2_609_942L),
    AlmiTextureSource("vit_iris.png", 581_172L),
    AlmiTextureSource("vit_mouth.png", 3_018_410L),
    AlmiTextureSource("vit_sclera.png", 965_536L),
)

val almi3dAssets = listOf(
    // VSim pair is still used only by the cinematic dual-gender chooser.
    Almi3dAsset(
        relativePath = "almi3d/almi_body_female_v12.glb",
        remoteUrl =
            "https://raw.githubusercontent.com/kunalkushwaha/vsim/3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91/packages/assets/library/human.glb",
        expectedSize = 2_767_576L,
    ),
    Almi3dAsset(
        relativePath = "almi3d/almi_body_male_v12.glb",
        remoteUrl =
            "https://raw.githubusercontent.com/kunalkushwaha/vsim/3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91/packages/assets/library/man.glb",
        expectedSize = 2_889_028L,
    ),
    // HM08 lite is intentionally no longer packaged. No live V12 route references it.
    Almi3dAsset(
        relativePath = "almi3d/digital/vitruvian_body.glb",
        remoteUrl = "$vitruvianSourceBase/vitruvian_body.glb",
        expectedSize = 6_879_364L,
        vitruvianProfile = "body",
    ),
    Almi3dAsset(
        relativePath = "almi3d/digital/vitruvian_head.glb",
        remoteUrl = "$vitruvianSourceBase/vitruvian_head.glb",
        expectedSize = 10_189_832L,
        vitruvianProfile = "head",
    ),
    // Runtime rigidly follows the head root, so the heavier skinned/rigged hair adds no visual value.
    Almi3dAsset(
        relativePath = "almi3d/digital/vitruvian_hair.glb",
        remoteUrl = "$vitruvianSourceBase/vitruvian_hair.glb",
        expectedSize = 21_189_248L,
        vitruvianProfile = "hair",
    ),
)

private fun downloadPinnedAsset(url: String, target: File, expectedSize: Long) {
    target.parentFile.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.download")
    if (temporary.exists()) temporary.delete()
    val connection = URI(url).toURL().openConnection().apply {
        connectTimeout = 30_000
        readTimeout = 300_000
        setRequestProperty("User-Agent", "ALMI-Android-v12-quality-assets")
    }
    connection.getInputStream().use { inputStream ->
        temporary.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
    }
    check(temporary.length() == expectedSize) {
        "Unexpected size for ${target.name}: ${temporary.length()} (expected $expectedSize)"
    }
    check(temporary.renameTo(target)) { "Could not install ${target.name}" }
}

val prepareAlmi3dAssets by tasks.registering {
    outputs.dir(almi3dGeneratedAssetsDir)
    outputs.upToDateWhen { false }
    doLast {
        if (almi3dGeneratedAssetsDir.exists()) almi3dGeneratedAssetsDir.deleteRecursively()
        if (almi3dTextureSourceDir.exists()) almi3dTextureSourceDir.deleteRecursively()
        almi3dGeneratedAssetsDir.mkdirs()
        almi3dTextureSourceDir.mkdirs()

        almiTextureSources.forEach { source ->
            downloadPinnedAsset(
                url = "$vitruvianSourceBase/${source.fileName}",
                target = File(almi3dTextureSourceDir, source.fileName),
                expectedSize = source.expectedSize,
            )
        }

        almi3dAssets.forEach { asset ->
            val target = File(almi3dGeneratedAssetsDir, asset.relativePath)
            downloadPinnedAsset(asset.remoteUrl, target, asset.expectedSize)
            asset.vitruvianProfile?.let { profile ->
                patchVitruvianTextures(target, profile, almi3dTextureSourceDir)
                check(target.length() > asset.expectedSize) {
                    "Textured ${asset.relativePath} did not grow"
                }
            }
            check(target.length() > 1_000_000L) {
                "${asset.relativePath} is unexpectedly small"
            }
        }

        File(almi3dGeneratedAssetsDir, "almi3d/ASSET_NOTICE.txt").apply {
            parentFile.mkdirs()
            writeText(
                "ALMI v12 3D ASSETS\n\n" +
                    "Identity chooser female/male: vsim packages/assets/library human.glb/man.glb, pinned at 3f97faf85e46d2f9a122b0a8b8d3ccc0af598f91.\n" +
                    "These MPFB2/MakeHuman generated chooser bodies preserve their authored PBR materials and animations.\n\n" +
                    "The visible Avatar Lab and Body Map use VitruvianGodot body + FACS head pinned at bdecdcd537b4031fdd0fb299b7e4f93f084fffa0.\n" +
                    "ALMI embeds Vitruvian's high-resolution body/face BaseColor, Normal, Roughness, eye, mouth, and hair maps into generated GLBs at build time.\n" +
                    "Hair uses Vitruvian's non-rigged visual mesh because ALMI attaches the whole authored hair root to the animated head; the discarded hair skeleton was not used by the runtime.\n" +
                    "Hair diffuse and opacity are combined into an RGBA alpha-masked texture rendered by Filament.\n\n" +
                    "The legacy HM08 lite avatar is not packaged in V12.\n"
            )
        }
    }
}

tasks.matching {
    (it.name.startsWith("merge") && it.name.endsWith("Assets")) ||
        it.name.contains("Lint", ignoreCase = true)
}.configureEach { dependsOn(prepareAlmi3dAssets) }

android {
    namespace = "com.almi.ai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.almi.ai"
        minSdk = 29
        targetSdk = 36
        versionCode = 30_000 + ciRunNumber
        versionName = "0.11.$ciRunNumber"
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
