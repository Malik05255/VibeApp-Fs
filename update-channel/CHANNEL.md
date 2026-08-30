# ALMI update channel

This branch is the controlled publishing pointer for ALMI latest-only updates.

- The first channel commit creates the bootstrap release.
- Each later channel commit publishes one newer mandatory latest release.
- The app UI only reads the latest manifest.
- Delta patches are generated from supported installed bases.
- Rollback and reapply packages preserve Android monotonic versionCode rules.
