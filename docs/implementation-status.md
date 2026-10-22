# Implementation status

Last updated: 2026-10-22

## M0 — Environment and project skeleton

- [x] Root Gradle Kotlin DSL settings and plugin declarations
- [x] Version catalog
- [x] Kotlin Android app module with Jetpack Compose
- [x] Launch screen and manifest
- [x] GitHub Actions workflow for unit tests, Android lint, and debug APK assembly
- [ ] Commit the generated Gradle Wrapper scripts, JAR, and properties
- [ ] Execute a clean build and confirm CI passes

## M1 — Domain foundation

- [x] Money value object using integer minor units and currency checks
- [x] Transaction model, operation types, and explicit state transitions
- [x] Unit tests for money arithmetic and transaction state invariants
- [ ] Confirm domain tests pass in CI

## M2 — Simulated-sale vertical slice

- [x] Payment processor port and explicit approved/declined/failed/unknown results
- [x] Deterministic mock processor; approved is the default scenario
- [x] Transaction repository port and thread-safe in-memory implementation
- [x] Sale use case reusing M1 models and guarded state transitions
- [x] Compose screen wired to the approved mock sale
- [x] Unit tests for processor outcomes, persisted states, invalid amounts, duplicate idempotency keys, and processor exceptions
- [ ] Run CI and verify unit tests, lint, and debug APK assembly

## Verification caveat

M2 source and tests are committed on the `feat/m2-approved-sale` branch. A successful Gradle build or CI run is not claimed until the workflow completes successfully. The in-memory repository is process-local and is not durable storage. Payment and hardware integrations remain simulated and replaceable; no live payments or hardware SDKs are used.