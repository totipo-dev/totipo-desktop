# D1 Maven dependency migration

**Gradle/Maven migration complete.**
**Nix dependency cache regenerated; package build, forced rebuild and flake check validated by the operator.**

D1 establishes the released binary boundary and changes package identity only.
Desktop VERSION remains `0.0.0-dev`; the executable remains `totipo-desktop`.
No application logic, UI, lifecycle, token, password, clipboard, search, TOTP,
merge, diagnostics or vault-data behavior was changed. All changes are
uncommitted for review. Nothing was published, tagged or released.

## Baseline and scope

- Desktop baseline HEAD: `a4eee6d8a735e10db54bbf57574d3aefd9aa8fcb`.
- Previous Java gitlink: `3d97dca72604f39b6b475c0d5a0a8b076632cc42`.
- VERSION before/after: `0.0.0-dev`.
- Working tree was clean before baseline initialization on the resumed pass.
- The initial agent pass found the tracked submodule uninitialized. Its baseline
  attempt could not resolve the old source dependency. This was a checkout
  prerequisite, not a product/build defect. After clarification,
  `git submodule update --init vendor/totipo-java` initialized the exact old
  gitlink solely for baseline reproduction. The old URL resolved, so no URL
  override was needed. `git submodule status` and the submodule's `rev-parse HEAD`
  both confirmed the exact commit above; it was not advanced to the release tag.
- `./gradlew clean test build` then passed before source/build edits:
  **181 tests, 0 failures, 0 errors, 0 skips**. Java 17 classfile verification
  passed (major 61, no preview); distribution verification passed.
- Baseline runtime JARs: `totipo-desktop-0.0.0-dev.jar`,
  `totipo-storage-nio.jar`, `totipo-core.jar`, `bcprov-jdk18on-1.86.jar`.
- The initialized Java checkout was subsequently removed by D1. No Java/spec
  source was edited and no replacement checkout, subtree or VCS dependency was
  added. Historical M0/M1/M2/M3/M4a reports remain unchanged.

Environment: x86_64 Linux, existing full OpenJDK 25.0.4.1+1 and Gradle wrapper
9.8.0. Wrapper, JDK configuration, flake lock and desktop VERSION are unchanged.
No Nix CLI command was executed by the agent, including indirectly through
bootstrap. Operator Nix evidence is recorded separately below.

## Released dependency and identity

The single direct Totipo application dependency is
`org.totipo:totipo-storage-nio:0.1.0`. It exposes
`org.totipo:totipo-core:0.1.0` transitively, which brings BC 1.86 at runtime.
Keeping core transitive exercises storage-nio's published application API contract.
`totipoJavaVersion` in `build.gradle.kts` is the single dependency-version
configuration location; it contains the exact value `0.1.0`.

Upstream is https://github.com/totipo-org/totipo-java, source tag `v0.1.0`,
implementing protocol v1/r17. `TOTIPO_JAVA_DEPENDENCY.md` replaces
`TOTIPO_JAVA_PIN.md`: Maven Central, strict locks and verification are the desktop
consumer boundary. Java source/spec snapshots, SPEC_PIN, corpus and source-commit
validation belong to Java release provenance, not the desktop package graph.
Artifact hashes stay in machine-enforced verification metadata rather than
being duplicated in the provenance document.

Desktop packages moved with `git mv` from `dev.totipo.desktop` to
`org.totipo.desktop`; tests moved consistently. The qualification package moved
from `dev.totipo.qualification` to `org.totipo.qualification`. Released API imports
now use `org.totipo`, with `org.totipo.storage.nio.NioTotipo` as the filesystem
entry point. Application main class is `org.totipo.desktop.TotipoDesktop` and
Gradle group is `org.totipo`. Class simple names and architecture are unchanged.
No storage SPI/internal API replaced a public application API.

Current Totipo GitHub links use `totipo-org`, including desktop and Java URLs.
The upstream specification repository is https://github.com/totipo-org/totipo-spec;
no desktop spec checkout is introduced. README now documents a normal clone and
first-resolution internet/cache requirements. ARCHITECTURE, runtime provenance,
qualification documentation and release gates describe the released boundary.
The packaged Totipo Java Apache-2.0 license notice is unchanged; BC's notice and
runtime version are unchanged.

## Mechanical source and class/API evidence

Temporary evidence is under `/tmp/totipo-d1`, not committed. Before migration,
all desktop sources were copied there and all production class paths recorded.
`javap -protected -constants` was run on every production class, including nested
and anonymous classes, capturing class declarations and public/protected
constructors, methods and fields.

Every Java file compares byte-for-byte equal after only replacing `dev.totipo`
with `org.totipo` in baseline text and moving its namespace directory:

| Inventory | Baseline | After | Result |
| --- | ---: | ---: | --- |
| Production Java source files | 37 | 37 | Exact normalized match |
| Test Java source files (including helpers) | 31 | 31 | Exact normalized match |
| Qualification Java files | 1 | 1 | Exact normalized match |
| Production class files | 61 | 61 | Exact normalized path match |
| Public/protected API inventory | All 61 classes | All 61 classes | Exact normalized match |
| Desktop tests | 181 | 181 | 0 failures/errors/skips |

There are zero production-source exceptions and no deleted or weakened tests.
Inventory files: `baseline-classes.txt`, `post-classes.txt`, `baseline-api.txt`,
`post-api.txt`, and `source-equivalence.txt` under the temporary directory.
Class paths normalize `dev/totipo` to `org/totipo`; javap output normalizes the
dotted namespace. Both comparisons have empty diffs.

