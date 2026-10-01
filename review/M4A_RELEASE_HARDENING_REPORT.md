# M4a release hardening report

## Outcome and evidence ownership

M4a adds a verified generic distribution, explicit unreleased versioning,
a Linux Nix package foundation, an operator-run filesystem harness, inventories,
and blocking qualification/release checklists. All changes are **uncommitted and
unstaged**. No release has been declared, committed, tagged, published, signed or
uploaded. Application/protocol behavior is unchanged.

The operator reports successful execution of the intended Nix dependency-cache
and package workflow after the source-filter, locale and installation-check fixes.
The generated cache and package configuration were independently inspected during
this completion pass. **No Nix CLI command or update script was run by the agent**,
by explicit instruction; this does not invalidate the operator's execution.
Nix version, output hash and exact forced-rebuild outcome: **Operator reports
successful Nix execution; exact value not recorded**. Nix reproducibility remains
UNQUALIFIED without an explicit comparison result.

The existing `result` symlink records
`/nix/store/zmdbmdkvn1l4rankgdgqaadi8ccmb6pm-totipo-desktop-0.0.0-dev`.
Its target is unavailable in this agent filesystem, so this is a recorded output
reference, not a claim of independently inspected installed bytes. The configured
installation and install checks were inspected statically. No supplied evidence
contradicts the operator's successful-run statement.

Native GUI/clipboard/accessibility and Java 17 runtime qualification could not
be performed. They remain UNQUALIFIED for M4b; a green build is not release readiness.

## Original implementation baseline (historical)

- HEAD: `7e4fc56 implement m3c`; `git status --short` initially empty.
- Gitlink and checked-out vendor HEAD:
  `3d97dca72604f39b6b475c0d5a0a8b076632cc42`, matching TOTIPO_JAVA_PIN.md.
  Vendor worktree was and remains clean. No submodule update was run.
- Reviewed build/settings, strict desktop locking/verification, wrapper, flake and
  lock, CI, README, ARCHITECTURE, M3c clipboard qualification notes.
- Reviewed all desktop real-NIO integration tests: NioSmokeTest (four cases) and
  NioPasswordChangeTest (two cases), plus public create/update/merge APIs.
- Baseline Gradle distribution task inspection confirmed `installDist`, `distTar`,
  `distZip`, and `assembleDist` already come from the application plugin.
- Production tree before and after: desktop → pinned storage-nio → pinned core →
  `org.bouncycastle:bcprov-jdk18on:1.86`. Zero Java production or test dependencies
  added. Locks, verification metadata, wrapper, flake.lock, settings and vendor
  source have no changes. JUnit 6.1.3 and its existing test-only tree are not shipped.

## Packaging reference and decisions

