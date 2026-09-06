# LMAI runtime identity contract

LMAI is an independent Android application.

- Stable Android applicationId: `com.malik05255.lmai`
- Kotlin/Android namespace: `com.malik.lmai`
- OAuth/deep-link scheme: `lmai://`
- Display brand: `LM_AI`
- Source packages, manifests, AIDL and Room schema packages must not use `com.vibe.app`.
- Runtime/configuration source must not reference `VibeApp`.

The applicationId is intentionally stable so existing LMAI installations can be upgraded in place while remaining installable alongside the original VibeApp package.
