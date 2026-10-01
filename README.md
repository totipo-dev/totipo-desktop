# Totipo Desktop

A Java/Swing desktop application for Totipo. Current status: **M1a lifecycle and
local observation**. The application can create/open local NIO vaults in existing
directories, show local observation progress and diagnostic codes, and manually
request another local observation with Refresh. Independent vault windows own
their sessions and close them during window/application shutdown.

Token display/editing and TOTP display are not implemented yet. A finished local
observation is not a claim of synchronization, freshness or complete history.
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
