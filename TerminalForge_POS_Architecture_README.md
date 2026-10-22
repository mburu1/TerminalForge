# TerminalForge --- Android POS Terminal Architecture Lab

> **Portfolio project inspired by the engineering challenges in a
> Principal Mobile Engineer (POS-Terminal) role.**
>
> TerminalForge is an educational, simulator-first Android POS platform
> built with **Kotlin, Java, Android, and an optional Kotlin/JVM
> backend**. It demonstrates architecture, transaction reliability,
> hardware abstraction, security boundaries, testing, observability, and
> technical leadership practices.
>
> **Scope and safety:** This is not Moniepoint software and is not
> affiliated with Moniepoint. It is not a production payment terminal.
> Use synthetic data and mock/sandbox integrations only. Do not collect,
> store, or transmit real cardholder data, PINs, cryptographic keys, or
> live payment credentials. Do not implement an EMV kernel or payment
> cryptography from scratch.

## 1. Project objectives

Build a credible engineering portfolio demonstrating:

-   Android-native engineering with Kotlin first and Java
    interoperability where useful.
-   POS transaction lifecycles, retries, idempotency, reconciliation,
    and offline states.
-   Hardware integration through replaceable interfaces for printers,
    card readers, and terminal status.
-   Secure-by-design boundaries, threat modelling, redacted logs, and
    secret management.
-   Payment-gateway integration through a mock/sandbox adapter.
-   Unit, integration, contract, UI, resilience, and performance
    testing.
-   Architecture decision records (ADRs), diagrams, runbooks, code
    review standards, and release gates.
-   A maintenance/repair workflow for terminal inventory, diagnostics,
    fault reports, and repair status.

## 2. Windows 11 setup: what to install

For Windows 11 with **16 GB DDR4 RAM**, keep the setup lean. Android
Studio, Gradle, an emulator, and a browser can consume significant
memory.

### Essential software

  -----------------------------------------------------------------------
  Software                Why you need it         Recommendation
  ----------------------- ----------------------- -----------------------
  Android Studio          Android IDE, SDK        Install the stable
                          manager, emulator,      release. Use its
                          Kotlin tooling          bundled JBR unless the
                                                  project requires
                                                  another JDK.

  Android SDK Platform +  Compile and package the Install the API level
  Build Tools             Android app             required by the project
                                                  and a recent stable
                                                  platform.

  Android SDK             `adb` and device        Install through SDK
  Platform-Tools          diagnostics             Manager.

  Git for Windows         Source control and Git  Install and configure
                          Bash                    your name/email and SSH
                                                  or HTTPS
                                                  authentication.

  A physical Android      Real-device testing     Prefer this for routine
  device (if available)                           testing if your
                                                  computer struggles with
                                                  an emulator.

  diagrams.net            Architecture, sequence, Store editable diagram
                          deployment, and threat  sources under
                          diagrams                `docs/diagrams/`.
  -----------------------------------------------------------------------

### Useful optional tools

  -----------------------------------------------------------------------
  Software                            When to install it
  ----------------------------------- -----------------------------------
  IntelliJ IDEA Community             Optional for separate JVM/backend
                                      work. Avoid running it alongside
                                      Android Studio if memory is tight.

  Postman or Bruno                    Choose one to inspect mock/sandbox
                                      backend APIs.

  Docker Desktop                      Only if you need containers for a
                                      local backend or dependencies; it
                                      adds memory overhead.

  PlantUML + Graphviz                 Useful for version-controlled UML
                                      diagrams reviewed as text diffs.

  VS Code                             Optional for Markdown, YAML, and
                                      scripts if Android Studio is not
                                      enough.

  Scrcpy                              Optional for mirroring a physical
                                      Android device over USB. Download
                                      from a trusted release source and
                                      use USB debugging carefully.
  -----------------------------------------------------------------------

### Do I need to install Kotlin separately?

**No.** For normal Android development, Kotlin support is built into
Android Studio, and the Gradle build resolves the Kotlin tooling
required by the project. You generally do not need a standalone Kotlin
compiler.

