plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.hilt)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
}

val githubOAuthClientId = providers.gradleProperty("GITHUB_OAUTH_CLIENT_ID").orElse(providers.environmentVariable("GITHUB_OAUTH_CLIENT_ID")).orElse("")
val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID")).orElse("")

android {
    namespace = "com.malik.lmai"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.malik.lmai"
        minSdk = 29
        targetSdk = 36
        versionCode = 20001
        versionName = "2.0.1"

        buildConfigField("String", "GITHUB_OAUTH_CLIENT_ID", "\"${githubOAuthClientId.get()}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.get()}\"")
        resConfigs("en", "ar")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.all {
    exclude(group = "com.intellij", module = "annotations")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.navigation)
    implementation(libs.room)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.serialization.json)
}
