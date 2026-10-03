# Totipo Desktop

A Java/Swing desktop application for Totipo. Current status: **M4a release
hardening, unreleased and NOT QUALIFIED**. The feature set includes explicit
TOTP clipboard copying, alongside merge/conflict resolution, token create,
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

Visible active TOTP codes have an explicit **Copy code** button, including a separate
button for each active conflict alternative. Copying preserves the displayed digits
exactly. There is no automatic copy, rollover copy, or global Ctrl/Cmd+C override.
Totipo attempts to clear its exact current clipboard payload at code expiry or
after at most 30 seconds, whichever comes first, and when its vault closes or
the application shuts down. It never clears content it cannot positively identify
as its own exact copy.

Once copied to the operating-system clipboard, a code is outside Totipo's exclusive
control. The OS, desktop environment, clipboard managers/history, remote-desktop
systems, accessibility software, or another application may read or retain it.
Clearing is best effort: Totipo can replace only the current clipboard contents
when its exact per-copy marker is still present. It cannot delete copies retained
elsewhere and does not provide secure clipboard erasure. Clipboard unavailability
requires another deliberate Copy click; Totipo never restores previous clipboard data.

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

Tombstoning is logical deletion: tombstones and immutable history retain secrets,
and storage/synchronization providers may retain copies. Password change rewraps
the same root key; it does not rotate the root, revoke old bootstrap copies,
provide rollback protection, or recover from root compromise.

Creating with an empty password requires a separate explicit confirmation.
Existing empty-password vaults remain readable. Possession of the vault bootstrap
permits offline password guessing; Argon2id raises its cost, not its possibility.
Before creating in a location with object-looking files but a missing `vault`,
check synchronization/provider state and look for the missing bootstrap. Such
unauthenticated names cannot prove identity or recoverability and do not veto
creation. Desktop's high-level NIO create path currently provides no orphan
context; an observation-triggered warning/confirmation remains a deferred r18
application safeguard, detailed in the M4b report.

Issuer/account/client text is rendered literally; control/direction characters
and backslashes use visible escapes in browsing and merge choices. Original model
values remain exact for storage and editing, without trimming or normalization.

Publication uncertainty offers **Retry exact publication** or **Stop retrying**.
Stopping releases the retry capability and leaves a persistent warning for that
open session: publication may already have occurred. Starting Create again makes
a distinct token, not a retry. Acknowledged publication and finished local
observation do not mean synchronization, freshness or complete history.
Protocol target: Totipo Vault Format **v1/r18**, through released Totipo Java 0.1.1.
Local configured-store acknowledgement is not remote synchronization or rollback
protection. Provider qualification remains limited; local NIO integration tests do
not establish guarantees for arbitrary filesystems or remote providers.

Clone normally:

```sh
git clone https://github.com/totipo-org/totipo-desktop.git
cd totipo-desktop
```

Gradle resolves `org.totipo:totipo-storage-nio:0.1.1` and its transitive
`org.totipo:totipo-core:0.1.1` from Maven Central. Internet access is needed
for first resolution unless dependencies are already cached or Nix-provided.

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
headless JDK. `./bootstrap-m0.sh` generates/verifies the exact wrapper and
builds using existing locks and strict verification. To deliberately regenerate
locks and SHA-256 verification metadata, use `./bootstrap-m0.sh --refresh-dependencies`
with JDK 25 and Gradle available, then review all generated inputs. It does not
update the Nix flake lock or Java dependency version.

See [ARCHITECTURE.md](ARCHITECTURE.md) for threading, state, and ownership policy,
[TOTIPO_JAVA_DEPENDENCY.md](TOTIPO_JAVA_DEPENDENCY.md) for the released dependency boundary, and
[the M0 report](review/M0_BOOTSTRAP_REPORT.md) for bootstrap evidence, and
[the M1a report](review/M1A_LIFECYCLE_OBSERVATION_REPORT.md) for lifecycle validation.
See also the [M1b report](review/M1B_READ_ONLY_TOKEN_REPORT.md) and
[M2a review report](review/M2A_CREATE_UPDATE_PUBLICATION_REPORT.md) and
[M2b review report](review/M2B_MERGE_RESOLUTION_REPORT.md).
See the [M3a password-change report](review/M3A_PASSWORD_CHANGE_REPORT.md) for lifecycle and validation evidence.

## Packaging and qualification (M4a)

No first release has been declared. **Linux is the first desktop qualification
target.** Current release status is **NOT QUALIFIED**; see the exact evidence in
[QUALIFICATION.md](QUALIFICATION.md) and the blocking
[RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). Build success is not native GUI,
clipboard, accessibility, or filesystem support qualification.

Development remains `./gradlew run`. To build the canonical generic distribution:

```sh
./gradlew clean build installDist distTar distZip verifyDistributionArchives
build/install/totipo-desktop/bin/totipo-desktop
```

