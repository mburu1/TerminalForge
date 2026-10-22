# TerminalForge

Simulator-first Android POS Terminal Architecture Lab built with Kotlin, Java interoperability, transaction reliability, hardware abstraction, and security-by-design.

> Educational portfolio project only. Use synthetic data and mock/sandbox integrations. This is not production payment software and does not claim PCI DSS, EMVCo, card-scheme, or processor certification. Never use real cardholder data, PINs, cryptographic keys, or live payment credentials.

## Milestones

- **M0 — Skeleton:** Android Gradle project and CI.
- **M1 — Domain foundation:** Money, transaction types, and legal state transitions.
- **M2 — Simulated sale:** Android-free processor contract, deterministic mock, transaction repository, and sale use case.
- **M3 — Reliability and recovery:** Idempotency-key enforcement, timeout ambiguity, duplicate/late callback handling, durable simulator snapshots, and startup recovery to reconciliation-required state.

## Operator experience (M2)

The Android Compose screen provides sale amount entry, progress indication, result display, transaction history, and transaction details. It calls the existing `ExecuteSale` use case; business rules remain in the domain. Transactions use an app-private file snapshot for simulator restart/recovery exercises.

## M3 reliability behavior

- A replay with the same idempotency key and same amount returns the original transaction without invoking the processor again.
- Reusing a key with a different amount is rejected.
- The transaction is persisted as PROCESSING before processor invocation.
- A timeout or unexpected adapter exception becomes UNKNOWN_OUTCOME, never a false decline or approval. The coordinator does not automatically retry.
- Callback events are deduplicated by transaction ID and event ID. Late callbacks after timeout are routed to reconciliation instead of silently rewriting the transaction outcome.
- Startup recovery moves persisted PROCESSING or UNKNOWN_OUTCOME transactions to RECONCILIATION_REQUIRED without retrying the processor.
- File-backed adapters are small single-process educational implementations. Use a database with unique constraints and atomic transactions for multi-process or production-grade persistence.

See [M3 reliability policy](docs/payments/idempotency-and-retries.md) and [implementation status](docs/implementation-status.md).

## Verify

With JDK 17 and Android SDK configured, run from the repository root:

- ./gradlew :app:testDebugUnitTest
- ./gradlew :app:lintDebug
- ./gradlew :app:assembleDebug

On Windows, use gradlew.bat. The Gradle Wrapper scripts, JAR, and distribution properties are committed. On Windows, run `gradlew.bat :app:testDebugUnitTest`, `gradlew.bat :app:lintDebug`, and `gradlew.bat :app:assembleDebug`. Build success is not claimed until CI completes successfully.

## Architecture documentation

See [TerminalForge POS Architecture README](TerminalForge_POS_Architecture_README.md), [diagrams](docs/diagrams), and [implementation status](docs/implementation-status.md).

## Engineering principles

- Kotlin-first Android application; Java used where interoperability is useful.
- Keep domain rules independent of Android UI and concrete infrastructure.
- Route processor and hardware interactions through replaceable interfaces.
- Use explicit transaction states and reconciliation for ambiguous outcomes.
- Store money as integer minor units; never use floating-point arithmetic for amounts.
- Keep diagnostics redacted and use synthetic data only.
