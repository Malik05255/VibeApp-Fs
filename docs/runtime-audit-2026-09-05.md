# Runtime audit — 2026-09-05

This audit was triggered by a device report where chat no longer crashed but selected `Local Gemini Nano` on a device that ML Kit explicitly reported as unsupported.

## Verified defects addressed

- Hidden Free AI routing treated a persisted local route as usable without checking ML Kit runtime capability.
- OAuth-backed OpenRouter rows could remain selectable even when the encrypted credential was missing or unreadable.
- Local Nano prompt truncation used `takeLast()` on the entire prompt and could delete the system policy/instructions on long conversations.
- OpenRouter PKCE did not bind the authorization redirect to a per-session `state` value.
- OpenRouter settings swallowed authorization-start, browser-launch, callback, and disconnect failures.
- The generic Agent error formatter hid the actionable cause when local Nano was unsupported.
- Free AI settings described the local route as ready even when runtime capability said otherwise.
- Repository guidance documented the app Java level as 11 although the app module uses JVM/JDK 17.
- `CONTRIBUTING.md` required normal fixes to target `dev`, but this fork had no `dev` branch and both Android CI and Localization Audit only watched pull requests to `main`. The missing `dev` branch was restored from the current stable main and the CI branch filters were repaired on `dev` so development PRs are actually validated.

## Verification scope

The repository's full Android CI compiles production source, runs unit tests, runs Android Lint, and builds a debug APK. Runtime-specific logic added by this audit has focused unit coverage for route availability and prompt truncation. Device/OEM behavior still requires final verification on representative Android hardware because ML Kit/AICore availability is device-dependent.

## Expected behavior

- Supported device: Local Gemini Nano remains eligible.
- Unsupported device + connected OpenRouter Free: local route is skipped and the cloud route is selected automatically.
- Unsupported device + no cloud route: local route is not left enabled; the chat shows an actionable localized message telling the user to connect OpenRouter Free.
- Stale OpenRouter OAuth row with no decryptable key: the route is filtered instead of sending the sentinel as a bearer credential.