Service/reflection/serialization inspection found no META-INF/services,
ServiceLoader, Class.forName or native object-stream persistence. Existing
serialVersionUID declarations belong to Swing subclasses and an exception (plus
a test helper); they are unchanged. Launcher and qualification main-class strings
were updated. Durable vault data is handled through Totipo's application API and
does not depend on desktop Java FQCNs. No compatibility shim is needed or added.

## Dependency graph and regression guard

Baseline compile graph:

```text
desktop
└── dev.totipo:storage-nio -> project :totipo-java:storage-nio
    └── project :totipo-java:core
```

Baseline runtime adds `org.bouncycastle:bcprov-jdk18on:1.86` beneath core.

Post-D1 compile graph (excluding Gradle's repeated strict-lock constraint rows):

```text
desktop
└── org.totipo:totipo-storage-nio:0.1.0
    └── org.totipo:totipo-core:0.1.0
```

Post-D1 runtime graph (excluding repeated strict-lock constraint rows):

```text
desktop
└── org.totipo:totipo-storage-nio:0.1.0
    └── org.totipo:totipo-core:0.1.0
        └── org.bouncycastle:bcprov-jdk18on:1.86
```

Exact Gradle graph output, including constraint rows, is recorded below.
There is no source-project substitution. Compile has no BC dependency; runtime
does. No new test dependency was introduced; JUnit 6.1.3, apiguardian 1.1.2,
jspecify 1.0.0 and opentest4j 1.3.0 remain unchanged in their prior configurations.

`verifyMavenBoundary`, wired into `check`, resolves compile/runtime artifacts and
walks both component graphs. It requires the two exact external
ModuleComponentIdentifiers, rejects dependency ProjectComponentIdentifiers and
the obsolete Maven group, and rejects extra or version-skewed Totipo modules.
It verifies storage-nio is the only direct Totipo dependency, core is a real
transitive dependency of storage-nio rather than merely a lock constraint, and
BC 1.86 is present at runtime. No source path is involved. Qualification classes
now compile through `check`; the filesystem harness itself is never run implicitly.

`.gitmodules`, the tracked Java gitlink/directory and `includeBuild` were removed.
`git submodule status` is empty and `git ls-files --stage` contains no gitlinks.
Settings retain `FAIL_ON_PROJECT_REPOS` and only `mavenCentral()`. Audits find no
mavenLocal, included build, source substitution, sibling/vendor checkout, file
repository or optional local-development override in current build configuration.
CI no longer requests recursive checkout and runs the guard through its existing
`build`/`check` path; credential-free checkout and build are retained.

## Locks and strict verification

Normal lock-writing workflow used:

```sh
./gradlew dependencies --write-locks --write-verification-metadata sha256
./gradlew build --write-verification-metadata sha256
```

Reviewed lock delta: add core/storage-nio 0.1.0 to compile/runtime and test
compile/runtime classpaths; compileClasspath is no longer empty. Gradle also
records the three empty qualification configurations (the harness explicitly
uses the main classpaths). Existing BC and all other dependency versions and
configurations remain unchanged. Settings lockfile is unchanged.

Reviewed verification delta: exactly two components and four SHA-256 entries,
the `.jar` and `.module` for each released Totipo module. These are the metadata
and artifacts actually resolved by this Gradle workflow; no speculative POM
entries were added. Existing entries, including BC hashes, are unchanged.
`org.gradle.dependency.verification=strict` remains active with strict locking;
no trust exceptions, wildcard allowances or disabled verification were added.
Bootstrap now preserves locks/metadata by default and regenerates them only with
explicit `--refresh-dependencies`. Wrapper generation/checks and normal build
verification are retained; automatic Nix flake updates were removed.

## Distribution

The verified installDist and both archives contain exactly:

```text
lib/totipo-desktop-0.0.0-dev.jar
lib/totipo-storage-nio-0.1.0.jar
lib/totipo-core-0.1.0.jar
lib/bcprov-jdk18on-1.86.jar
bin/totipo-desktop
bin/totipo-desktop.bat
LICENSE
THIRD_PARTY.md
VERSION
licenses/TOTIPO_JAVA_LICENSE
licenses/BOUNCY_CASTLE_LICENSE.html
```

The allowlist now requires released versioned Totipo filenames and rejects stale
unversioned JARs as well as extra runtime/test/fixture JARs. Existing per-artifact
SHA-256 comparison proves installed JAR bytes equal the resolved Gradle artifacts;
no independent downloads occur. ZIP/TAR contents are compared byte-for-byte with
verified installDist. Source, vendor and cache entries remain rejected. Totipo
runtime classfiles remain Java 17. Both generated launchers have the new main
class, unchanged command name, exact classpath and DisableAttachMechanism flag.

## Nix cache regeneration and operator validation

**Nix dependency cache regeneration complete; operator package validation PASS.**

The flake no longer requests self submodules. Package source filtering now selects
only desktop sources/build inputs/licenses. Java source, Gradle metadata, tests,
SPEC_PIN and conformance-corpus inputs/checks are removed. The obsolete
`packaging/strict-locking.gradle` helper only applied the same strict-locking
policy across the old composite; it and its init-script references are removed.
Desktop's native strict locking remains enabled. Build/check/update tasks now
run desktop checks and distribution verification only, with no included Java
tests. Runtime install checks require the exact four versioned JARs, preserve
byte comparisons, launcher/JDK/attach checks, licenses and source/test/cache
exclusions.

The operator ran the documented `mitmCache.updateScript` successfully and
regenerated `package-deps.json`. Inspection of the current file confirms released
`org.totipo:totipo-core:0.1.0` and `org.totipo:totipo-storage-nio:0.1.0` artifacts
under the top-level bucket `https://repo.maven.apache.org/maven2/org`, with inner
keys `totipo#totipo-core/0.1.0` and `totipo#totipo-storage-nio/0.1.0`. Each entry
contains `jar`, `module` and `pom` SHA-256 hashes. BC 1.86 remains present;
Java conformance-only Jackson and JUnit 5.10.2 cache entries are gone. No hashes
were hand-edited and no Java source checkout or GitHub Java-source download was
introduced. The cache is no longer stale.

Operator validation exposed an error in the initial hand-written freshness
guard: it expected the wrong generated key layout. The regenerated cache was
correct; this was not a dependency-cache failure. The already-applied correction
in `package.nix` matches `gradle.fetchDeps` output:

```nix
centralCache = cacheData."https://repo.maven.apache.org/maven2/org" or { };
key = "totipo#${builtins.elemAt coordinate 1}/${builtins.elemAt coordinate 2}";
```

For `org.totipo:totipo-core:0.1.0`, the inner key is
`totipo#totipo-core/0.1.0`, without an additional `org/` prefix. The fail-closed
freshness check remains enabled, requiring JAR and module/POM entries for both
locked coordinates; its bypass remains limited to the official update workflow.
This report-only finalization did not change application, build or package logic.

| Operator validation | Result and evidence ownership |
| --- | --- |
| Documented `mitmCache.updateScript` | PASS, operator-reported; regenerated entries independently inspected |
| `nix build path:.` | PASS, operator-reported; current cache and corrected guard reflect the validated configuration |
| `nix build --rebuild path:.` | PASS, operator-reported; not rerun by the agent |
| `nix flake check path:.` | PASS, explicitly confirmed by the operator; not rerun by the agent |
| Installed runtime JAR inventory | PASS as part of operator-reported package validation; exact four-JAR checks remain in `package.nix` |

The installed runtime inventory is exactly:

```text
totipo-desktop-0.0.0-dev.jar
totipo-storage-nio-0.1.0.jar
totipo-core-0.1.0.jar
bcprov-jdk18on-1.86.jar
```

The current `result` symlink points to
`/nix/store/0mp0cbvvqhkx1ldd4q5vvw05p13cy9xq-totipo-desktop-0.0.0-dev`.
Its target is unavailable in the agent environment, so installed contents could
not be independently re-inspected here. Inventory PASS is attributed to the
operator's package validation and its required install checks, not a new agent
inspection. No Nix CLI command was run during finalization.

Documented fish commands retained for reproducibility on x86_64-linux;
execution results are attributed to the operator in the table above:

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

`path:.` is necessary while migration files are uncommitted/untracked, so the
source snapshot includes the complete review state. No Nix evaluation, cache
update, build, flake check or forced rebuild was performed by the agent. The
migrated package build, forced rebuild and flake check are now validated by the operator;
this D1 evidence supersedes the earlier pending-cache status. Earlier M4a evidence
still applies only to the earlier source boundary. Native qualification remains
separate from package validation.

## Qualification and remaining scope

The previously recorded filesystem qualification was against the old
source-consumed Java implementation. Released Java 0.1.0 is intended to be
semantically equivalent; compilation and tests are not new filesystem
qualification. The harness was compiled, but not executed against an arbitrary
root. Rerunning it against a candidate package belongs to later release
qualification. Native GUI, clipboard, accessibility, packaged launch and Java 17
runtime checks remain unperformed/UNQUALIFIED. Existing release gates are retained.

Pre/post searches covered `dev.totipo`, `dev/totipo`, `totipo-dev`, old GitHub URLs,
the old vendor path, includeBuild and TOTIPO_JAVA_PIN. Before D1 these classified
as Java namespaces/imports, Gradle/main-class wiring, checkout/package/bootstrap
configuration, current provenance/docs, and historical review evidence. All
operational hits migrated or were removed. Remaining old-name hits are historical
review reports and this D1 report's explicit before/after evidence. Historical
reports were not modernized. New-identity audits cover org.totipo source/imports,
org/totipo source paths, released Maven/cache coordinates and totipo-org links.
No namespace was inserted into protocol or user vault data.

## Validation results and final worktree

The command results, full dependency graphs and worktree snapshots below complete
the evidence for this report.

Exact compile dependency output:

```text
compileClasspath - Compile classpath for source set 'main'.
+--- org.totipo:totipo-storage-nio:0.1.0
|    \--- org.totipo:totipo-core:0.1.0
+--- org.totipo:totipo-storage-nio:{strictly 0.1.0} -> 0.1.0 (c)
\--- org.totipo:totipo-core:{strictly 0.1.0} -> 0.1.0 (c)
```

Exact runtime dependency output:

```text
runtimeClasspath - Runtime classpath of source set 'main'.
+--- org.totipo:totipo-storage-nio:0.1.0
|    \--- org.totipo:totipo-core:0.1.0
|         \--- org.bouncycastle:bcprov-jdk18on:1.86
+--- org.totipo:totipo-storage-nio:{strictly 0.1.0} -> 0.1.0 (c)
+--- org.totipo:totipo-core:{strictly 0.1.0} -> 0.1.0 (c)
\--- org.bouncycastle:bcprov-jdk18on:{strictly 1.86} -> 1.86 (c)
```

`(c)` denotes a strict-lock dependency constraint, not a direct application
implementation dependency. Refreshed and ordinary graph outputs agree.

| Command/check | Result |
| --- | --- |
| Baseline `./gradlew clean test build` | PASS; 181 tests; Java 17 and old distribution verified |
| `git submodule status` after removal | PASS; empty output |
| `./gradlew dependencies --write-locks --write-verification-metadata sha256` | PASS; reviewed delta described above |
| `./gradlew build --write-verification-metadata sha256` | PASS; qualification compile, boundary, tests and distribution |
| `./gradlew clean test build` | PASS |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; all 181 tests executed, 0 failures/errors/skips |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; all 181 tests executed, 0 failures/errors/skips |
| `./gradlew --refresh-dependencies clean test build` | PASS; build-cache reuse occurred for compile/test tasks; uncached execution is separately recorded |
| `./gradlew installDist distTar distZip verifyDistribution verifyDistributionArchives` | PASS; exact runtime bytes and archive inventories |
| `./gradlew --refresh-dependencies dependencies --configuration compileClasspath` | PASS; external modules only |
| `./gradlew --refresh-dependencies dependencies --configuration runtimeClasspath` | PASS; external modules and BC 1.86 |
| `./gradlew dependencies --configuration compileClasspath` | PASS; graph above |
| `./gradlew dependencies --configuration runtimeClasspath` | PASS; graph above |
| Mechanical production/test/qualification source comparison | PASS; zero exceptions |
| Normalized javap API and class-path inventories | PASS; 61 classes |
| `bash -n bootstrap-m0.sh` | PASS |
| `git diff --check` and `git diff --cached --check` | PASS |
| Nix commands | Not run by agent; operator cache/build/rebuild/flake-check PASS as detailed above |
| Explicit filesystem/native GUI/clipboard/Java 17 runtime qualification | NOT RUN; no new qualification claim |

Baseline emitted a fontconfig warning, but tests completed successfully. No test
was skipped for it. Log files for the Gradle commands remain under
`/tmp/totipo-d1`; they are temporary evidence, not packaged inputs.

The optional isolated online build also passed:

```sh
tmp_gradle_home="$(mktemp -d /tmp/totipo-d1-gradle.XXXXXX)"
GRADLE_USER_HOME="$tmp_gradle_home" ./gradlew --no-daemon --no-build-cache --no-configuration-cache clean test build verifyDistributionArchives
```

The temporary Gradle home began empty, including wrapper/dependency caches and
init scripts. Gradle downloaded its wrapper distribution, resolved dependencies
with the unchanged Central-only repository settings and strict verification,
and executed all 14 tasks, including all 181 tests (0 failures/errors/skips),
qualification compilation, Java 17 bytecode, Maven boundary and archive checks.
The temporary home was removed afterwards. This proves the build does not require
an initialized Java submodule, sibling checkout or previous composite dependency
cache. Production source/API inventories were compared again after this build
and still matched exactly after namespace normalization.

A negative guard smoke used an init script stored only in `/tmp/totipo-d1` to
add a direct core dependency, then ran `verifyMavenBoundary` with configuration
cache disabled. It failed as intended with “Only storage-nio may be a direct
Totipo dependency.” The normal task was rerun without that script and passed.
No repository source, locks or verification metadata were changed by the smoke.

The initial isolated-build invocation was rejected before execution because its
temporary-directory cleanup used `rm -rf`. It was rerun with non-force `rm -r`
cleanup and succeeded; this did not affect repository state or validation.

Final audit confirms no changes to VERSION, wrapper, Gradle properties, flake
lock, settings lock or packaged license files. The operator subsequently
regenerated package-deps hashes and corrected the freshness-guard key layout as
recorded above. All requested Gradle validations passed. Nix validation comes
from the operator's evidence, not an inference from Gradle results.

Pre-operator worktree snapshot, retained as historical agent-pass evidence
(`git status --short`; `R`/`D` entries staged by git mv/rm and
`M` edits are all uncommitted; new provenance/report files remain untracked):

```text
 M .github/workflows/ci.yml
D  .gitmodules
 M ARCHITECTURE.md
 M QUALIFICATION.md
 M README.md
 M RELEASE_CHECKLIST.md
 M THIRD_PARTY.md
D  TOTIPO_JAVA_PIN.md
 M bootstrap-m0.sh
 M build.gradle.kts
 M flake.nix
 M gradle.lockfile
 M gradle/verification-metadata.xml
 M package.nix
D  packaging/strict-locking.gradle
 M qualification/PACKAGED_LAUNCH.md
 M settings.gradle.kts
RM src/main/java/dev/totipo/desktop/Base32.java -> src/main/java/org/totipo/desktop/Base32.java
RM src/main/java/dev/totipo/desktop/DesktopApplication.java -> src/main/java/org/totipo/desktop/DesktopApplication.java
RM src/main/java/dev/totipo/desktop/MergeDraft.java -> src/main/java/org/totipo/desktop/MergeDraft.java
RM src/main/java/dev/totipo/desktop/MergeInputs.java -> src/main/java/org/totipo/desktop/MergeInputs.java
RM src/main/java/dev/totipo/desktop/MergeWrites.java -> src/main/java/org/totipo/desktop/MergeWrites.java
RM src/main/java/dev/totipo/desktop/MutationGate.java -> src/main/java/org/totipo/desktop/MutationGate.java
RM src/main/java/dev/totipo/desktop/NioVaultAccess.java -> src/main/java/org/totipo/desktop/NioVaultAccess.java
RM src/main/java/dev/totipo/desktop/PasswordChangeController.java -> src/main/java/org/totipo/desktop/PasswordChangeController.java
RM src/main/java/dev/totipo/desktop/PasswordChangeSubmission.java -> src/main/java/org/totipo/desktop/PasswordChangeSubmission.java
RM src/main/java/dev/totipo/desktop/PasswordInput.java -> src/main/java/org/totipo/desktop/PasswordInput.java
RM src/main/java/dev/totipo/desktop/StateSubscriber.java -> src/main/java/org/totipo/desktop/StateSubscriber.java
RM src/main/java/dev/totipo/desktop/TokenDraft.java -> src/main/java/org/totipo/desktop/TokenDraft.java
RM src/main/java/dev/totipo/desktop/TokenWriteController.java -> src/main/java/org/totipo/desktop/TokenWriteController.java
RM src/main/java/dev/totipo/desktop/TokenWrites.java -> src/main/java/org/totipo/desktop/TokenWrites.java
RM src/main/java/dev/totipo/desktop/TotipoDesktop.java -> src/main/java/org/totipo/desktop/TotipoDesktop.java
RM src/main/java/dev/totipo/desktop/VaultAccess.java -> src/main/java/org/totipo/desktop/VaultAccess.java
RM src/main/java/dev/totipo/desktop/VaultWindowController.java -> src/main/java/org/totipo/desktop/VaultWindowController.java
RM src/main/java/dev/totipo/desktop/clipboard/TotpClipboard.java -> src/main/java/org/totipo/desktop/clipboard/TotpClipboard.java
RM src/main/java/dev/totipo/desktop/clipboard/TotpClipboardPayload.java -> src/main/java/org/totipo/desktop/clipboard/TotpClipboardPayload.java
RM src/main/java/dev/totipo/desktop/ui/Edt.java -> src/main/java/org/totipo/desktop/ui/Edt.java
RM src/main/java/dev/totipo/desktop/ui/LauncherFrame.java -> src/main/java/org/totipo/desktop/ui/LauncherFrame.java
RM src/main/java/dev/totipo/desktop/ui/LauncherPanel.java -> src/main/java/org/totipo/desktop/ui/LauncherPanel.java
RM src/main/java/dev/totipo/desktop/ui/LauncherView.java -> src/main/java/org/totipo/desktop/ui/LauncherView.java
RM src/main/java/dev/totipo/desktop/ui/MergeEditorPanel.java -> src/main/java/org/totipo/desktop/ui/MergeEditorPanel.java
RM src/main/java/dev/totipo/desktop/ui/PasswordChangeDialog.java -> src/main/java/org/totipo/desktop/ui/PasswordChangeDialog.java
RM src/main/java/dev/totipo/desktop/ui/PasswordChangePanel.java -> src/main/java/org/totipo/desktop/ui/PasswordChangePanel.java
RM src/main/java/dev/totipo/desktop/ui/PasswordPrompt.java -> src/main/java/org/totipo/desktop/ui/PasswordPrompt.java
RM src/main/java/dev/totipo/desktop/ui/SwingUsability.java -> src/main/java/org/totipo/desktop/ui/SwingUsability.java
RM src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java -> src/main/java/org/totipo/desktop/ui/TokenBrowserPanel.java
RM src/main/java/dev/totipo/desktop/ui/TokenEditDialog.java -> src/main/java/org/totipo/desktop/ui/TokenEditDialog.java
RM src/main/java/dev/totipo/desktop/ui/TokenEditorPanel.java -> src/main/java/org/totipo/desktop/ui/TokenEditorPanel.java
RM src/main/java/dev/totipo/desktop/ui/TokenPresentation.java -> src/main/java/org/totipo/desktop/ui/TokenPresentation.java
RM src/main/java/dev/totipo/desktop/ui/TokenSearch.java -> src/main/java/org/totipo/desktop/ui/TokenSearch.java
RM src/main/java/dev/totipo/desktop/ui/TotpDisplay.java -> src/main/java/org/totipo/desktop/ui/TotpDisplay.java
RM src/main/java/dev/totipo/desktop/ui/VaultFrame.java -> src/main/java/org/totipo/desktop/ui/VaultFrame.java
RM src/main/java/dev/totipo/desktop/ui/VaultPanel.java -> src/main/java/org/totipo/desktop/ui/VaultPanel.java
RM src/main/java/dev/totipo/desktop/ui/VaultView.java -> src/main/java/org/totipo/desktop/ui/VaultView.java
RM src/qualification/java/dev/totipo/qualification/FilesystemQualification.java -> src/qualification/java/org/totipo/qualification/FilesystemQualification.java
RM src/test/java/dev/totipo/desktop/Base32Test.java -> src/test/java/org/totipo/desktop/Base32Test.java
RM src/test/java/dev/totipo/desktop/ClipboardLifecycleTest.java -> src/test/java/org/totipo/desktop/ClipboardLifecycleTest.java
RM src/test/java/dev/totipo/desktop/DesktopApplicationTest.java -> src/test/java/org/totipo/desktop/DesktopApplicationTest.java
RM src/test/java/dev/totipo/desktop/MergeControllerTest.java -> src/test/java/org/totipo/desktop/MergeControllerTest.java
RM src/test/java/dev/totipo/desktop/MergeEditorTest.java -> src/test/java/org/totipo/desktop/MergeEditorTest.java
RM src/test/java/dev/totipo/desktop/MergeFixtures.java -> src/test/java/org/totipo/desktop/MergeFixtures.java
RM src/test/java/dev/totipo/desktop/MergeWritesTest.java -> src/test/java/org/totipo/desktop/MergeWritesTest.java
RM src/test/java/dev/totipo/desktop/NioPasswordChangeTest.java -> src/test/java/org/totipo/desktop/NioPasswordChangeTest.java
RM src/test/java/dev/totipo/desktop/NioSmokeTest.java -> src/test/java/org/totipo/desktop/NioSmokeTest.java
RM src/test/java/dev/totipo/desktop/PasswordChangeTest.java -> src/test/java/org/totipo/desktop/PasswordChangeTest.java
RM src/test/java/dev/totipo/desktop/PasswordInputTest.java -> src/test/java/org/totipo/desktop/PasswordInputTest.java
RM src/test/java/dev/totipo/desktop/StateSubscriberTest.java -> src/test/java/org/totipo/desktop/StateSubscriberTest.java
RM src/test/java/dev/totipo/desktop/TestSupport.java -> src/test/java/org/totipo/desktop/TestSupport.java
RM src/test/java/dev/totipo/desktop/TokenWriteControllerTest.java -> src/test/java/org/totipo/desktop/TokenWriteControllerTest.java
RM src/test/java/dev/totipo/desktop/TokenWritesTest.java -> src/test/java/org/totipo/desktop/TokenWritesTest.java
RM src/test/java/dev/totipo/desktop/VaultWindowControllerTest.java -> src/test/java/org/totipo/desktop/VaultWindowControllerTest.java
RM src/test/java/dev/totipo/desktop/clipboard/ClipboardProbe.java -> src/test/java/org/totipo/desktop/clipboard/ClipboardProbe.java
RM src/test/java/dev/totipo/desktop/clipboard/TotpClipboardTest.java -> src/test/java/org/totipo/desktop/clipboard/TotpClipboardTest.java
RM src/test/java/dev/totipo/desktop/ui/LauncherPanelTest.java -> src/test/java/org/totipo/desktop/ui/LauncherPanelTest.java
RM src/test/java/dev/totipo/desktop/ui/PasswordBrowserTest.java -> src/test/java/org/totipo/desktop/ui/PasswordBrowserTest.java
RM src/test/java/dev/totipo/desktop/ui/PasswordChangePanelTest.java -> src/test/java/org/totipo/desktop/ui/PasswordChangePanelTest.java
RM src/test/java/dev/totipo/desktop/ui/PasswordPromptTest.java -> src/test/java/org/totipo/desktop/ui/PasswordPromptTest.java
RM src/test/java/dev/totipo/desktop/ui/PublicationPanelTest.java -> src/test/java/org/totipo/desktop/ui/PublicationPanelTest.java
RM src/test/java/dev/totipo/desktop/ui/TokenBrowserTest.java -> src/test/java/org/totipo/desktop/ui/TokenBrowserTest.java
RM src/test/java/dev/totipo/desktop/ui/TokenEditingBrowserTest.java -> src/test/java/org/totipo/desktop/ui/TokenEditingBrowserTest.java
RM src/test/java/dev/totipo/desktop/ui/TokenEditorPanelTest.java -> src/test/java/org/totipo/desktop/ui/TokenEditorPanelTest.java
RM src/test/java/dev/totipo/desktop/ui/TokenFixtures.java -> src/test/java/org/totipo/desktop/ui/TokenFixtures.java
RM src/test/java/dev/totipo/desktop/ui/TotpCopyTest.java -> src/test/java/org/totipo/desktop/ui/TotpCopyTest.java
RM src/test/java/dev/totipo/desktop/ui/TotpDisplayTest.java -> src/test/java/org/totipo/desktop/ui/TotpDisplayTest.java
RM src/test/java/dev/totipo/desktop/ui/UsabilityTest.java -> src/test/java/org/totipo/desktop/ui/UsabilityTest.java
RM src/test/java/dev/totipo/desktop/ui/VaultPanelTest.java -> src/test/java/org/totipo/desktop/ui/VaultPanelTest.java
D  vendor/totipo-java
?? TOTIPO_JAVA_DEPENDENCY.md
?? review/D1_MAVEN_DEPENDENCY_MIGRATION_REPORT.md
```

Pre-operator combined tracked diff (`git diff HEAD --stat`, covering both staged moves/removals
and unstaged content edits; the two untracked Markdown additions above are not
included in this Git statistic):

```text
 .github/workflows/ci.yml                           |  1 -
 .gitmodules                                        |  3 -
 ARCHITECTURE.md                                    | 13 ++--
 QUALIFICATION.md                                   |  7 ++
 README.md                                          | 81 ++++++++++++----------
 RELEASE_CHECKLIST.md                               | 12 ++--
 THIRD_PARTY.md                                     | 13 ++--
 TOTIPO_JAVA_PIN.md                                 | 25 -------
 bootstrap-m0.sh                                    | 27 ++++----
 build.gradle.kts                                   | 81 +++++++++++++++++++---
 flake.nix                                          |  2 -
 gradle.lockfile                                    |  4 +-
 gradle/verification-metadata.xml                   | 16 +++++
 package.nix                                        | 49 ++++++-------
 packaging/strict-locking.gradle                    |  7 --
 qualification/PACKAGED_LAUNCH.md                   |  2 +-
 settings.gradle.kts                                |  1 -
 .../java/{dev => org}/totipo/desktop/Base32.java   |  2 +-
 .../totipo/desktop/DesktopApplication.java         | 20 +++---
 .../{dev => org}/totipo/desktop/MergeDraft.java    |  4 +-
 .../{dev => org}/totipo/desktop/MergeInputs.java   |  4 +-
 .../{dev => org}/totipo/desktop/MergeWrites.java   |  4 +-
 .../{dev => org}/totipo/desktop/MutationGate.java  |  4 +-
 .../totipo/desktop/NioVaultAccess.java             |  8 +--
 .../totipo/desktop/PasswordChangeController.java   | 14 ++--
 .../totipo/desktop/PasswordChangeSubmission.java   |  6 +-
 .../{dev => org}/totipo/desktop/PasswordInput.java |  2 +-
 .../totipo/desktop/StateSubscriber.java            |  4 +-
 .../{dev => org}/totipo/desktop/TokenDraft.java    |  8 +--
 .../totipo/desktop/TokenWriteController.java       |  6 +-
 .../{dev => org}/totipo/desktop/TokenWrites.java   |  4 +-
 .../{dev => org}/totipo/desktop/TotipoDesktop.java |  2 +-
 .../{dev => org}/totipo/desktop/VaultAccess.java   |  6 +-
 .../totipo/desktop/VaultWindowController.java      | 12 ++--
 .../totipo/desktop/clipboard/TotpClipboard.java    |  4 +-
 .../desktop/clipboard/TotpClipboardPayload.java    |  2 +-
 .../java/{dev => org}/totipo/desktop/ui/Edt.java   |  2 +-
 .../totipo/desktop/ui/LauncherFrame.java           |  2 +-
 .../totipo/desktop/ui/LauncherPanel.java           |  2 +-
 .../totipo/desktop/ui/LauncherView.java            |  2 +-
 .../totipo/desktop/ui/MergeEditorPanel.java        |  8 +--
 .../totipo/desktop/ui/PasswordChangeDialog.java    |  2 +-
 .../totipo/desktop/ui/PasswordChangePanel.java     |  4 +-
 .../totipo/desktop/ui/PasswordPrompt.java          |  2 +-
 .../totipo/desktop/ui/SwingUsability.java          |  2 +-
 .../totipo/desktop/ui/TokenBrowserPanel.java       |  6 +-
 .../totipo/desktop/ui/TokenEditDialog.java         |  2 +-
 .../totipo/desktop/ui/TokenEditorPanel.java        |  8 +--
 .../totipo/desktop/ui/TokenPresentation.java       |  4 +-
 .../totipo/desktop/ui/TokenSearch.java             |  4 +-
 .../totipo/desktop/ui/TotpDisplay.java             |  6 +-
 .../{dev => org}/totipo/desktop/ui/VaultFrame.java |  6 +-
 .../{dev => org}/totipo/desktop/ui/VaultPanel.java |  8 +--
 .../{dev => org}/totipo/desktop/ui/VaultView.java  | 10 +--
 .../qualification/FilesystemQualification.java     |  6 +-
 .../{dev => org}/totipo/desktop/Base32Test.java    |  2 +-
 .../totipo/desktop/ClipboardLifecycleTest.java     |  8 +--
 .../totipo/desktop/DesktopApplicationTest.java     |  8 +--
 .../totipo/desktop/MergeControllerTest.java        | 18 ++---
 .../totipo/desktop/MergeEditorTest.java            | 14 ++--
 .../{dev => org}/totipo/desktop/MergeFixtures.java |  4 +-
 .../totipo/desktop/MergeWritesTest.java            | 10 +--
 .../totipo/desktop/NioPasswordChangeTest.java      |  6 +-
 .../{dev => org}/totipo/desktop/NioSmokeTest.java  | 38 +++++-----
 .../totipo/desktop/PasswordChangeTest.java         | 10 +--
 .../totipo/desktop/PasswordInputTest.java          |  2 +-
 .../totipo/desktop/StateSubscriberTest.java        |  6 +-
 .../{dev => org}/totipo/desktop/TestSupport.java   | 10 +--
 .../totipo/desktop/TokenWriteControllerTest.java   | 10 +--
 .../totipo/desktop/TokenWritesTest.java            |  4 +-
 .../totipo/desktop/VaultWindowControllerTest.java  |  6 +-
 .../totipo/desktop/clipboard/ClipboardProbe.java   |  2 +-
 .../desktop/clipboard/TotpClipboardTest.java       |  4 +-
 .../totipo/desktop/ui/LauncherPanelTest.java       |  2 +-
 .../totipo/desktop/ui/PasswordBrowserTest.java     |  8 +--
 .../totipo/desktop/ui/PasswordChangePanelTest.java |  6 +-
 .../totipo/desktop/ui/PasswordPromptTest.java      |  2 +-
 .../totipo/desktop/ui/PublicationPanelTest.java    |  6 +-
 .../totipo/desktop/ui/TokenBrowserTest.java        |  8 +--
 .../totipo/desktop/ui/TokenEditingBrowserTest.java | 10 +--
 .../totipo/desktop/ui/TokenEditorPanelTest.java    |  8 +--
 .../totipo/desktop/ui/TokenFixtures.java           |  4 +-
 .../totipo/desktop/ui/TotpCopyTest.java            | 14 ++--
 .../totipo/desktop/ui/TotpDisplayTest.java         |  8 +--
 .../totipo/desktop/ui/UsabilityTest.java           | 14 ++--
 .../totipo/desktop/ui/VaultPanelTest.java          |  6 +-
 vendor/totipo-java                                 |  1 -
 87 files changed, 434 insertions(+), 371 deletions(-)
```

Pre-operator `git diff --stat` (unstaged edits only):

```text
 .github/workflows/ci.yml                           |  1 -
 ARCHITECTURE.md                                    | 13 ++--
 QUALIFICATION.md                                   |  7 ++
 README.md                                          | 81 ++++++++++++----------
 RELEASE_CHECKLIST.md                               | 12 ++--
 THIRD_PARTY.md                                     | 13 ++--
 bootstrap-m0.sh                                    | 27 ++++----
 build.gradle.kts                                   | 81 +++++++++++++++++++---
 flake.nix                                          |  2 -
 gradle.lockfile                                    |  4 +-
 gradle/verification-metadata.xml                   | 16 +++++
 package.nix                                        | 49 ++++++-------
 qualification/PACKAGED_LAUNCH.md                   |  2 +-
 settings.gradle.kts                                |  1 -
 src/main/java/org/totipo/desktop/Base32.java       |  2 +-
 .../org/totipo/desktop/DesktopApplication.java     | 20 +++---
 src/main/java/org/totipo/desktop/MergeDraft.java   |  4 +-
 src/main/java/org/totipo/desktop/MergeInputs.java  |  4 +-
 src/main/java/org/totipo/desktop/MergeWrites.java  |  4 +-
 src/main/java/org/totipo/desktop/MutationGate.java |  4 +-
 .../java/org/totipo/desktop/NioVaultAccess.java    |  8 +--
 .../totipo/desktop/PasswordChangeController.java   | 14 ++--
 .../totipo/desktop/PasswordChangeSubmission.java   |  6 +-
 .../java/org/totipo/desktop/PasswordInput.java     |  2 +-
 .../java/org/totipo/desktop/StateSubscriber.java   |  4 +-
 src/main/java/org/totipo/desktop/TokenDraft.java   |  8 +--
 .../org/totipo/desktop/TokenWriteController.java   |  6 +-
 src/main/java/org/totipo/desktop/TokenWrites.java  |  4 +-
 .../java/org/totipo/desktop/TotipoDesktop.java     |  2 +-
 src/main/java/org/totipo/desktop/VaultAccess.java  |  6 +-
 .../org/totipo/desktop/VaultWindowController.java  | 12 ++--
 .../totipo/desktop/clipboard/TotpClipboard.java    |  4 +-
 .../desktop/clipboard/TotpClipboardPayload.java    |  2 +-
 src/main/java/org/totipo/desktop/ui/Edt.java       |  2 +-
 .../java/org/totipo/desktop/ui/LauncherFrame.java  |  2 +-
 .../java/org/totipo/desktop/ui/LauncherPanel.java  |  2 +-
 .../java/org/totipo/desktop/ui/LauncherView.java   |  2 +-
 .../org/totipo/desktop/ui/MergeEditorPanel.java    |  8 +--
 .../totipo/desktop/ui/PasswordChangeDialog.java    |  2 +-
 .../org/totipo/desktop/ui/PasswordChangePanel.java |  4 +-
 .../java/org/totipo/desktop/ui/PasswordPrompt.java |  2 +-
 .../java/org/totipo/desktop/ui/SwingUsability.java |  2 +-
 .../org/totipo/desktop/ui/TokenBrowserPanel.java   |  6 +-
 .../org/totipo/desktop/ui/TokenEditDialog.java     |  2 +-
 .../org/totipo/desktop/ui/TokenEditorPanel.java    |  8 +--
 .../org/totipo/desktop/ui/TokenPresentation.java   |  4 +-
 .../java/org/totipo/desktop/ui/TokenSearch.java    |  4 +-
 .../java/org/totipo/desktop/ui/TotpDisplay.java    |  6 +-
 .../java/org/totipo/desktop/ui/VaultFrame.java     |  6 +-
 .../java/org/totipo/desktop/ui/VaultPanel.java     |  8 +--
 src/main/java/org/totipo/desktop/ui/VaultView.java | 10 +--
 .../qualification/FilesystemQualification.java     |  6 +-
 src/test/java/org/totipo/desktop/Base32Test.java   |  2 +-
 .../org/totipo/desktop/ClipboardLifecycleTest.java |  8 +--
 .../org/totipo/desktop/DesktopApplicationTest.java |  8 +--
 .../org/totipo/desktop/MergeControllerTest.java    | 18 ++---
 .../java/org/totipo/desktop/MergeEditorTest.java   | 14 ++--
 .../java/org/totipo/desktop/MergeFixtures.java     |  4 +-
 .../java/org/totipo/desktop/MergeWritesTest.java   | 10 +--
 .../org/totipo/desktop/NioPasswordChangeTest.java  |  6 +-
 src/test/java/org/totipo/desktop/NioSmokeTest.java | 38 +++++-----
 .../org/totipo/desktop/PasswordChangeTest.java     | 10 +--
 .../java/org/totipo/desktop/PasswordInputTest.java |  2 +-
 .../org/totipo/desktop/StateSubscriberTest.java    |  6 +-
 src/test/java/org/totipo/desktop/TestSupport.java  | 10 +--
 .../totipo/desktop/TokenWriteControllerTest.java   | 10 +--
 .../java/org/totipo/desktop/TokenWritesTest.java   |  4 +-
 .../totipo/desktop/VaultWindowControllerTest.java  |  6 +-
 .../totipo/desktop/clipboard/ClipboardProbe.java   |  2 +-
 .../desktop/clipboard/TotpClipboardTest.java       |  4 +-
 .../org/totipo/desktop/ui/LauncherPanelTest.java   |  2 +-
 .../org/totipo/desktop/ui/PasswordBrowserTest.java |  8 +--
 .../totipo/desktop/ui/PasswordChangePanelTest.java |  6 +-
 .../org/totipo/desktop/ui/PasswordPromptTest.java  |  2 +-
 .../totipo/desktop/ui/PublicationPanelTest.java    |  6 +-
 .../org/totipo/desktop/ui/TokenBrowserTest.java    |  8 +--
 .../totipo/desktop/ui/TokenEditingBrowserTest.java | 10 +--
 .../totipo/desktop/ui/TokenEditorPanelTest.java    |  8 +--
 .../java/org/totipo/desktop/ui/TokenFixtures.java  |  4 +-
 .../java/org/totipo/desktop/ui/TotpCopyTest.java   | 14 ++--
 .../org/totipo/desktop/ui/TotpDisplayTest.java     |  8 +--
 .../java/org/totipo/desktop/ui/UsabilityTest.java  | 14 ++--
 .../java/org/totipo/desktop/ui/VaultPanelTest.java |  6 +-
 83 files changed, 434 insertions(+), 335 deletions(-)
```

The snapshots above predate operator validation. The current uncommitted state
additionally includes regenerated `package-deps.json`, the corrected/formatted
`package.nix`, and the operator's untracked `result` symlink. This finalization
edits only this report; all migration and operator changes remain uncommitted.

Ready for D1 final review
