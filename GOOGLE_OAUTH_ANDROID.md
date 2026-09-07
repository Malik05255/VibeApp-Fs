# Google OAuth Android identity for lm_AI

Google Sign-In for the independent lm_AI Android app must be registered in the same Google Cloud project as the `GOOGLE_WEB_CLIENT_ID` secret.

Current Android OAuth client identity:

- Package name: `com.malik05255.lmai`
- Signing SHA-1: `3C:A7:CC:77:71:6E:FD:A3:2D:B9:77:E4:CF:23:4A:7E:F3:C5:66:A2`
- Signing SHA-256: `39:1A:3B:15:3D:6E:70:64:9B:F6:C3:0E:7B:99:B9:69:39:0A:FE:B8:A6:93:19:9D:7D:7E:59:80:D2:92:D4:C9`

The CI workflow derives the package name and SHA-1 from the APK signing keystore on every build and uploads them as the `lm_AI-google-oauth-identity` artifact. If repository variable `GOOGLE_ANDROID_SHA1` is set, CI also fails immediately when the actual signing certificate no longer matches it.

A Google Sign-In status code 10 (`DEVELOPER_ERROR`) after the lm_AI package rename indicates that the Android OAuth client in Google Cloud does not match this package name and signing SHA-1. The Web OAuth Client ID alone is not sufficient for Android Google Sign-In.

Do not change `applicationId` back to the legacy VibeApp package. `com.malik05255.lmai` is intentionally stable so lm_AI remains installable alongside the original app and can update in place.
