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

val openRouterOAuthCallbackUrl = providers.gradleProperty("OPENROUTER_OAUTH_CALLBACK_URL")
    .orElse(providers.environmentVariable("OPENROUTER_OAUTH_CALLBACK_URL"))
    .orElse("lmai://openrouter-oauth")

val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID"))
    .orElse("")

val googleAndroidSha1 = providers.gradleProperty("GOOGLE_ANDROID_SHA1")
    .orElse(providers.environmentVariable("GOOGLE_ANDROID_SHA1"))
    .orElse("")

val googleMapsApiKey = providers.gradleProperty("GOOGLE_MAPS_API_KEY")
    .orElse(providers.environmentVariable("GOOGLE_MAPS_API_KEY"))
    .orElse("")

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.malik.lmai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.malik05255.lmai"
        minSdk = 29
        targetSdk = 36
        versionCode = 20101
        versionName = "2.1.1"

        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"${githubOAuthClientId.get()}\"")
        buildConfigField("String", "OPENROUTER_OAUTH_CALLBACK_URL", "\"${openRouterOAuthCallbackUrl.get()}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.get()}\"")
        buildConfigField("String", "GOOGLE_ANDROID_SHA1", "\"${googleAndroidSha1.get()}\"")
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${googleMapsApiKey.get()}\"")
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = googleMapsApiKey.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    lint {
        abortOnError = true
        textReport = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

configurations.configureEach {
    exclude(group = "com.intellij", module = "annotations")
}

dependencies {
    implementation(project(":build-engine"))
    implementation(project(":shadow-runtime"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.splashscreen.pinned)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)

    implementation(libs.androidx.navigation)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose.android)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.ktor.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)
    implementation(libs.ktor.logging)

    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.jsoup)

    implementation(libs.google.play.services.auth)
    implementation(libs.google.play.services.location)
    // 6.12.0 stays compatible with the app's compileSdk 36 / AGP 9.1 baseline.
    // Newer 8.x releases currently require Android API 37.
    implementation(libs.google.maps.compose)
    implementation(libs.google.api.client.android)
    implementation(libs.google.drive.api)

    // Independent on-device runtime. Model weights are downloaded separately and
    // never inflate the APK. MediaPipe's generated lite protos need protobuf 4.26.1,
    // while the embedded Android build engine also brings the full protobuf runtime.
    // Use one modern full runtime (which includes GeneratedMessageLite) to avoid
    // duplicate com.google.protobuf classes without removing APIs required by the
    // on-device build engine.
    implementation(libs.mediapipe.tasks.genai) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.protobuf.java)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hiddenapibypass)
    debugImplementation(libs.chucker.debug)
    releaseImplementation(libs.chucker.release)

    implementation(libs.compose.markdown)
    implementation(libs.compose.markdown.code)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit.pinned)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}