Inspected current upstream
[opvaultfx package.nix](https://github.com/ingon/opvaultfx/blob/master/package.nix),
[flake.nix](https://github.com/ingon/opvaultfx/blob/master/flake.nix), and
[build.gradle](https://github.com/ingon/opvaultfx/blob/master/build.gradle).
Reused the derivation pattern, official Gradle MITM cache, full-JDK launcher
wrapper, desktop-item helpers and bytecode provenance metadata. Totipo installs
`installDist` directly instead of extracting a TAR during installation.

The OpenJFX/Beryx plugins and jlink configuration were not adopted: Totipo is
ordinary Swing and already has an application layout. No Gradle packaging plugin,
jlink/jpackage, native installer, signing infrastructure or update code was added.
Linux-only metadata describes the intended Nix scope, not completed native qualification.

Before writing the derivation, inspected the pinned nixpkgs
[Gradle documentation](https://github.com/NixOS/nixpkgs/blob/b6c8664de9b6cc07fe5666a29f91884ba81197c4/doc/languages-frameworks/gradle.section.md),
its fetch-deps/update-deps implementation and setup hook. The public manual URL
initially timed out; the exact pinned documentation was available from upstream.

## Version and generic distribution

`VERSION` contains `0.0.0-dev`. Gradle and Nix both read that file; neither assigns
an independent version literal. One LF or CRLF terminator (or none) is accepted;
missing/empty/whitespace-bearing and unsafe filename values fail. Gradle trims
only after validation. `-PreleaseBuild=true` rejects the development sentinel.
The release checklist requires changing VERSION and matching the intended tag;
M4a does not choose a release version.

The generic layout contains:

```text
bin/totipo-desktop
bin/totipo-desktop.bat
lib/totipo-desktop-<VERSION>.jar
lib/totipo-storage-nio.jar
lib/totipo-core.jar
lib/bcprov-jdk18on-1.86.jar
LICENSE
THIRD_PARTY.md
VERSION
licenses/BOUNCY_CASTLE_LICENSE.html
licenses/TOTIPO_JAVA_LICENSE
```

No runtime is bundled. Java 17 or newer is required; production compilation
remains `--release 17`. All Totipo production classes in all three packaged JARs
are inspected for classfile major 61 and no preview bytecode. Bouncy Castle is an
upstream multi-release library and is not incorrectly required to have uniform
Java 17 classfiles in its versioned entries.

`verifyDistribution` verifies the launcher/executable bit, exact layout and
four-component runtime filename allowlist. Installed JAR hashes must match the
resolved build artifacts. It rejects extra JARs/files, missing licenses, source
entries and cache/vendor entries. Unix and Windows classpaths must contain only
the exact packaged JARs, relative to APP_HOME. `verifyDistributionArchives`
compares every archive file path and SHA-256 to installDist and rejects duplicate
file paths. Both tasks work with configuration-cache storage and reuse.

Both generated scripts have `-XX:+DisableAttachMechanism`; ordinary `run` receives
no new JVM argument. This reduces JVM Attach API availability, without claiming
protection from same-user inspection or memory attacks. Both scripts were read:
no embedded build-host path or credential was found; JAVA_OPTS/JAVA_HOME remain
runtime variables, not captured values from the build environment.

Generic installDist and both extracted archives were invoked outside the checkout
with `env -i`, only the selected JAVA_HOME and ordinary shell utilities on PATH.
Extraction directories included spaces. `-XX:+PrintFlagsFinal -version` confirmed
`DisableAttachMechanism=true`; actual entry-point loading reached the expected
Swing HeadlessException. This is a runtime-loading sanity check, **not a successful
GUI launch**, native clipboard test or qualification of vault chooser behavior.
No Java 17 runtime was present; it was not tested.

## Nix architecture and exact toolchain

The root flake continues using nixpkgs
`b6c8664de9b6cc07fe5666a29f91884ba81197c4`. All development and jailed-agent inputs
are preserved. `packages.default` is exposed for Linux systems only, with
`meta.mainProgram = "totipo-desktop"`; no extra app layer is needed.

The pinned
[Gradle definition](https://github.com/NixOS/nixpkgs/blob/b6c8664de9b6cc07fe5666a29f91884ba81197c4/pkgs/development/tools/build-managers/gradle/default.nix)
selects **9.7.1** for `gradle_9`. It is overridden with the same full `jdk25`.
The pinned
[JDK source](https://github.com/NixOS/nixpkgs/blob/b6c8664de9b6cc07fe5666a29f91884ba81197c4/pkgs/development/compilers/openjdk/25/source.json)
is **25.0.4.1+1**, matching the installed Nix-provided runtime used here. It is not
a headless runtime or a jlink image. Java desktop/AWT/Swing modules remain available.

The wrapper remains authoritative for developers/CI at **9.8.0**. The installed
Nix-provided Gradle 9.7.1 was tested with the proposed strict-locking init script,
strict dependency verification, forced recompilation, desktop checks, distribution
verification and the included-build checks: **642 tests passed** (181 desktop,
346 core, 115 storage-nio; no failures, errors or skips). This supports accepting
the minor tool-version difference for this build. It does not validate proxy or
sandbox integration. Using the repository wrapper inside Nix would require an
additional reproducibly fetched distribution and integration with native-library
patching/the official setup hook; it was unnecessary given the tested nixpkgs
Gradle 9 route. No incompatible version was silently substituted.

`package.nix` uses stdenv.mkDerivation, gradle.fetchDeps, makeWrapper,
makeDesktopItem and copyDesktopItems. The build constructs installDist and checks
both archives. The check phase runs desktop and included-build checks. A tiny
Gradle init script imposes strict locking across the composite without editing
vendor. Hash verification remains strict. Configuration caching is disabled for
Nix's transient MITM build environment; dependency verification/locking, lint,
Werror and bytecode checks are not disabled.

The source filter includes desktop source, Gradle configuration/locks/verification,
VERSION, notices, and vendor build/core/storage sources including test corpus.
It excludes .git, .gradle, build, IDE/review and temporary files. It traverses the
checked-out vendor directories rather than a Git-fileset gitlink. Modern Nix's
[self.submodules facility](https://nix.dev/manual/nix/2.35/command-ref/new-cli/nix3-flake.html#self-attributes)
is enabled; `includeBuild("vendor/totipo-java")` is unchanged. No source is fetched
from GitHub by the derivation or replaced with Maven Totipo artifacts. Pre-build
checks require actual vendor production sources and corpus, catching an omitted
submodule. The completion pass checked the filter statically; Nix execution is
operator-reported evidence, not an agent rerun.

Intended installation (operator reports successful build; agent inspected configuration):

```text
$out/bin/totipo-desktop                          # wrapper
$out/lib/totipo-desktop/bin/totipo-desktop-unwrapped
$out/lib/totipo-desktop/lib/<four runtime JARs>
$out/share/doc/totipo-desktop/{LICENSE,VERSION,THIRD_PARTY.md,licenses/}
$out/share/applications/totipo-desktop.desktop
```

The wrapper sets JAVA_HOME to the pinned full JDK and supplies coreutils/findutils/
sed for the generated launcher. It needs no Gradle, checkout, vendor source or
shell Java configuration. The Windows script is omitted from the Linux Nix output.
An install-check phase checks wrapper/JDK reference, attach flag, copied JAR
identity/count, desktop entry, licenses and absence of source/test/cache files;
the operator reports successful package execution after the corrected assertion.
Expected desktop fields are:

```ini
Name=Totipo
Exec=totipo-desktop
Categories=Utility;
Terminal=false
```

The single Utility category may omit the trailing semicolon, as produced by
makeDesktopItem; both spellings are accepted by the install check.
No MIME handlers, autostart, tray behavior or icon is added. Metadata identifies
the project homepage, Apache-2.0 desktop license, Linux scope and Maven binary
bytecode provenance. The JDK remains a managed closure dependency with its own
legal notices.

## Official dependency-cache workflow (operator completed)

Reference workflow below; not executed by the agent in this completion pass:

```sh
update_script=$(nix build --no-link --print-out-paths .#packages.x86_64-linux.default.mitmCache.updateScript)
"$update_script"
nix flake check
nix build .
nix build .
nix build --rebuild .
```

For this unstaged review use `path:.` instead of `.` so new files are included;
Git flake inputs omit untracked files. This does not authorize committing or
staging. Once packaging inputs are tracked, ordinary `nix build .` is the intended
interface. Choose the actual system attribute for other Linux architectures.

The official update script records actual downloads/hashes. Our update task runs
the package's build/check tasks, including the composite tests, rather than
blindly resolving unused configurations. No hashes were hand-edited. The current
JSON is generated evidence, not the former UNGENERATED bootstrap marker: format
version 1, one Maven Central repository, 18 dependency coordinates and 43 valid
SHA-256 SRI entries for POM/JAR/module data. Test-only downloads in this build
cache do not become shipped runtime dependencies. File SHA-256:
`544ea4837239d9fcd44f719227624b951dd0c265917de733f53724a5d36a84df`.

Operator Nix execution: successful, as explicitly supplied for this completion.
Agent Nix executions this pass: zero, by instruction. Exact command transcript,
Nix version and forced-rebuild comparison were not supplied; no counts or hashes
are inferred from that statement. Nix reproducibility remains UNQUALIFIED; a
cached repeat alone would not establish it. This is a future candidate evidence
gate, not a claim that the completed package workflow failed.

## Filesystem harness and qualification evidence

`src/qualification` is an isolated source set using the already locked production
classpath and annotation-processor configuration; it adds no dependency or JUnit
requirement. `filesystemQualification -PqualificationRoot=...` is never wired into
build/check/test or CI. There is no default root. Missing, blank, filesystem-root,
home-directory and nonexistent roots were rejected.

The harness creates a private uniquely named `totipo-qualification-` child of the
operator-selected root. Public NioTotipo operations cover create/close/reopen,
active token creation/reopen, ordinary update, two historical-base updates,
semantic conflict, merge/reopen, successful password change, old-password
rejection and new-password reopen. It checks stable root fingerprint, token state
and fixed-instant TOTP where expected. All sessions are closed; synthetic input
arrays are wiped. Observation waits are bounded and subscriptions cancelled.
Cleanup is in finally, without following symlinks; cleanup failure prints the
exact test directory. The success message follows cleanup, not just the workflow.

Actual qualification environment, 2026-10-01:

- Linux kernel **6.18.53**, **amd64**, isolated development environment; distribution
  not exposed (`/etc/os-release` absent).
- Nix OpenJDK **25.0.4.1+1**; `java.vendor` reports **N/A** (recorded literally).
- **ext4**, POSIX attributes supported.
- PASS at **14:57:54Z**, repeated at **15:04:17Z** for cleanup-scope validation.
  The second run retained an existing parent sentinel and removed only its child.
  Both caller-created empty parent test roots were removed afterward.
- Root paths, passwords, secrets and TOTP values are intentionally absent from
  persisted qualification evidence. No real vault was opened.

This proves only that exact workflow/environment, not all Linux/ext4, crash or
power-loss durability, synchronization, FUSE, NFS/SMB, network shares or cloud-sync
paths. Those remain UNQUALIFIED. GUI, clipboard, accessibility, screen readers,
desktop entry and interactive packaged path behavior remain UNQUALIFIED: no
DISPLAY/WAYLAND_DISPLAY is exposed. No Xvfb, icon or platform workaround was added.
QUALIFICATION.md distinguishes the filesystem-only PASS from NOT QUALIFIED release status.

## Reproducibility and notices

Ran two separate `./gradlew clean :totipo-java:clean` operations, each followed
by `./gradlew --no-build-cache installDist distTar distZip verifyDistributionArchives`.
Sorted installed file names/hashes (including every JAR and launcher) matched.
The archives were byte-identical with Gradle 9.8.0 and JDK 25 in this environment.
Archive tasks explicitly disable timestamp preservation and use reproducible order;
ZIP listings show normalized 1980 timestamps. No opaque archive post-processing.
A final build after validation changes reproduced the same hashes:

```text
b1fd71559068f50bd2e63c294ebe756998cf360e1bd4e6e85c1d21deb40134ee  totipo-desktop-0.0.0-dev.tar
f2fdb7f67f3f6bb39aaed083737d24db7e327098bb87b1f0c7787552fa673184  totipo-desktop-0.0.0-dev.zip
```

These are local development artifacts, not published release checksums or a
cross-environment reproducible-build guarantee. The checksum helper requires
explicit regular files, rejects absent arguments/files, and only calls sha256sum.

THIRD_PARTY.md inventories desktop (Apache-2.0), pinned Totipo Java core/storage
(Apache-2.0), Bouncy Castle 1.86 (MIT), and selected Nix OpenJDK (GPL-2.0 with
Classpath exception and its runtime legal directory). The vendor license copy
matches byte-for-byte. Bouncy Castle's upstream r1rv86 LICENSE.html was downloaded
unchanged and is packaged alongside the Apache license; its SHA-256 is
`afaf5bd0b0d869dc86b71711e1b775b204948634c9710a35a9a70d1613a2ab14`.
Test dependencies are excluded from the runtime inventory.

## Commands and results

| Command/check | Result |
| --- | --- |
| `git submodule status`; gitlink/vendor HEAD/status comparison | PASS; pin unchanged, vendor clean |
| `./gradlew tasks --group distribution` | PASS; existing application tasks confirmed |
| `./gradlew clean test build` (also final invocation with verifyDistributionArchives) | PASS |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; forced online compilation/tests |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; forced offline compilation/tests |
| `./gradlew installDist distTar distZip` | PASS |
| `./gradlew verifyDistribution` | PASS; all four runtime JARs, notices, scripts and Totipo classfiles |
| `./gradlew verifyDistributionArchives` | PASS; ZIP/TAR file contents match installDist |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | PASS; resolved unchanged tree, no FAILED entries |
| Gradle 9.7.1 `--no-daemon --no-build-cache --rerun-tasks --no-configuration-cache --dependency-verification=strict --init-script packaging/strict-locking.gradle build verifyDistributionArchives :totipo-java:check` | PASS; all 642 tests |
| Final Gradle 9.7.1 distribution verification with strict init script | PASS |
| Repeated final wrapper distribution tasks | PASS; configuration cache reused |
| Explicit filesystemQualification, twice | PASS on exact ext4 environment above; cleanup verified |
| Five missing/unsafe/nonexistent-root cases | PASS: correctly rejected |
| Eight invalid/missing/development-release VERSION cases | PASS: correctly rejected; no terminator/LF/CRLF fixtures accepted; VERSION restored |
| Injected extra JUnit JAR, removed attach flag, injected host path, removed executable bit | PASS: verifier rejected all four; restored distribution passed |
| `tar -tf` / `unzip -l` and Unix/Windows script review | PASS; expected runtime/notice inventory, no test/source/cache tree or host secrets |
| Isolated installDist and extracted archive launch probes, JDK 25 | Runtime/attach sanity only; expected HeadlessException; GUI UNQUALIFIED |
| Two clean generic builds: SHA-256/layout comparisons | PASS; identical, final artifact hashes unchanged |
| Checksum helper missing argument/nonexistent file | PASS: correctly rejected |
| `git diff --check` | Completion pass: three existing trailing spaces in verbatim Bouncy Castle license, lines 8–10; preserved. All other files pass. |
| Intended Nix package/cache workflow | Operator reports successful execution; agent did not rerun by instruction. Exact forced-rebuild result not recorded. |
| Native packaged launch/checklists | No specific native outcome/environment recorded in supplied notes; remains UNQUALIFIED. |
| Java 17 execution | NOT RUN: no suitable runtime installed |

Initial implementation runs exposed Kotlin import/configuration-cache captures and
an unused qualification annotation-processor lock configuration. These were fixed
without disabling the normal configuration cache or weakening locks; final runs
passed. The checksum helper now uses portable /bin/sh because this isolated
environment lacks /usr/bin/env. A Fontconfig warning occurred during headless tests;
all tests passed, and it is not treated as native GUI qualification.

## Files and source audit

Changed: build.gradle.kts (version/distribution/harness tasks), flake.nix (package
output/submodules), CI (archive verification), README (packaging/status/workflow),
ARCHITECTURE (packaging/security boundaries).

Added: VERSION, package.nix, package-deps.json, packaging/strict-locking.gradle,
packaging/licenses/{BOUNCY_CASTLE_LICENSE.html,TOTIPO_JAVA_LICENSE},
scripts/sha256-release-artifacts.sh,
src/qualification/java/dev/totipo/qualification/FilesystemQualification.java,
QUALIFICATION.md, THIRD_PARTY.md, RELEASE_CHECKLIST.md,
qualification/{GUI_SMOKE,CLIPBOARD_SMOKE,ACCESSIBILITY_SMOKE,PACKAGED_LAUNCH}.md,
and this report.

Audit confirms no production Java or existing tests changed; no protocol,
observation, token, password, clipboard or provider semantics changed. No new
production Java dependency, packaging plugin, updater/network application code,
default vault directory, migration, watcher or icon. The generic distribution
contains precisely its intended JARs, with no embedded Java runtime. Nix declares
the existing vendor composite, strict checks and full pinned JDK; successful
execution is operator-reported. VERSION remains the single source, attach hardening is only
in generated launchers, and no host path was found in them. The release checklist
cannot pass the development sentinel or missing native qualification gates.

The explicit native checklists cover GUI/merge/AdditionalConflict/password/focus/
shutdown behavior, M3c clipboard cases and history limitations, modest keyboard/
accessibility checks, actual package launching and user-selected vault paths.
They require actual evidence and do not infer support from test or build results.
Updates remain manual. No platform support has been claimed without evidence.

Remaining qualification limits: no exact Nix forced-rebuild comparison recorded;
no Java 17 runtime test; no native GUI/clipboard/accessibility qualification. Nix Gradle's 9.7.1 versus wrapper 9.8.0
is explicitly investigated/tested, not hidden. An intentional icon remains future
release polish. M4b must choose the candidate version and complete every native
matrix gate for the claimed scope before release is considered.

## Follow-up: dependency-update build locale

The operator reported InvalidPathException in NioSpiTest at lines 47 and 163
during the dependency update. Both lines construct Unicode filenames. Reproduced
the exact two failures with the pinned Gradle 9.7.1/JDK 25 toolchain under
`LC_ALL=C LANG=C`: 57 NioSpiTest cases ran, two failed. The JVM reported
`file.encoding=UTF-8` but `native.encoding` and `sun.jnu.encoding` were
`ANSI_X3.4-1968`. UTF-8 Java text encoding alone does not fix native path encoding.

Added `LC_ALL = "C.UTF-8"` to the derivation, covering both the official update
workflow and normal package checks, without changing the runtime wrapper locale.
With `LC_ALL=C.UTF-8 LANG=C`, all 115 storage-nio tests passed (zero failures,
errors or skips) using `gradle --no-daemon --no-build-cache --rerun-tasks
--no-configuration-cache --dependency-verification=strict --init-script
packaging/strict-locking.gradle :totipo-java:storage-nio:test`.
The vendor pin, sources, tests, strict verification and locks remain unchanged.
The operator's addition of SPEC_PIN.md to the source filter is preserved.

The operator subsequently completed the intended dependency-cache/package workflow
successfully. No cache regeneration was performed in this completion pass. The
three trailing spaces in the verbatim upstream Bouncy Castle license remain
preserved; they are reported as a whitespace-check exception, not hidden.

## Sandbox finding: missing SPEC_PIN

The initial filtered Nix source omitted `vendor/totipo-java/SPEC_PIN.md`.
`SpecSnapshotIntegrityTest.snapshotMatchesChecksumsAndIndependentPins()` failed
with `NoSuchFileException`. The operator corrected the filter to include it.
This demonstrates that the included upstream integrity test usefully validates
packaged source completeness. It was a packaging omission, not a protocol/core
defect; upstream tests and the Totipo Java pin remain unchanged.

The subsequent UTF-8 filename failure was likewise a packaging/sandbox-environment
issue, not a protocol/core defect. The affected methods were
`NioSpiTest.exactNamesNeverAcceptCaseOrNormalizationAliases()` and
`NioSpiTest.scanExposesAllExactDirectNamesKindsAndLogicalLengths()`.
The final derivation sets `LC_ALL = "C.UTF-8"` for fetching and package tests,
with `JAVA_HOME = jdk` and full `jdk25` selected in the flake. It retains the
process/filesystem locale fix, rather than substituting `-Dfile.encoding=UTF-8`.

## Follow-up: desktop category installation check

The operator's subsequent `nix build path:.` log shows distribution/archive
verification and all desktop/core/storage checks passed, followed by successful
installation and fixup. Installation checking reached `Exec=totipo-desktop` and
then failed. The pinned nixpkgs
[makeDesktopItem implementation](https://github.com/NixOS/nixpkgs/blob/b6c8664de9b6cc07fe5666a29f91884ba81197c4/pkgs/build-support/make-desktopitem/default.nix)
renders lists using semicolon joining, producing `Categories=Utility` with no
trailing separator. Our literal `Categories=Utility;` check was too strict.

Changed the assertion to `grep -Ex 'Categories=Utility;?'`. Local regression
checks accept both single-Utility forms and reject an extra Network category,
Network alone, UtilityExtra and an empty value. No desktop entry or application
behavior changed, no dependency hashes changed, and the generated cache is
preserved. A new dependency update is not needed for this assertion-only fix.
The Fontconfig messages were nonfatal warnings in the supplied successful check
phase; they are not treated as native GUI qualification. The operator now reports
successful Nix execution after this fix; the earlier pending-rerun status is
superseded. No exact native GUI result was supplied in this completion request.

## Completion-pass independent validation (2026-10-01)

Before editing, inspected the existing uncommitted diff, package.nix, flake.nix,
generated cache, source filter and this report. This pass changes only this
report, README.md and QUALIFICATION.md status prose. Operator package/cache/locale
fixes, licenses, version, vendor and build inputs are preserved. All changes
remain uncommitted; no stage/reset/clean/submodule update or release action ran.

Independently reran successfully:

- `git submodule status`: exact pin unchanged; vendor worktree clean.
- `./gradlew clean test build`.
- `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test`.
- `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test`.
- `./gradlew installDist distTar distZip verifyDistribution verifyDistributionArchives`.
- `./gradlew --offline dependencies --configuration runtimeClasspath`: only
  storage-nio → core → Bouncy Castle 1.86, with the strict 1.86 constraint.
- `./gradlew validateVersion -PreleaseBuild=true`: expected rejection of the
  development sentinel (negative check passed); VERSION remains `0.0.0-dev`.

Additional composite check: `LC_ALL=C.UTF-8 ./gradlew --no-daemon
--no-build-cache --rerun-tasks --no-configuration-cache
--dependency-verification=strict --init-script packaging/strict-locking.gradle
:totipo-java:check` passed with Gradle 9.8.0. Completion XML results total 642
passing tests: 181 desktop, 346 core and 115 storage-nio; no failures/errors/skips.
An initial additional offline composite attempt failed before tests because this
session's Gradle cache lacked Jackson 2.18.2. The online strict run resolved it
without changing locks or verification metadata. This local cache miss is
separate from the generated Nix dependency cache and does not contradict the
operator's Nix success. The requested desktop offline run passed independently.

`git diff --check` reports only the three already documented trailing spaces in
`packaging/licenses/BOUNCY_CASTLE_LICENSE.html`, lines 8–10. The exact upstream
notice/hash is preserved. The check excluding that one license passes. This is
an explicit cosmetic exception, not a product/packaging deficiency.

Static inspection and a filesystem inventory applying the filter's selection and
exclusion rules cover all 273 required tracked vendor files: SPEC_PIN, root and
module Gradle/settings files, version catalog, wrapper/verification/lock inputs,
core and storage-nio production/tests/resources/test fixtures, and all 98 snapshot
files (97 checksummed payload files including 90 vectors, plus SNAPSHOT.sha256).
The storage tests' cross-module snapshot path is included. Ancestor directories
are traversable under the filter; symlinks, .git, .gradle, build outputs, IDE,
temporary and review material remain excluded. This is static filter validation,
not a claim of agent-side Nix evaluation.

Package configuration still runs `installDist verifyDistributionArchives` and
`check :totipo-java:check`; the update task covers both. Strict dependency
verification and the composite strict-locking init script remain configured.
Desktop/vendor compilation retains `-Xlint:all`, `-Werror`, release 17 and bytecode
checks. No locks, verification metadata, dependencies or upstream tests changed.

Generic archive inventories and both launchers were inspected again. The runtime
set is exactly the four expected JARs; both scripts preserve the attach-disable
flag and relative application classpath. Production bytecode inspection found
61 desktop, 176 core and 26 storage-nio classes, all major 61/minor 0. Full JDK 25
wrapping, unwrapped Gradle launcher, licenses, desktop entry and installation
checks remain as configured above. Development `run` remains unaffected.

The freshly generated ZIP/TAR hashes equal the historical hashes recorded above.
No generic distribution input changed in this completion pass, so the existing
two-clean-build comparison was preserved without repeating it. Filesystem harness
inputs also did not change; the exact ext4 qualification was preserved, not rerun.

Broad Linux support, other ext4 environments, non-ext4 filesystems, NFS, SMB,
FUSE, cloud-sync paths, Java 17 runtime execution, untested native GUI/clipboard/
accessibility/screen-reader environments, Windows and macOS remain UNQUALIFIED.
No native result is inferred from successful Nix packaging. No jlink/jpackage,
packaging plugin, updater/network code, feature/architecture change or release
action was introduced. M4b must record candidate-specific installed-output,
rebuild and native qualification evidence before any release claim.

## Original M4a review snapshot

The following output includes this report. `git diff --stat` excludes untracked
new files; the added-file inventory above and `git status` identify them.
Nothing is staged or committed.

### git status --short

```text
 M .github/workflows/ci.yml
 M ARCHITECTURE.md
 M README.md
 M build.gradle.kts
 M flake.nix
?? QUALIFICATION.md
?? RELEASE_CHECKLIST.md
?? THIRD_PARTY.md
?? VERSION
?? package-deps.json
?? package.nix
?? packaging/
?? qualification/
?? review/M4A_RELEASE_HARDENING_REPORT.md
?? scripts/
?? src/qualification/
```

### git diff --stat

```text
 .github/workflows/ci.yml |   2 +-
 ARCHITECTURE.md          |  23 +++++++
 README.md                | 106 +++++++++++++++++++++++++++++++-
 build.gradle.kts         | 155 +++++++++++++++++++++++++++++++++++++++++++++++
 flake.nix                |   9 +++
 5 files changed, 293 insertions(+), 2 deletions(-)
```

Ready for M4b release-candidate qualification