Use **Kotlin as the primary language** for new Android code. Keep Java
available for interoperability, legacy APIs, vendor SDK examples, or a
deliberate Java module. Install a standalone Kotlin compiler only if you
specifically need Kotlin scripts or command-line compilation outside the
Gradle/IDE workflow.

### JDK guidance

-   Start with Android Studio's bundled **JetBrains Runtime (JBR)** for
    the IDE.
-   Android builds use the JDK selected by Gradle/Android Studio. Check
    the project's Android Gradle Plugin and Gradle compatibility
    requirements before choosing a JDK.
-   Do not install several JDK versions "just in case." Add a separate
    JDK only when a build requires it.
-   Verify with `java -version`, `.\gradlew.bat --version`, and Android
    Studio's Gradle JDK setting.

### Emulator and memory strategy

1.  Prefer a physical Android phone for routine testing where possible.
2.  Create one emulator profile rather than several; use a modest device
    profile and a supported x86_64 system image.
3.  Close Docker and other memory-heavy applications while running the
    emulator.
4.  Start with Gradle defaults; tune memory only after measuring.
5.  Do not assume an emulator proves compatibility with physical card
    readers, PIN pads, printers, or secure hardware.

### Verify installation

In PowerShell:

``` powershell
git --version
java -version
adb version
```

If `adb` is not on `PATH`, run it from the Android SDK `platform-tools`
directory or add that directory to your user `PATH`.

In Android Studio:

1.  Open **SDK Manager** and confirm Android SDK Platform, Build-Tools,
    and Platform-Tools are installed.
2.  Open **Device Manager** and create one emulator, or enable USB
    debugging on a test device.
3.  Create a Kotlin Android project and run it.
4.  Run the project's Gradle wrapper tasks before adding architecture or
    dependencies.

## 3. Product scope

### Primary application: TerminalForge POS

A simulator-first Android application with these flows:

1.  Operator opens a local demo session.
2.  Operator selects **Sale**, **Refund simulation**, **Reversal
    simulation**, or **Transaction history**.
3.  The app validates the request and creates a transaction with a
    unique local ID and idempotency key.
4.  A payment orchestrator calls a mock processor and mock
    terminal-hardware adapter.
5.  Transaction state is persisted and shown to the operator.
6.  Simulated interruptions and duplicate requests test retry safety.
7.  A reconciliation screen compares local transaction records with a
    mock settlement feed.

### Companion workflow: Terminal Care

A maintenance module for terminal inventory, health diagnostics, issue
reports, repair assignments, and repair status. It must not expose
sensitive payment data.

### Explicitly out of scope for the first release

-   Live payments, production credentials, real card data, real PIN
    capture, or key injection.
-   Implementing an EMV kernel or cryptographic payment functions from
    scratch.
-   Claiming PCI DSS, EMVCo, card-scheme, or processor certification.
-   Integrating a real POS vendor SDK without authorization and vendor
    documentation.

## 4. Target repository structure

