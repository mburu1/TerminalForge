# Implementation status

Last updated: 2026-10-22

## M0 — Environment and project skeleton

- [x] Root Gradle Kotlin DSL settings and plugin declarations
- [x] Version catalog
- [x] Kotlin Android app module with Jetpack Compose
- [x] Minimal launch screen and manifest
- [x] GitHub Actions workflow for unit tests, Android lint, and debug APK assembly
- [ ] Commit the generated Gradle Wrapper scripts, JAR, and properties
- [ ] Execute a clean build and confirm CI passes

## M1 — Domain foundation

- [x] Money value object using integer minor units and currency checks
- [x] Transaction model, operation types, and explicit state transitions
- [x] Unit tests for money arithmetic and transaction state invariants
- [ ] Confirm domain tests pass in CI

## Next milestone

M2: build the simulated-sale vertical slice after CI verifies M0 and M1. Keep processor and hardware access behind interfaces, and persist ambiguous outcomes as UNKNOWN_OUTCOME rather than treating timeouts as declines.

## Verification caveat

The source files and workflow are committed, but a successful Gradle build is not claimed until CI completes successfully. The workflow temporarily bootstraps the Gradle Wrapper because wrapper files have not yet been committed.
