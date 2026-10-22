# M3 — Idempotency, timeout, callback, and recovery policy

TerminalForge is simulator-first. This policy demonstrates reliability patterns; it does not claim exactly-once remote execution or payment certification.

## Idempotency

- A logical sale is identified by its idempotency key.
- Repeating the same key and amount returns the stored transaction and does not call the processor again.
- Reusing a key with a different amount or operation is rejected.
- Persist the transaction in PROCESSING before invoking the processor.
- Adapters enforce unique transaction IDs and idempotency keys. The file adapter is an educational single-process implementation; multi-process use requires database uniqueness constraints and transactional writes.

## Timeouts and exceptions

- A timeout or unexpected adapter exception means UNKNOWN_OUTCOME, not DECLINED or FAILED.
- A worker-thread interrupt cannot establish whether an external processor completed the operation, so the coordinator does not automatically retry after timeout.
- Reconcile an ambiguous result before starting a new financial operation.
- A typed ProcessorResult.Failed maps to FAILED; an unexpected exception is treated as ambiguous.

## Duplicate callbacks

- Callback identity is (transactionId, eventId).
- The durable file inbox persists the event key before returning acceptance, so a replay after restart is rejected.
- A deployed system must record callback identity and state change atomically using a durable unique constraint.

## Restart recovery

- Run StartupRecovery after opening the durable transaction repository and before enabling sale submissions.
- A recovered PROCESSING transaction advances through UNKNOWN_OUTCOME to RECONCILIATION_REQUIRED.
- An existing UNKNOWN_OUTCOME transaction advances to RECONCILIATION_REQUIRED.
- Recovery never retries an interrupted processor request and never invents an approval or decline.

## Verification

From the repository root with JDK 17 and Android SDK configured, run :app:testDebugUnitTest, :app:lintDebug, and :app:assembleDebug. Use gradlew.bat on Windows. The repository wrapper files are not yet committed; current CI bootstraps Gradle before running these tasks.
