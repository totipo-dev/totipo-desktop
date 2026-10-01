# Totipo Desktop

A Java/Swing desktop application for Totipo. Current status: **M2b explicit merge/conflict resolution**, alongside token create,
ordinary update, and publication uncertainty. The application supports local
vault create/open, observation and diagnostics, read-only logical-token/TOTP
browsing, manual Base32 token creation, and ordinary update of an explicitly
selected semantic alternative. Independent vault windows own their sessions and
close them during window/application shutdown. Refresh requests local observation.

Each vault window allows one write workflow at a time.
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
