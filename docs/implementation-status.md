# Implementation status

Last updated: 2026-10-22

## M0 — Environment and project skeleton

- [x] Root Gradle Kotlin DSL settings and plugin declarations
- [x] Version catalog
- [x] Kotlin Android app module with Jetpack Compose
- [x] Minimal launch screen and manifest
- [x] Initial unit-test source
- [x] GitHub Actions workflow for unit tests, Android lint, and debug APK assembly
- [ ] Commit the generated Gradle Wrapper scripts, JAR, and properties
- [ ] Execute a clean build and confirm CI passes

## Next milestone

M1: introduce pure Kotlin domain models for Money, transaction operation types, transaction states, transition rules, and deterministic tests. Keep domain tests independent of Android framework and emulator requirements.

## Verification caveat

The source files and workflow have been committed, but no successful Gradle build or CI run is claimed. The workflow temporarily bootstraps the Gradle Wrapper because wrapper files have not yet been committed.