``` text
terminalforge/
├── README.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── app/                         # Android entry point and navigation
├── core/
│   ├── common/                  # Result types, dispatchers, clock, IDs
│   ├── model/                   # Shared immutable domain models
│   ├── database/                # Room entities, DAOs, migrations
│   ├── network/                 # HTTP client and API error mapping
│   ├── security/                # Redaction and secure-storage interfaces
│   ├── testing/                 # Test fixtures and fake implementations
│   └── ui/                      # Shared UI components
├── feature/
│   ├── operator/                # Demo operator session
│   ├── sale/                    # Simulated sale workflow
│   ├── refund/                  # Refund simulation
│   ├── transactions/            # History and transaction details
│   ├── reconciliation/          # Settlement comparison
│   └── terminal-care/           # Repair and maintenance workflow
├── domain/
│   ├── transaction/             # Use cases and domain rules
│   ├── payment/                 # Payment orchestration contracts
│   ├── terminal/                # Hardware capability contracts
│   └── reconciliation/          # Matching and discrepancy rules
├── data/
│   ├── local/                   # Room-backed repositories
│   ├── remote/                  # Mock/sandbox API adapters
│   └── repository/              # Repository implementations
├── integrations/
│   ├── payment-mock/            # Deterministic fake processor
│   ├── terminal-simulator/      # Fake reader/printer/status events
│   └── vendor-contracts/        # Interfaces only; no vendor binaries
├── backend/                     # Optional Kotlin/JVM service, later phase
│   ├── build.gradle.kts
│   └── src/
├── docs/
│   ├── architecture/
│   │   ├── system-context.md
│   │   ├── container-and-module-view.md
│   │   ├── transaction-lifecycle.md
│   │   ├── failure-model.md
│   │   └── deployment-view.md
│   ├── adr/
│   │   ├── 0001-kotlin-first.md
│   │   ├── 0002-modular-android.md
│   │   ├── 0003-payment-adapter-boundary.md
│   │   └── 0004-local-transaction-persistence.md
│   ├── diagrams/
│   │   ├── context.mmd
│   │   ├── transaction-sequence.mmd
│   │   ├── state-machine.mmd
│   │   └── deployment.mmd
│   ├── security/
│   │   ├── threat-model.md
│   │   ├── data-classification.md
│   │   ├── logging-policy.md
│   │   └── release-security-checklist.md
│   ├── payments/
│   │   ├── transaction-state-machine.md
│   │   ├── idempotency-and-retries.md
│   │   ├── reconciliation.md
│   │   └── emv-integration-boundary.md
│   ├── hardware/
│   │   ├── capability-interfaces.md
│   │   └── vendor-integration-checklist.md
│   ├── testing/
│   │   ├── test-strategy.md
│   │   ├── test-matrix.md
│   │   └── performance-baselines.md
│   ├── operations/
│   │   ├── runbook.md
│   │   ├── incident-response.md
│   │   └── release-and-rollback.md
│   └── decisions/
│       └── risk-register.md
├── scripts/
│   ├── verify.ps1
│   └── collect-diagnostics.ps1
└── .github/
    └── workflows/
        └── android-ci.yml
```

This is the **target structure**, not a requirement to create every
directory on day one. Start with `app`, `core/model`, `domain`, `data`,
`integrations/payment-mock`, `integrations/terminal-simulator`, `docs`,
and tests. Extract modules when they enforce useful boundaries.

## 5. Architecture and dependency rules

A simplified transaction path:

``` text
UI → Use case → Payment orchestrator → PaymentProcessor interface
                                      → TerminalHardware interface
                                      → TransactionRepository interface
                                               ↓
                                      Local persistence / mock backend
```

Dependency rules:

-   `app` handles entry points and dependency-injection wiring; domain
    rules do not belong in Activities or Composables.
-   `feature/*` depends on domain use cases and shared UI/core
    utilities, not concrete vendor implementations.
-   `domain/*` depends on Kotlin/JVM abstractions and domain
    models---not Android UI, Room, Retrofit, or vendor SDK classes.
-   `data/*` implements repository interfaces and maps storage/network
    models to domain models.
-   `integrations/*` implements payment and hardware ports. Domain code
    must not depend on a specific provider.
-   Vendor SDKs stay behind adapter interfaces and are added only when
    authorized hardware and documentation are available.
-   No module may log secrets, PINs, full PANs, track data,
    cryptographic keys, or authentication tokens.

## 6. Domain model and transaction lifecycle

### Suggested core models

-   `Money`: amount in minor units plus currency; avoid floating-point
    money arithmetic.
-   `Transaction`: immutable ID, amount, currency, operation type,
    state, timestamps, idempotency key, and safe processor reference.
-   `PaymentAttempt`: attempt number, outcome category, start/end times,
    and safe diagnostic code.
-   `Terminal`: terminal ID, model, firmware metadata, capability set,
    and health status.
-   `HardwareResult`: success, cancelled, unavailable, timeout, or
    failure---without sensitive payloads.
-   `ReconciliationRecord`: local transaction reference, processor
    reference, amount/currency, matching result, and discrepancy reason.

