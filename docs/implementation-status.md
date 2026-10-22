# Implementation status

Last updated: 2026-10-22

## M0 — Skeleton
- [x] Gradle Kotlin DSL, version catalog, Compose app, manifest, CI workflow
- [x] Commit Gradle Wrapper scripts, JAR, and distribution properties
- [ ] Confirm clean build and CI success

## M1 — Domain foundation
- [x] Money value object and currency validation
- [x] Transaction model, operation types, legal state transitions
- [x] Money and transaction unit tests

## M2 — Simulated-sale vertical slice
- [x] Android-free PaymentProcessor contract and typed results
- [x] Deterministic mock for approved, declined, and failed outcomes
- [x] TransactionRepository contract and thread-safe in-memory adapter
- [x] ExecuteSale use case reusing existing domain state transitions
- [x] Tests for deterministic output, approval, decline, processor failure, adapter exceptions, retrieval, and duplicate IDs
- [x] Compose operator experience: sale entry, progress indication, typed result display, history, and transaction details wired to ExecuteSale
- [ ] Run Gradle tests and confirm CI passes

## M3 — Reliability and recovery
- [x] Idempotency-key replay returns the original transaction and conflicting payloads are rejected
- [x] Persist PROCESSING before processor invocation
- [x] Timeout/adapter exception maps to UNKNOWN_OUTCOME without automatic retry
- [x] Callback event deduplication and late-callback reconciliation routing
- [x] File-backed transaction snapshot and callback inbox for restart exercises
- [x] Startup recovery moves interrupted transactions to RECONCILIATION_REQUIRED
- [x] Tests for idempotency, timeout, duplicate callbacks, late callbacks, and restart recovery
- [ ] Run Gradle tests, lint, assemble, and confirm CI passes

## M4 — Hardware abstraction
- [x] Android-free card-reader, receipt-printer, and terminal-status ports
- [x] Configurable simulator for contactless/contact/magstripe reads and peripheral faults
- [x] Typed disconnection, timeout, out-of-paper, and device-fault outcomes
- [x] Cancellation propagates as coroutine cancellation, not as a payment decline
- [x] Unit tests for default behavior, faults, status reporting, and operator cancellation
- [ ] Run Gradle tests, lint, assemble, and confirm CI passes

## Scope notes
Hardware interfaces are ports only; no live device SDK, PIN capture, payment cryptography, or certification claims are included. Simulator faults are deterministic and injected through configuration. File-backed transaction storage is an educational single-process adapter, not a substitute for Room/SQL transactions or a unique constraint in a multi-process production system. Exactly-once execution across an external processor is not claimed. Build success is not claimed until CI completes successfully.
