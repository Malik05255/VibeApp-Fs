# H AI authentication and updates

> This file keeps its historical filename for compatibility with existing links.

H AI's stable Android application ID is `com.malik05255.lmai`. The public product name is H AI; the package ID remains unchanged so existing H AI installations can update in place.

## Google

The interactive Android sign-in flow uses Google Sign-In with the Web OAuth client ID supplied through `GOOGLE_WEB_CLIENT_ID`.

Repository configuration used by Android CI and signed release builds:

- Secret: `GOOGLE_WEB_CLIENT_ID`
- Variable: `GOOGLE_ANDROID_PACKAGE_NAME` (expected: `com.malik05255.lmai`)
- Variable: `GOOGLE_ANDROID_SHA1`
- Secret: `GOOGLE_MAPS_API_KEY` when Maps rendering is required

No Google OAuth client secret belongs in the APK.

## GitHub

H AI uses GitHub OAuth Device Flow. Configure the OAuth App client ID as repository variable `OAUTH_CLIENT_ID`.

The Android app never requests a GitHub password or packages an OAuth client secret. Tokens stored by the app are protected with Android Keystore-backed encryption.

## Updateable release APKs

All future releases must keep the same application ID and signing certificate.

The signed release workflow keeps the existing signing-secret names for compatibility:

- `LM_AI_KEYSTORE_BASE64`
- `LM_AI_STORE_PASSWORD`
- `LM_AI_KEY_ALIAS`
- `LM_AI_KEY_PASSWORD`

Never commit a private keystore, signing password, OAuth secret, or API key.