### State machine

``` text
CREATED → VALIDATING → READY → PROCESSING
                                  ├── APPROVED
                                  ├── DECLINED
                                  ├── UNKNOWN_OUTCOME
                                  └── FAILED
UNKNOWN_OUTCOME → RECONCILIATION_REQUIRED → RESOLVED
APPROVED → REVERSAL_PENDING → REVERSED
```

Rules to document and test:

-   A timeout does **not** necessarily mean a payment failed. Treat
    ambiguous outcomes as `UNKNOWN_OUTCOME` and reconcile before
    retrying a new financial operation.
-   Use an idempotency key for a logical operation. Repeating a request
    with the same key must not create a second logical charge in the
    simulator.
-   Do not silently turn a refund or reversal into a new sale.
-   Record state transitions and safe diagnostic codes for auditability.
-   Use deterministic clocks in tests and well-defined timestamp
    handling.
-   Local persistence alone cannot guarantee exactly-once processing
    across a remote processor. Design for idempotency, at-least-once
    delivery, and reconciliation.

## 7. Hardware integration strategy

Define capabilities before integrating devices:

``` kotlin
interface TerminalHardware {
    suspend fun getStatus(): TerminalStatus
    suspend fun printReceipt(receipt: Receipt): PrintResult
    suspend fun cancelCurrentOperation(): CancelResult
}

interface CardReader {
    suspend fun startCardRead(request: CardReadRequest): CardReadResult
}

interface ReceiptPrinter {
    suspend fun print(receipt: Receipt): PrintResult
}
```

The simulator should return deterministic results and allow tests to
trigger disconnects, timeouts, paper-out, cancellation, and duplicate
callbacks. Real integrations must use the manufacturer's supported SDK
and device-specific test process. A generic emulator does not prove
peripheral or secure-hardware compatibility.

## 8. Security and compliance posture

-   Use mock/sandbox transactions with synthetic data only.
-   Never handle PIN blocks, track data, real PANs, or cryptographic
    keys in this educational app.
-   Do not implement EMV cryptography or key management yourself.
    Document the boundary to an approved terminal kernel, secure
    PIN-entry device, processor, and certified vendor stack.
-   Keep secrets out of source control, resources, logs, screenshots,
    and crash reports.
-   Redact diagnostic logs and use least privilege.
-   Add a threat model covering rooted devices, app tampering, debug
    builds, replay, duplicate callbacks, insecure local storage, API
    impersonation, and compromised dependencies.
-   State clearly that the project is **not certified** and does not
    establish PCI DSS, EMVCo, or card-scheme compliance.

## 9. Code milestones and acceptance criteria

Every milestone should end with a runnable build, tests, and updated
documentation. Avoid creating the entire target tree before the first
vertical slice works.

### M0 --- Environment and project skeleton

**Implement** - Android Studio project, Kotlin-first Gradle
configuration, version catalog, lint/format configuration. - Basic CI
workflow that builds and tests on a clean runner. - Initial README, ADR
template, and architecture context diagram.

**Done when** - App builds from the Gradle wrapper. - A clean checkout
can run verification. - No secrets or machine-specific paths are
committed.

### M1 --- Domain foundation

**Implement** - `Money`, `Transaction`, `TransactionState`, operation
types, safe result/error types. - State-transition rules and
deterministic ID/clock abstractions. - Unit tests for valid and invalid
transitions.

**Done when** - Money arithmetic uses minor units and validates
currency/amount. - Illegal state transitions fail predictably. - Domain
tests do not require an Android emulator.

### M2 --- First vertical slice: simulated sale

**Implement** - Minimal operator screen and sale amount input. - Use
case → orchestrator → mock processor → repository. - Local transaction
history and details.

**Done when** - Approved, declined, and failed simulated sales can be
demonstrated. - Each attempt has a traceable safe ID. - UI remains
responsive during suspendable operations.

### M3 --- Reliability and recovery

