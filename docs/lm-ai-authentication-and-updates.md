# lm_AI authentication and updates

The app ID is `com.malik.lmai`, allowing installation beside VibeApp.

## Google
Create a Web OAuth client in Google Auth Platform and add repository variable `GOOGLE_WEB_CLIENT_ID`. No client secret belongs in the APK. Credential Manager returns a Google ID token; a future backend must validate it before granting server-side access.

## GitHub
Create a GitHub OAuth App, enable Device Flow, and add repository variable `GITHUB_OAUTH_CLIENT_ID`. The app never requests a GitHub password. Tokens are encrypted with Android Keystore.

## Updateable release APKs
All future releases must keep the same app ID and signing certificate. Configure `LM_AI_KEYSTORE_BASE64`, `LM_AI_STORE_PASSWORD`, `LM_AI_KEY_ALIAS`, and `LM_AI_KEY_PASSWORD` as GitHub Actions secrets. Never commit the private keystore.
