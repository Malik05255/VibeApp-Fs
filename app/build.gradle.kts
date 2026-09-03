plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

val githubOAuthClientId = providers.gradleProperty("GITHUB_OAUTH_CLIENT_ID")
    .orElse(providers.environmentVariable("GITHUB_OAUTH_CLIENT_ID"))
    .orElse("")
val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID"))
    .orElse("")
val releaseStoreFile = providers.gradleProperty("LM_AI_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("LM_AI_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("LM_AI_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("LM_AI_KEY_PASSWORD").orNull

android {
    namespace = "com.vibe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.malik.lmai"
        minSdk = 29
        targetSdk = 36
        versionCode = 20000
        versionName = "2.0.0"

        buildConfigField(
            "String",
            "GITHUB_OAUTH_CLIENT_ID",
            "\"${githubOAuthClientId.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${googleWebClientId.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        // دعم اللغة الإنجليزية والعربية فقط وتجاهل باقي اللغات
        resConfigs("en", "ar")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi")
        }
    }

    //noinspection WrongGradleMethod
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    signingConfigs {
        if (!releaseStoreFile.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            // Must stay true: libaapt2.so is executed as a binary via ProcessBuilder,
            // not loaded as a shared library. With false, Android 10+ does not extract
            // .so to nativeLibraryDir and the fallback files/ dir has noexec mount.
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/eclipse.inf"
            excludes += "kotlin/kotlin.kotlin_builtins"
            excludes += "kotlin/ranges/ranges.kotlin_builtins"
            excludes += "kotlin/reflect/reflect.kotlin_builtins"
            excludes += "kotlin/collections/collections.kotlin_builtins"
            excludes += "kotlin/coroutines/coroutines.kotlin_builtins"
            excludes += "kotlin/annotation/annotation.kotlin_builtins"
            excludes += "kotlin/internal/internal.kotlin_builtins"
            excludes += "plugin.xml"
            excludes += "plugin.properties"
            // Javac compiler localized messages (not visible to users)
            excludes += "com/sun/tools/javac/resources/compiler_ja.properties"
            excludes += "com/sun/tools/javac/resources/compiler_zh_CN.properties"
            excludes += "com/sun/tools/javac/resources/javac_ja.properties"
            excludes += "com/sun/tools/javac/resources/javac_zh_CN.properties"
            excludes += "com/sun/tools/javac/resources/launcher_ja.properties"
            excludes += "com/sun/tools/javac/resources/launcher_zh_CN.properties"
            // Javap / doclint localized messages (unused)
            excludes += "com/sun/tools/javap/resources/javap_ja.properties"
            excludes += "com/sun/tools/javap/resources/javap_zh_CN.properties"
            excludes += "com/sun/tools/doclint/resources/doclint_ja.properties"
            excludes += "com/sun/tools/doclint/resources/doclint_zh_CN.properties"
            // JAXP/Xerces localized messages
            excludes += "org/openjdk/com/sun/org/apache/xerces/internal/impl/msg/*_*.properties"
            excludes += "org/openjdk/com/sun/org/apache/xml/internal/serializer/output_*.properties"
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
        disable += "MissingTranslation"
        abortOnError = false
    }
}

configurations.all {
    exclude(group = "com.intellij", module = "annotations")
    // bundletool JAR contains both DEX and Java bytecode, which breaks R8
    exclude(group = "com.android.tools.build", module = "bundletool")
}

dependencies {
    // Android
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.credentials:credentials:1.7.0-alpha03")
    implementation("androidx.credentials:credentials-play-services-auth:1.7.0-alpha03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.viewmodel)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.coil.compose)

    // SplashScreen
    implementation(libs.splashscreen)

    // DataStore
    implementation(libs.androidx.datastore)

    // Dependency Injection
    implementation(libs.hilt)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    ksp(libs.hilt.compiler)

    // Ktor
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.logging)
    implementation(libs.ktor.serialization)

    // Markdown
    implementation(libs.compose.markdown)
    implementation(libs.compose.markdown.code)

    // Navigation
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.navigation)

    // Room
    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // HTML parsing (web search)
    implementation(libs.jsoup)

    // Hidden API bypass — lets plugin inspector reflect WindowManagerGlobal.getRootViews()
    // so that dialogs / popup menus / bottom sheets are visible to the agent on API 30+.
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")

    implementation(project(":build-engine"))
    implementation(project(":shadow-runtime"))

    // Test
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.chucker.debug)
    releaseImplementation(libs.chucker.release)
}