**Implement** - Idempotency-key handling. - Timeouts, duplicate
callbacks, cancellation, unknown outcomes, and retry policy. -
Persistence and startup recovery of in-progress transactions. -
Reconciliation-required state for ambiguous results.

**Done when** - Replaying the same logical request does not create a
duplicate simulated charge. - A timeout never automatically becomes a
definitive decline. - Tests cover process interruption and repeated
callbacks.

### M4 --- Hardware abstraction

**Implement** - `CardReader`, `ReceiptPrinter`, and
`TerminalStatusProvider` interfaces. - Terminal simulator with
configurable capabilities and fault injection. - Receipt rendering using
synthetic transaction information only.

**Done when** - Features depend on interfaces, not simulator
implementation classes. - Printer-out, reader-disconnected,
cancellation, and timeout cases are testable. - Unsupported device
capabilities are handled explicitly.

### M5 --- Refunds, reversals, and reconciliation

**Implement** - Separate refund and reversal use cases. - Mock processor
settlement feed. - Matching rules and discrepancy report.

**Done when** - Refund/reversal preconditions are validated. - Duplicate
operations are rejected or safely idempotent. - Unmatched and
amount-mismatched records are visible and auditable.

### M6 --- Security engineering

**Implement** - Data classification, threat model, safe logging wrapper,
dependency scanning, release checklist. - Demo-session handling and
appropriate Android storage boundaries. - Negative tests for
sensitive-field leakage.

**Done when** - Logs and exception messages do not expose sensitive
fields. - Debug-only behavior cannot be mistaken for production
configuration. - Security limitations and non-certification are
prominent.

### M7 --- Terminal Care

**Implement** - Terminal inventory, health status, issue report, repair
ticket, assignment, and lifecycle. - Local repository first; optional
mock backend API later. - Role-aware demo views without claiming
production identity security.

**Done when** - A repair ticket can be created, assigned, updated, and
closed. - The workflow is tested and documented. - Maintenance data is
separated from payment credentials and transaction secrets.

### M8 --- Quality, performance, and observability

**Implement** - Unit, repository, API-contract, UI, migration, and
fault-injection tests. - Structured, redacted diagnostic events and
correlation IDs. - Startup/transaction-flow performance measurements on
a physical device where possible.

**Done when** - CI runs build, unit tests, lint, and static checks. -
Performance claims include device, build type, workload, and measurement
method. - Known risks and test gaps are documented.

### M9 --- Optional Kotlin/JVM backend and sandbox API

**Implement** - A small Kotlin/JVM service (choose Ktor or Spring Boot,
not both initially). - API contracts for terminal registration, repair
tickets, mock transaction status, and reconciliation import. - Demo
authentication, validation, and rate limiting appropriate to the local
demo.

**Done when** - API contract tests run in CI. - App handles
offline/backend-unavailable responses. - No live processor credentials
or real card data are accepted.

### M10 --- Principal-level architecture handover

**Implement** - C4 context/container/module views, transaction sequence,
state machine, threat model, ADRs, risk register, runbooks, and
release/rollback process. - Code-review checklist and a short technical
decision presentation. - Demo script showing normal and failure paths.

**Done when** - Another engineer can build, run, test, understand
failure handling, and explain the trade-offs from the repository
alone. - Every architecture diagram matches the code. - Limitations,
open risks, and next steps are explicit.

## 10. Testing strategy

  --------------------------------------------------------------------------
  Layer                   Focus                      Example cases
  ----------------------- -------------------------- -----------------------
  Domain unit tests       Invariants and transitions Invalid amount, illegal
                                                     state change, currency
                                                     mismatch

  Use-case tests          Business orchestration     Sale approved/declined,
                                                     refund eligibility

  Adapter tests           Hardware/processor mapping Timeout, disconnect,
                                                     malformed response

  Repository tests        Persistence correctness    Migration, restart
                                                     recovery, duplicate ID

  Contract tests          API behavior               Validation errors,
                                                     version compatibility,
                                                     idempotency

  UI tests                Operator workflows         Submit sale, show
                                                     outcome, navigate
                                                     history

  Resilience tests        Failure recovery           Duplicate callback,
                                                     offline restart,
                                                     ambiguous timeout

  Security tests          Data                       Secret leakage, debug
                          exposure/configuration     flags, unsafe logs

  Performance tests       Responsiveness/stability   Startup, history
                                                     loading, repeated
                                                     transactions
  --------------------------------------------------------------------------

