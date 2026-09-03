# GitHub OAuth setup

lm_AI uses GitHub's OAuth Device Flow. The Android app never asks for or stores a
GitHub password, and no client secret is embedded in the APK.

## One-time setup

1. Open GitHub **Settings → Developer settings → OAuth Apps → New OAuth App**.
2. Use the repository URL as the homepage and callback URL.
3. Enable **Device Flow** in the OAuth App settings.
4. Copy the OAuth App **Client ID**.
5. In this repository, open **Settings → Secrets and variables → Actions → Variables**.
6. Create a repository variable named `GITHUB_OAUTH_CLIENT_ID` containing the Client ID.

For a local build, provide the same public Client ID as either a Gradle property or
an environment variable:

```properties
GITHUB_OAUTH_CLIENT_ID=Ov23liExample
```

Do not add a GitHub client secret, account password, or access token to the source
code, Gradle properties committed to Git, or GitHub Actions variables.

## Sign-in flow

1. In lm_AI, open **Settings → GitHub integration**.
2. Tap **Sign in with GitHub**.
3. Copy the one-time code and open GitHub from the button.
4. Approve access on GitHub.
5. lm_AI receives the access token directly from GitHub, encrypts it with Android
   Keystore, and loads the account's repositories.

Disconnecting removes the encrypted token from the device. Account-level access can
also be revoked from GitHub **Settings → Applications**.
