# Stable runtime identity contract

H AI is an independent Android application. Its stable internal Android identity intentionally retains the existing LMAI package and deep-link identifiers for upgrade compatibility.

- Stable Android applicationId: `com.malik05255.lmai`
- Kotlin/Android namespace: `com.malik.lmai`
- Build-engine namespace: `com.malik.lmai.build.engine`
- OAuth/deep-link scheme: `lmai://`
- Display brand: `H AI`
- Application, build-engine, tests, AIDL, Room schemas, generated runtime assets, and operational configuration must not use the legacy `com.vibe.*` identity.
- Runtime/source class names and paths must not retain VibeApp branding.

The applicationId and `lmai://` scheme are intentionally stable so existing installations can be upgraded in place. They are internal compatibility identifiers and do not define the public H AI display brand.

Historical Git history and legally required upstream attribution are not runtime dependencies and are intentionally preserved.
