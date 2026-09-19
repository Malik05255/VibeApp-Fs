# H AI Release Process

This document describes the two release paths currently present in the repository.

## 1. Automatic H AI update from `main`

The primary Android workflow is `.github/workflows/android.yml`.

On a successful push to `main`, it:

1. validates OAuth/signing configuration;
2. builds the debug APK with the persistent configured debug signing key;
3. runs unit tests and Android Lint;
4. creates a GitHub Release tagged as `v<versionName>-build.<runNumber>`;
5. uploads the H AI APK plus `update-manifest.json`.

The update manifest contains the version metadata, download URL, and SHA-256 used by the in-app updater.

Required configuration includes:

- repository variable `OAUTH_CLIENT_ID`;
- secret `GOOGLE_WEB_CLIENT_ID`;
- optional/expected variable `GOOGLE_ANDROID_PACKAGE_NAME` = `com.malik05255.lmai`;
- variable `GOOGLE_ANDROID_SHA1` when enforcing the Android OAuth identity;
- secret `ANDROID_DEBUG_KEYSTORE_BASE64` for stable debug-signing identity;
- secret `GOOGLE_MAPS_API_KEY` when Maps is required.

If `ANDROID_DEBUG_KEYSTORE_BASE64` is missing, CI generates a one-time bootstrap keystore artifact and deliberately fails instead of publishing an APK with a transient signing identity.

## 2. Signed release workflow

The signed workflow is `.github/workflows/release.yml` and is triggered by:

- manual `workflow_dispatch`; or
- pushing a tag matching `v*`.

It runs `app:assembleRelease`, restores the configured release keystore, signs through the Android Gradle configuration, and uploads the signed APK as a workflow artifact.

Signing secrets:

- `LM_AI_KEYSTORE_BASE64`
- `LM_AI_STORE_PASSWORD`
- `LM_AI_KEY_ALIAS`
- `LM_AI_KEY_PASSWORD`

These secret names are retained for compatibility even though the public product name is H AI.

## Version changes

Update these values in `app/build.gradle.kts`:

```kotlin
versionCode = <increment>
versionName = "<new-version>"
```

`versionCode` must increase for every installable update.

## Recommended release validation

Before publishing or tagging a release, verify:

```bash
./gradlew --no-daemon app:testDebugUnitTest
./gradlew --no-daemon app:lintDebug
./gradlew --no-daemon app:assembleDebug
```

For the signed path, also verify the release build with the configured signing environment.

## Identity contract

Do not change these only for branding cleanup:

- application ID: `com.malik05255.lmai`
- Kotlin namespace: `com.malik.lmai`
- deep-link/OAuth compatibility scheme: `lmai://`

They are compatibility identifiers. The user-facing brand is H AI.
