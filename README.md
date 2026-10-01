# Totipo Desktop

A Java/Swing desktop application for Totipo. Current status: **M3b desktop usability and accessibility**, alongside merge/conflict resolution, token create,
ordinary update, and publication uncertainty. The application supports local
vault create/open, observation and diagnostics, read-only logical-token/TOTP
browsing, manual Base32 token creation, and ordinary update of an explicitly
selected semantic alternative. Independent vault windows own their sessions and
close them during window/application shutdown. Refresh requests local observation.

Search filters logical tokens by token ID, issuer or account, including every
complete conflict alternative, with result counts and distinct empty states.
Ctrl/Cmd+F focuses Search, Escape in Search clears it, F5 refreshes, and Ctrl/Cmd+N
opens Create Token when available. Normal list arrow/Page/Home/End navigation is
preserved. Hiding a selected token clears its selection; clearing search does not
restore it or select another token. Search is temporary and stays within the process.
Forms have associated labels, accessible control names, guarded Escape cancellation
and ordinary default buttons. Long details and merge content scroll; focus is not
requested by state updates. These are concrete usability improvements, not a formal
accessibility certification.

Each vault window allows one write workflow at a time.
**Change Password…** accepts current/new/confirmation passwords and shares that
workflow slot with token editors and publication decisions. An acknowledged change
keeps the existing session open and preserves its root and tokens. Authentication
or definite observation/staging failure keeps the session usable and requires fresh
entry for another attempt. STALE and UNCERTAIN retire the session and require
explicit Open Vault. Password-change UNCERTAIN has no retry capability, automatic
retry, rollback, or preferred recovery password. Earlier unresolved token-publication
warnings remain independent.

Updates can change status (ACTIVE/TOMBSTONED) and optionally replace the secret;
existing secrets are never exported. Conflicted tokens require explicit
alternative selection for **Edit Alternative…**, which changes only that alternative.
**Resolve Conflict…** separately offers all captured alternatives or a deliberate
subset, then explicit field-by-field resolution and secret equality-group selection.
No automatic winner or automatic merge is chosen. New relevant information stops
normal merge publication and offers fresh review, cancellation, or explicitly
confirmed publication of the frozen original resolution. The latter may leave
competing alternatives.

Publication uncertainty offers **Retry exact publication** or **Stop retrying**.
Stopping releases the retry capability and leaves a persistent warning for that
open session: publication may already have occurred. Starting Create again makes
a distinct token, not a retry. Acknowledged publication and finished local
observation do not mean synchronization, freshness or complete history.
Protocol target: Totipo Vault Format **v1/r17**, through pinned Totipo Java.
Local configured-store acknowledgement is not remote synchronization or rollback
protection. Provider qualification remains limited; local NIO integration tests do
not establish guarantees for arbitrary filesystems or remote providers.

Clone with the exact dependency checkout:

```sh
git clone --recurse-submodules https://github.com/totipo-dev/totipo-desktop.git
cd totipo-desktop
```

For an existing clone:

```sh
git submodule update --init --recursive
```

Build with **JDK 25** in `JAVA_HOME` (toolchain auto-download is disabled).
Production classes target **Java 17**; tests use JDK 25. The repository wrapper
pins Gradle **9.8.0**, bin distribution and checksums.

```sh
./gradlew clean test build
./gradlew --no-daemon --no-build-cache --rerun-tasks clean test
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test
./gradlew run
```

The run command requires a graphical desktop; build and tests are headless.
Offline builds require dependencies and the wrapper distribution to be cached
by a prior online build.

The existing `nix develop` shell supplies the full `jdk25` for Swing, not a
headless JDK. To deliberately refresh reproducibility inputs, run
`./bootstrap-m0.sh` in that shell (or with JDK 25 and Gradle available).
It refreshes `flake.lock` when Nix is available, generates and verifies the
exact wrapper, writes locks and SHA-256 verification metadata, and builds.
Review all generated inputs; it never updates the Totipo Java pin.

See [ARCHITECTURE.md](ARCHITECTURE.md) for threading, state, and ownership policy,
[TOTIPO_JAVA_PIN.md](TOTIPO_JAVA_PIN.md) for the dependency boundary and pin, and
[the M0 report](review/M0_BOOTSTRAP_REPORT.md) for bootstrap evidence, and
[the M1a report](review/M1A_LIFECYCLE_OBSERVATION_REPORT.md) for lifecycle validation.
See also the [M1b report](review/M1B_READ_ONLY_TOKEN_REPORT.md) and
[M2a review report](review/M2A_CREATE_UPDATE_PUBLICATION_REPORT.md) and
[M2b review report](review/M2B_MERGE_RESOLUTION_REPORT.md).
See the [M3a password-change report](review/M3A_PASSWORD_CHANGE_REPORT.md) for lifecycle and validation evidence.
