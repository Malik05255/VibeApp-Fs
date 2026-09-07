# LMAI runtime identity contract

LMAI is an independent Android application.

- Stable Android applicationId: `com.malik05255.lmai`
- Kotlin/Android namespace: `com.malik.lmai`
- Build-engine namespace: `com.malik.lmai.build.engine`
- OAuth/deep-link scheme: `lmai://`
- Display brand: `LM_AI`
- Application, build-engine, tests, AIDL, Room schemas, generated runtime assets, and operational configuration must not use the legacy `com.vibe.*` identity.
- Runtime/source class names and paths must not retain VibeApp branding.

The applicationId is intentionally stable so existing LMAI installations can be upgraded in place while remaining installable alongside the original VibeApp package.

Historical Git history and legally required upstream attribution are not runtime dependencies and are intentionally preserved.
