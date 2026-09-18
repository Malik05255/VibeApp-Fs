# H AI

H AI is an Android assistant focused on conversation, project work, and native Android app creation from the phone.

## Current scope

- **H assistant** — the main conversational interface.
- **Arabic and English** — the supported app locales.
- **AI provider routing** — free/provider routes plus user-configured providers.
- **Native Android build pipeline** — AAPT2, Java/Kotlin build tooling, D8, APK packaging and signing.
- **Project workspace** — create, edit, build, export, install, and repair Android projects.
- **GitHub integration** — Android authentication uses GitHub Device Flow; no GitHub OAuth client secret is packaged in the APK.
- **Persistence and backup** — project/history storage plus optional cloud/backup integrations where configured.
- **Diagnostics and recovery** — build diagnostics, runtime logs, snapshots, and repair flows.

## Android baseline

- Application ID: `com.malik05255.lmai`
- Minimum Android version: Android 10 / API 29
- Compile / target SDK: 36
- Current app version: 2.1.1

## Repository structure

```text
app/             H AI Android application
build-engine/    On-device Android build pipeline
shadow-runtime/  Runtime support used by the build engine
build-tools/     Embedded compiler/build tooling
cloud/           Optional cloud runtime and deployment resources
docs/            Architecture and project documentation
.github/         CI, security, readiness, and deployment workflows
```

## Quality gates

Pull requests are validated with Android build, unit tests, Android Lint, localization checks, and CodeQL. The application source should not package private OAuth client secrets.

## Build

A JDK 17 environment and the Android/Gradle toolchain are required.

```bash
./gradlew app:assembleDebug
./gradlew app:testDebugUnitTest
./gradlew app:lintDebug
```

## License

See [LICENSE](LICENSE).
