# TerminalForge

Simulator-first Android POS Terminal Architecture Lab built with Kotlin, Java interoperability, transaction reliability, hardware abstraction, and security-by-design.

> Educational portfolio project only. Use synthetic data and mock/sandbox integrations. This is not production payment software and does not claim PCI DSS, EMVCo, card-scheme, or processor certification. Never use real cardholder data, PINs, cryptographic keys, or live payment credentials.

## M2 simulated-sale vertical slice

M2 includes an Android-independent payment processor port, deterministic mock processor, in-memory transaction repository, and ExecuteSale use case. It reuses the existing Money, Transaction, OperationType, and TransactionState model. The mock supports APPROVED, DECLINED, and FAILED scenarios. Approved references are synthetic and stable for the same transaction ID. Adapter exceptions become typed failures, and duplicate transaction IDs are rejected before processor invocation.

### Verify

From the repository root, with JDK 17 and Android SDK configured, run:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`

On Windows, use `gradlew.bat` instead of `./gradlew`. If wrapper scripts are not present, use Android Studio Gradle sync or generate and commit the wrapper first.

The M2 repository is in-memory and is not durable across process restarts. Determinism is not idempotency; duplicate logical requests, timeout ambiguity, and recovery remain M3 work.

## Architecture documentation

See [TerminalForge POS Architecture README](TerminalForge_POS_Architecture_README.md), [diagrams](docs/diagrams), and [implementation status](docs/implementation-status.md).

## Engineering principles

- Kotlin-first Android application; Java used where interoperability is useful.
- Keep domain rules independent of Android UI and concrete infrastructure.
- Route processor and hardware interactions through replaceable interfaces.
- Use explicit transaction states and reconciliation for ambiguous outcomes.
- Store money as integer minor units; never use floating-point arithmetic for amounts.
- Keep diagnostics redacted and use synthetic data only.

## Current status

M0 and M1 foundations are present. The M2 simulated-sale domain slice and its unit tests are implemented. Run the documented Gradle tasks and inspect CI before treating the build as verified.