`build/distributions` contains the ZIP and TAR. Both contain generated launchers,
the application JAR, core/storage-nio/Bouncy Castle runtime JARs, and licenses.
They **do not include Java**: install **Java 17 or newer** and set `JAVA_HOME` or
provide `java` on PATH. Production bytecode remains Java 17. This environment has
JDK 25 only; Java 17 runtime and native packaged launch remain UNQUALIFIED.
`verifyDistribution` checks the installed layout; `verifyDistributionArchives`
also compares both archives against it. Generated launchers disable JVM Attach
API availability with `-XX:+DisableAttachMechanism`; this does not prevent
same-user process inspection or memory attacks. Development `run` is unchanged.

`VERSION` is the single application-version source. It currently names an
unreleased development state. `./gradlew validateVersion -PreleaseBuild=true`
intentionally fails until an actual candidate version replaces the sentinel.
Missing, empty, whitespace-bearing, or unsafe filename versions fail configuration.

### Nix package — human build validation passed

The flake exposes a Linux-only package using the full pinned `jdk25`, Gradle 9
and the same `installDist`. Existing dev-shell/jailed-agent inputs remain intact.
The source is an ordinary repository checkout. Java arrives as released Maven
artifacts through the dependency cache. After regenerating that cache:

```sh
nix flake check
nix build .
nix run .
# Native qualification must also use the installed command:
./result/bin/totipo-desktop
```

The wrapper fixes `JAVA_HOME` to the managed full JDK and supplies launcher shell
utilities. Runtime use requires neither Gradle, checkout sources nor shell
Java configuration. The desktop entry is Totipo / Utility / Terminal=false, with
no handlers, autostart or placeholder icon. Vaults remain user-selected paths;
installation directories are never vault storage.

**M4b repin status:** Human-operated Nix dependency-cache regeneration passed.
The reviewed generated `package-deps.json` contains the released Totipo 0.1.1
JAR/module/POM hashes, retains BC 1.86, and has no unrelated dependency changes.
Human-operated x86_64-linux flake check, build and forced rebuild passed;
the full supplied package inventory has the expected files, and all four JAR
hashes match the verified Gradle distribution. The operator also reported a
successful launch and quick smoke test. Native qualification remains separate.
No Nix command was run by the agent. Current evidence and qualification limits
are in [the M4b report](review/M4B_TOTIPO_JAVA_0_1_1_R18_REPIN_REPORT.md).
Prior M4a package evidence does not validate this repin. Native qualification
remains UNQUALIFIED. Do not hand-edit dependency hashes.

From the repository root, generate/refresh using the official update script:

```fish
set update_script (
    nix build \
        --no-link \
        --print-out-paths \
        'path:.#packages.x86_64-linux.default.mitmCache.updateScript'
)

$update_script

nix flake check path:.
nix build path:.
nix build --rebuild path:.
```

Substitute `aarch64-linux` only when qualifying that system. For this uncommitted
review, `path:.` includes uncommitted/untracked migration files that Git-based
flake sources can omit. Do not update `flake.lock` or Gradle verification hashes
just to make the build pass. Review `package-deps.json`: it should match the locked
released Totipo artifacts, retain BC 1.86, and leave unrelated dependencies unchanged.
The update task runs desktop checks and distribution verification only.
An ordinary second build proves cache reuse; the forced rebuild is separate.
MITM transport does not waive Gradle's artifact hash verification.

The derivation sets `LC_ALL=C.UTF-8` for dependency fetching and package tests.
NIO filename encoding follows the native locale: `-Dfile.encoding=UTF-8` alone
does not make Unicode paths representable under the C locale. After changing
`package.nix`, regenerate the update script with the command above; an existing
Nix-store script still references its original derivation/source snapshot.

Pinned nixpkgs supplies Gradle **9.7.1**, running on the same JDK 25. The developer
and CI wrapper remains **9.8.0**. See the M4a report for compatibility evidence;
the operator reports successful MITM/package execution. Nix's patched Gradle and
official setup hook are retained instead of introducing a separate wrapper download in the build.

### Explicit filesystem qualification

Create/select an empty disposable test root on each filesystem to be claimed:

```sh
./gradlew filesystemQualification -PqualificationRoot=/explicit/disposable/test/root
```

No default root is provided. The harness creates a unique `totipo-qualification-`
child, uses only test credentials, and deletes only that child without following
symlinks. Keep the selected root private and do not use production vault storage.
Cleanup failure prints the exact remaining test directory. Record the non-secret
environment output and checklist results in QUALIFICATION.md; omit the root path.
A pass applies only to that tested environment, not other Linux filesystems,
network shares, cloud synchronization or FUSE configurations.

Updates are manual. M4a adds no updater, native installers, signing, tags or
publication. Future final artifacts get SHA-256 checksums with
`scripts/sha256-release-artifacts.sh FILE [FILE ...]`; it does not change or upload
files. See [THIRD_PARTY.md](THIRD_PARTY.md) for the production inventory and
[the M4a report](review/M4A_RELEASE_HARDENING_REPORT.md) for all limitations.