Use fakes for fast deterministic tests; use instrumented tests only
where Android framework behavior matters. Keep all test data synthetic.

## 11. Local verification and CI

Suggested local checks, adjusted to actual Gradle task names:

``` powershell
# Run from the repository root
.\gradlew.bat tasks
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

Add tasks only when the corresponding plugin/configuration exists. CI
should use the Gradle wrapper, cache dependencies safely, run on a clean
environment, and retain test/lint reports. Pin action versions and
review third-party actions before use.

## 12. Suggested implementation dependencies

Choose stable versions compatible with the Android Gradle Plugin and
project JDK at implementation time; do not blindly copy versions from
old tutorials.

-   Kotlin and Android Gradle Plugin
-   AndroidX Activity and Lifecycle
-   Jetpack Compose **or** XML Views (choose one initially; Compose is a
    sensible greenfield default)
-   Kotlin Coroutines and Flow
-   Room for local persistence
-   DataStore for appropriate non-secret preferences
-   AndroidX Navigation if needed
-   Hilt or another dependency-injection approach when module wiring
    benefits from it
-   Retrofit/OkHttp or Ktor Client for a remote API, if added
-   JUnit, coroutine test utilities, and AndroidX test libraries
-   Android Lint and optionally Detekt/ktlint

Add libraries to solve requirements, not merely to make the dependency
list look enterprise-grade.

## 13. Documentation deliverables

-   Problem statement, users, assumptions, and explicit non-goals.
-   C4 system-context and container/module diagrams.
-   Transaction sequence and state-machine diagrams.
-   ADRs with context, options, decision, consequences, and status.
-   Domain glossary and API contract.
-   Data classification and threat model.
-   EMV/vendor integration boundary and certification caveats.
-   Hardware capability matrix.
-   Test strategy, traceability matrix, and actual performance
    baselines.
-   Failure runbook, diagnostics guide, incident response, release and
    rollback checklist.
-   Risk register with owner, severity, mitigation, and residual risk.
-   Demo instructions and screenshots using synthetic data only.

## 14. Definition of done

A milestone is complete only when:

-   The app builds from a clean checkout using the Gradle wrapper.
-   Tests and lint pass, or known failures are recorded with reasons.
-   Business logic is isolated from UI and concrete infrastructure.
-   Failure paths are tested, not just the happy path.
-   Logs are redacted and data is synthetic.
-   Relevant diagrams, ADRs, and README sections are updated.
-   No real payment secrets, vendor binaries, customer data, or
    machine-specific paths are committed.

## 15. Recommended first week

1.  **Day 1:** Install Android Studio/SDK and Git; create and run a
    blank Kotlin app.
2.  **Day 2:** Create the repository skeleton, CI build, and
    architecture context diagram.
3.  **Day 3:** Implement domain models and transaction state-machine
    tests.
4.  **Day 4:** Build the simulated-sale vertical slice end to end.
5.  **Day 5:** Add idempotency, duplicate-callback, timeout, and
    unknown-outcome tests.
6.  **Day 6:** Add terminal simulator interfaces and fault injection.
7.  **Day 7:** Update ADRs, threat model, demo instructions, and record
    implemented versus planned work.

## 16. How to present this portfolio honestly

Describe it as a **simulator-first Android POS architecture lab**
demonstrating transaction lifecycle design, reliability patterns,
hardware abstraction, security boundaries, and test strategy. Do not
claim eight years of POS experience, production EMV implementation,
payment certification, or Moniepoint affiliation unless those claims are
independently true.

The strongest evidence is a runnable demo plus tests that reproduce
difficult failure scenarios---not a large directory tree or unsupported
claims.
