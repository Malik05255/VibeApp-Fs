android {
    namespace = "com.malik.lmai"
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

        // دعم اللغة الإنجليزية والعربية فقط
        resConfigs("en", "ar")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi")
        }
    }
}
