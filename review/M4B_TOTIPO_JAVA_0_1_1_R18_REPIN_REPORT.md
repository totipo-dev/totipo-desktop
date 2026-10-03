# M4b — Totipo Java 0.1.1 / v1/r18 dependency repin

Status: **repin and compatibility/package validation complete; NOT QUALIFIED for release**.
All changes remain uncommitted. Human-operated Nix cache/build/rebuild, complete
package inventories/hashes and optional startup evidence have been reviewed.
No commit, tag, release or push occurred.

## Summary

Desktop now resolves released Totipo Java 0.1.1, targeting Vault Format v1/r18,
in place of Java 0.1.0/r17. The Java delta alone requires **no desktop semantic
implementation change**. The r18 application audit required narrow production
changes: explicit empty-password creation confirmation, display-only control-text
escaping, preservation of embedded newlines in editable issuer/account strings,
and clearer deletion/password-change disclosures. Protocol, token-write, merge,
retry, password-rewrap and TOTP semantics remain delegated to the released library.

The recommended observation-triggered orphan warning remains an **explicit
deferred limitation**. No desktop application-conformance claim is made.
Desktop VERSION remains `0.0.0-dev`; release status remains **NOT QUALIFIED**.

## Starting baseline

- Branch: `main`.
- HEAD: `986ec63da7545c946e171a375fc47aed3ec3d1bc`.
- Initial `git status --short`: empty; worktree confirmed clean before edits.
- Desktop VERSION: `0.0.0-dev` (unreleased).
- Sole direct Totipo dependency: `org.totipo:totipo-storage-nio:0.1.0`.
- Transitive core: `org.totipo:totipo-core:0.1.0`; runtime BC: `1.86`.
- Independently resolved baseline compile/runtime graphs confirmed these versions;
  baseline `verifyMavenBoundary` and distribution verification passed.
- `gradle.lockfile` strictly locks both Totipo modules across compile/runtime/test
  classpaths; `settings-gradle.lockfile` has only `empty=incomingCatalogForLibs0`.
- Lock mode STRICT, all configurations locked; verification strict in
  `gradle.properties`; metadata verification true, signatures false as before.
  Baseline Totipo verification covers each 0.1.0 JAR and module with SHA-256.
- Maven Central is the only configured dependency repository; no included build,
  Maven-local fallback, project dependency or source substitution.
- `package.nix` expected 0.1.0 JARs. `package-deps.json` already contained 0.1.0
  core/storage JAR/module/POM entries and BC 1.86; it is stale for this repin.
  Earlier docs incorrectly described it as a pre-source-migration cache; current
  wording now describes the observed 0.1.0 cache precisely.
- Baseline cache SHA-256: `42f4bad66299feaa7ddc12f529e8fc0765774f6a753a9d5ff3275b5a5e2f9f27`.
- Baseline/current `flake.lock` SHA-256:
  `6c1d5cd7f8a002e0ea4182351b9bcf77f6a0475552f4c4c7f148f49e0d0ca3e8`.
- Existing release/qualification status: M4a machinery, unreleased, NOT QUALIFIED.
  Earlier source-consumed filesystem evidence and operator package reports do
  not qualify this released-dependency repin.
- Non-Nix baseline `./gradlew clean test build`: PASS (50s). JDK
  OpenJDK 25.0.4.1+1, Gradle wrapper 9.8.0; production target Java 17.

## Upstream provenance

- Repository: https://github.com/totipo-org/totipo-java.
- [Released v0.1.1](https://github.com/totipo-org/totipo-java/releases/tag/v0.1.1).
- Tag target: `dfd76a9a9cff261fdb214ac85457bddb06f1d777`.
- Previous v0.1.0 target: `1865fd36a05d2153425aa288dc7b17ac80c71a69`.
- Released Maven Central coordinates:
  `org.totipo:totipo-storage-nio:0.1.1`, `org.totipo:totipo-core:0.1.1`.
- [Java SPEC_PIN at the release](https://github.com/totipo-org/totipo-java/blob/v0.1.1/SPEC_PIN.md)
  records Totipo v1/r18 spec commit
  `4623a7e1718e23504903096c92332597057bd8f0`. This is an exact committed
  revision; the upstream pin says there is no distinct r18 spec release/tag.
- Direct Maven Central downloads of both versions' POMs, modules and runtime JARs
  were inspected outside the desktop worktree. New hashes match the upstream
  [release-preparation artifact inventory](https://github.com/totipo-org/totipo-java/blob/v0.1.1/review/V0_1_1_RELEASE_PREPARATION_REPORT.md).
- A temporary upstream checkout under `/tmp` was used solely for review. No Java
  source, spec snapshot or portable corpus was copied into desktop or substituted
  for released artifacts. Maven Central remains the authoritative consumer boundary.

## Compatibility assessment

Reviewed the complete tagged source delta before desktop implementation edits.
Only nine production Java files changed, all through comments/Javadocs. Removing
comments/whitespace from each changed file yielded identical source. Both modules'
build scripts are unchanged. POM bytes are identical after substituting the version;
module API/runtime variants retain the same dependency relationship and BC 1.86.

Independently ran `javap -public -s` on every class in each released Maven JAR;
the resulting public declarations and JVM descriptors are identical between versions.
This includes VaultSession/VaultState, token descriptors/heads/alternatives,
create/open, create/update/merge builders, subset/partial resolution,
AdditionalConflict, publication retry/uncertain results, password-change results
and NioTotipo. No desktop-relevant method body or signature changed.

The unchanged production source and unchanged portable case JSON files support
the released compatibility statement: no wire-format, crypto-construction, TOKEN
grammar, graph, fold or TOTP semantic changes. Documentation clarifies per-head
metadata retention and operation-scoped conformance. The desktop suite uses the
new Maven bytes; it does not reproduce or independently certify Java's 90-case
portable conformance claim.

**Conclusion: no desktop semantic implementation change is required solely
because of the 0.1.1 dependency repin. No public API incompatibility was found.**

## r18 application audit

| Requirement | Classification | Evidence / result |
| --- | --- | --- |
| Empty-password creation | Code/UI change | Previously allowed silently after two matching empty fields. DesktopApplication now requires LauncherView.confirmEmptyPassword before create; LauncherFrame uses a warning with Cancel as default. It explains offline guessing. Reservation prevents reentrant actions, and shutdown is rechecked after the modal decision. Cancel/close never creates. Existing empty-password open remains accepted. DesktopApplicationTest covers accept/reject, open exemption, reentrancy and shutdown. |
| Absent canonical vault + orphan-looking names | Explicit limitation/deferred issue | NioVaultAccess calls high-level NioTotipo.create, which exposes no pre-creation observation context. NioTotipoStore.readVault/scanObjects expose it only through the experimental provider SPI. Desktop deliberately consumes no SPI; adding observation, handle ownership, an asynchronous confirmation stage and failure/race handling extends that boundary. No such redesign was performed. README advises checking synchronization/provider state and the missing bootstrap. This general guidance does not satisfy the observation-triggered SHOULD warning/confirmation. No exhaustive scan, identity/recoverability claim, deletion or orphan creation veto was added. |
| Tombstone/erasure wording | Already truthful; UI/docs clarification | Existing UI reports TOMBSTONED and hides ordinary active TOTP use for tombstones. No secure-erasure/provider-deletion claim was found. Token and merge editors now explicitly say logical deletion retains secrets/history/provider copies; README adds the same disclosure. |
| Password rewrap wording | Already truthful; UI/docs clarification | Existing controller preserves root on acknowledged change and retires STALE/UNCERTAIN sessions. No security-reset/root-rotation claim was found. A persistent form explanation and README disclose same-root rewrap, retained old bootstrap copies, no rollback protection and no root-compromise recovery. |
| State/conflict presentation | Already satisfied within exposed API | TokenPresentation distinguishes complete alternatives, semantic conflicts, equal-valued distinct causal heads, TOMBSTONED, incomplete values and unresolved ancestry. VaultPanel keeps observation progress/diagnostics separate from freshness. Alternative labels imply no winner. AdditionalConflict stops normal save and offers latest review/cancel or separately confirmed frozen partial publication, explicitly warning omitted/new information can remain competing. Success wording acknowledges publication, never complete resolution. Existing MergeControllerTest/MergeWritesTest/TokenBrowserTest cover these paths. |
| Untrusted issuer/account/client text | Code/UI change | Existing list and merge-choice renderers disable HTML; detail/merge areas and fields are literal Swing text. Added display-only escaping of control/NUL, format/direction, Unicode line separators and backslashes, preventing forged detail lines. Original model strings are retained. Disabled JTextField newline filtering in token fields/custom merge fields to avoid silent space replacement during editing. New tests prove exact strings reach update/merge builders. Search case-folds temporary comparisons only, never stored or displayed values. |

Literal edit fields still contain exact original text; browse/merge displays offer
visible escapes. Native visual/accessibility behavior of these changes awaits
qualification. No blanket application-conformance certification is inferred.

## Gradle dependency changes

- `totipoJavaVersion`: `0.1.0` → `0.1.1`.
- `./gradlew dependencies --write-locks` regenerated the lock deliberately.
  Only core and storage-nio version lines changed. BC/JUnit/other entries and
  `settings-gradle.lockfile` remain unchanged; no manual Gradle lock edits.
- Strict `./gradlew compileJava` initially rejected exactly four new artifacts:
  the two JARs and module metadata. Verification was not disabled.
- Used the established bootstrap process's Gradle steps:
  `./gradlew dependencies --write-locks --write-verification-metadata sha256`
  and `./gradlew build --write-verification-metadata sha256`.
- Gradle generated four new checksum entries; obsolete 0.1.0 Totipo verification
  entries were narrowly removed. All unrelated verification entries/configuration
  remain untouched. No wildcard trust or broad acceptance was introduced.
- Normal resolution prefers Gradle modules; no new POM verification entry was
  generated. POMs were independently compared/downloaded for provenance.

| New verified artifact | SHA-256 |
| --- | --- |
| totipo-core-0.1.1.jar | `8a100f458fa234bb85a208537a1106eb376fed702215fc8a5d27e0bd96815c87` |
| totipo-core-0.1.1.module | `fbd7d4cc41e1c183a528890643e7a4a1ca7c6ca564639e262c6411bd77ea5331` |
| totipo-storage-nio-0.1.1.jar | `691b56c8831f71dafb7d5cb6f7d68ee59c9f42c5bb19c29c9455e1925237334e` |
| totipo-storage-nio-0.1.1.module | `31344ba97aad7ade0bf2c9e37d1715b8e2ef947e0ff1bbf77be406d5a972c7fa` |

## Maven boundary

Resolved with `./gradlew dependencies --configuration compileClasspath` and
`./gradlew dependencies --configuration runtimeClasspath`:

```text
compileClasspath:
desktop
└── org.totipo:totipo-storage-nio:0.1.1  [sole direct Totipo dependency]
    └── org.totipo:totipo-core:0.1.1

runtimeClasspath:
desktop
└── org.totipo:totipo-storage-nio:0.1.1
    └── org.totipo:totipo-core:0.1.1
        └── org.bouncycastle:bcprov-jdk18on:1.86
```

Gradle additionally prints strict lock constraints for each resolved version.
`verifyMavenBoundary`, through `check`, passed and rejects project/non-Maven
components, obsolete namespace, version skew, extra/direct core dependencies,
missing transitive core and incorrect runtime BC. Settings retain only Maven
Central, FAIL_ON_PROJECT_REPOS, no composite build/local fallback/substitution.
No 0.1.0 Totipo module remains in the resolved compile/runtime/test graph.

## Distribution verification

`installDist`, ZIP and TAR verification: PASS. Runtime inventory is exactly:

| JAR | SHA-256 |
| --- | --- |
| totipo-desktop-0.0.0-dev.jar | `e6c6c8460dfc5a949da93a0acd9710f306e8c18af893afc8b00a6a68ff193fc6` |
| totipo-storage-nio-0.1.1.jar | `691b56c8831f71dafb7d5cb6f7d68ee59c9f42c5bb19c29c9455e1925237334e` |
| totipo-core-0.1.1.jar | `8a100f458fa234bb85a208537a1106eb376fed702215fc8a5d27e0bd96815c87` |
| bcprov-jdk18on-1.86.jar | `2af190b300cbb0b35e248ccf5f4a06b6072030aeb3da7a98ec73abe5b4cb371f` |

Installed file inventory (11 files), identical in both archives:

```text
LICENSE
THIRD_PARTY.md
VERSION
bin/totipo-desktop
bin/totipo-desktop.bat
lib/bcprov-jdk18on-1.86.jar
lib/totipo-core-0.1.1.jar
lib/totipo-desktop-0.0.0-dev.jar
lib/totipo-storage-nio-0.1.1.jar
licenses/BOUNCY_CASTLE_LICENSE.html
licenses/TOTIPO_JAVA_LICENSE
```

No old/mixed/duplicate Totipo version, test/spec/corpus/review/source/cache files
are distributed. Launchers retain the correct main class, four-JAR classpath,
Attach disable option and no build-host paths. All Totipo production classes
remain Java 17 bytecode. Packaged JARs equal Gradle-resolved artifacts; Totipo JAR
hashes also equal independently downloaded Maven Central bytes. Licenses remain
unchanged and match upstream.

## Nix source changes

Static edits only: `package.nix` install checks now expect
`totipo-storage-nio-0.1.1.jar` and `totipo-core-0.1.1.jar`; stale-cache comment
describes repins. Preserved full JDK/JRE model, Linux boundary, filtering, four-JAR
inventory, Attach-disabled launcher, cache readiness guard and fetchDeps mechanism.
`flake.nix` and `flake.lock` are unchanged. Host architecture is x86_64;
the flake exposes `packages.x86_64-linux.default`.

## Human-operated Nix dependency-cache regeneration

**Human-operated Nix dependency-cache regeneration: PASS based on supplied
command output and reviewed generated diff.** No Nix command or generated cache
update script was executed by the agent. Under
`https://repo.maven.apache.org/maven2/org`, the transition is exactly:

```text
totipo#totipo-core/0.1.0        -> totipo#totipo-core/0.1.1         [jar, module, pom]
totipo#totipo-storage-nio/0.1.0 -> totipo#totipo-storage-nio/0.1.1  [jar, module, pom]
```

Exact fish command supplied for Gate 1:

```fish
set update_script (
    nix build \
        --no-link \
        --print-out-paths \
        'path:.#packages.x86_64-linux.default.mitmCache.updateScript'
)

$update_script
```

The human supplied update-script output, `git status --short`, and
`git diff -- package-deps.json`. The script reached `gradleUpdateScript` with
Gradle 9.7.1, executed all 13 tasks and printed `BUILD SUCCESSFUL in 47s` and
`gradleUpdateScript completed in 47 seconds`. Distribution verification listed
the four expected JARs; Maven-boundary verification passed. Fontconfig reported
missing default configuration/no writable cache directories; these diagnostics
did not prevent the supplied successful test/check result. This is headless build
evidence, not native GUI qualification.

Reviewed both the supplied diff and the actual worktree diff: both old entries
are gone, both new entries contain generated JAR/module/POM hashes, BC 1.86 and
all unrelated entries are byte-identical. Decoded all six generated SRI hashes
and compared them with the independently downloaded Maven Central artifacts;
all match. The new POM SHA-256 values are:

- Core: `6cb13d022cceec673794497bfd092b8cb84774f30ecd3b21a3194deddbe1be66`.
- Storage: `09310a25382ec4adfa8f2d2f060ddb1210568c68a7feddebfefa5456242bf62a`.

JAR/module hashes match the verification table above. No hashes were guessed or
hand-edited. `flake.lock` remains unchanged at its baseline SHA-256. Generated
`package-deps.json` SHA-256:
`1447e7391c512f2712b5bc4921cbe9461fd2402d0307fd772c77ad77197971c3`.
No unexpected dependency churn or regeneration failure occurred. Gate 1 accepted.

## Human-operated Nix validation

**Human-operated Nix validation: PASS based on supplied command output** for
x86_64-linux flake check, build and forced rebuild. The human supplied execution
of these exact commands, without failure output:

```fish
nix flake check path:.
nix build path:.
nix build --rebuild path:.
```

`path:.` intentionally includes local uncommitted changes. The flake check warned
that aarch64-darwin, aarch64-linux and x86_64-darwin were omitted as incompatible;
no validation is claimed for those systems. Normal build and `--rebuild` returned
without errors or additional output. This supplies successful cache/package
execution and forced-rebuild evidence; no agent-executed Nix validation is claimed.
The human also supplied `git status --short` and these successful inventories:

```fish
find -L result -maxdepth 4 -type f -print | sort
find -L result -type f -name '*.jar' -print | sort
```

The full recursive inventory subsequently supplied by the human is exactly:

```text
result/bin/totipo-desktop
result/lib/totipo-desktop/bin/totipo-desktop-unwrapped
result/lib/totipo-desktop/lib/bcprov-jdk18on-1.86.jar
result/lib/totipo-desktop/lib/totipo-core-0.1.1.jar
result/lib/totipo-desktop/lib/totipo-desktop-0.0.0-dev.jar
result/lib/totipo-desktop/lib/totipo-storage-nio-0.1.1.jar
result/share/applications/totipo-desktop.desktop
result/share/doc/totipo-desktop/LICENSE
result/share/doc/totipo-desktop/licenses/BOUNCY_CASTLE_LICENSE.html
result/share/doc/totipo-desktop/licenses/TOTIPO_JAVA_LICENSE
result/share/doc/totipo-desktop/THIRD_PARTY.md
result/share/doc/totipo-desktop/VERSION
```

The recursive JAR inventory contains exactly the four listed JARs: no old Totipo,
mixed version or test JAR. The layout matches the derivation's package/install
checks, including the unwrapped executable, desktop entry and both license
notices. There are exactly 12 files and no unexpected test/spec/corpus/review/
source/cache resources. The packaged Windows launcher is intentionally removed
by the Linux package. Human-supplied SHA-256 hashes for all four JARs exactly
match the Gradle distribution table: desktop, core, storage-nio and BC.
The two Totipo JARs therefore also match the resolved strict-verified Maven
artifacts and the human-generated cache hashes. Package contents/hash review: PASS.

The tracked `result` symlink was changed by the human-operated build from
`/nix/store/0mp0cbvvqhkx1ldd4q5vvw05p13cy9xq-totipo-desktop-0.0.0-dev` to
`/nix/store/pfmp0r9nhi0jm1bwfgxw7lxx1f4r2yir-totipo-desktop-0.0.0-dev`.
This output path is not mounted in the agent environment: read-only attempts to
inspect its wrapper/entry/JAR bytes could not access the files. That is an
environment boundary, not evidence of a failed human build. The output link is
retained uncommitted for human review; it is excluded from the filtered Nix source.

The human supplied these additional read-only outputs; no Nix rerun was needed:

```fish
find -L result -type f -print | sort
sha256sum result/lib/totipo-desktop/lib/*.jar
```

Optional graphical launch: **human-reported PASS for launch/quick smoke only**.
The human ran `./result/bin/totipo-desktop` and reported that the app ran and a
quick smoke test looked good. No runtime error was reported. The specific
workflows, GUI environment and native checklist steps were not recorded; this is
not full GUI, clipboard, accessibility or filesystem qualification. Native
qualification status/checklist gates remain unchanged.

## Tests

Agent-executed non-Nix validation:

| Command / check | Result |
| --- | --- |
| Baseline `./gradlew clean test build` | PASS, 50s, Java 0.1.0 |
| Baseline compile/runtime dependency reports | PASS, confirmed 0.1.0 and BC 1.86 |
| `./gradlew dependencies --write-locks` | PASS; only two Totipo version lines |
| `./gradlew compileJava` before verification refresh | Expected FAIL; strict verification rejected four new Totipo artifacts |
| `./gradlew dependencies --write-locks --write-verification-metadata sha256` | PASS, generated narrow metadata |
| `./gradlew build --write-verification-metadata sha256` | Metadata generated; initially FAIL: two tests selected the newly added password explanation instead of status. Selector corrected to accessible status name; no Java API incompatibility. |
| `./gradlew clean test build` after correction | PASS, 25s |
| `./gradlew test --tests '*DesktopApplicationTest' --tests '*UntrustedTextTest' --tests '*TextRoundTripTest' --tests '*TokenEditorPanelTest' --tests '*MergeEditorTest' --tests '*PasswordChangeTest'` | PASS, focused application/UI/builder checks |
| Final `./gradlew clean test build` | PASS, 24s; 187 tests, zero failures/errors/skips |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 33s |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 33s; 187 tests, zero failures/errors/skips |
| `./gradlew installDist distTar distZip verifyDistribution verifyDistributionArchives` | PASS, first distribution |
| `./gradlew --no-build-cache --rerun-tasks installDist distTar distZip verifyDistribution verifyDistributionArchives` | PASS, second distribution, all eight tasks executed |
| Final compile/runtime dependency reports | PASS, exact graphs above |
| `./gradlew check` | PASS, Maven boundary/distribution/qualification-harness compilation; earlier full tests reused |
| Independent Maven download/source/POM/public JVM descriptor comparisons | PASS, temporary Java review harness |
| Independent installed/ZIP/TAR inventory/hash comparison | PASS, temporary Java harness and TAR extraction |
| Generated cache diff/SRI hash review against independent Maven bytes | PASS, temporary Java harness; the cache itself was generated by the human |
| `git diff --check` | PASS |

Temporary harnesses/logs remain outside the repository at
`/tmp/totipo-desktop-r18-evidence`; they are not build dependencies or package inputs.

Workflow coverage and limits:

- Real NIO tests: vault create/open/close/reopen; token creation/update/read; TOTP;
  concurrent alternatives including a tombstone, merge into active state;
  password rewrap success/root stability and authentication failure.
- Component/API fakes: ordinary status writes/restoration fields, conflict and
  equal-valued heads, incomplete/unresolved state, full/subset merge,
  AdditionalConflict latest review/frozen publication/cancel, retry/stop,
  password CHANGED/AUTHENTICATION_FAILED/FAILED/STALE/UNCERTAIN and session retirement.
- Clipboard tests use controlled payload/native-boundary substitutes; no new
  operating-system clipboard evidence was obtained. Human-reported packaged
  launch/quick smoke is recorded separately above.
- Explicit filesystem qualification harness compiled through check; it was not
  executed for this repin. No full native GUI checklist, screen-reader or Java 17 runtime check.

## Reproducibility

Two distribution generations in the same environment, separated by forced clean
online/offline tests: all 11 installed files and hashes identical; runtime JAR
inventory/hashes identical; ZIP/TAR semantic inventories identical to installDist.
Both archive bytes are also identical; no timestamp/format difference to explain.

- ZIP SHA-256: `07d161de1c81359b2809398e5e4032c1919673dfb0db456abb287dc090386538`.
- TAR SHA-256: `f0be18bdd613ca108ccc93e3e8bdc1ec30b8c8fedb82093cccefbca25196090e`.
- Human-operated Nix normal build and `--rebuild`: PASS based on supplied command
  output; no failure or reproducibility mismatch reported. Human-supplied packaged
  JAR hashes match all four Gradle JARs exactly, including the desktop JAR across
  wrapper Gradle 9.8.0 and Nix Gradle 9.7.1. This does not separately claim a
  before/after hash inventory for the entire Nix runtime closure.

## Qualification impact

**NOT QUALIFIED** remains explicit in README, QUALIFICATION and RELEASE_CHECKLIST.
No checklist boxes were checked. Source/build/dependency/distribution validation
does not establish filesystem durability, native GUI behavior, OS clipboard,
accessibility, Java 17 runtime behavior, platform support or release readiness.
Earlier filesystem/operator evidence retains its original scope/date. No new
root migration/re-key, secure deletion, rollback protection, DEVICE/provenance
machinery, parser duplication, persistent graph store or conformance corpus.

## Semantic changes

No changes to portable/protocol semantics or Java-dependent write/merge/retry/
password-change implementations. Desktop production application changes are:

1. Explicit decision before creating with an empty password; shutdown/reentrancy
   guarded; existing empty-password readers unaffected.
2. Visible escaping of untrusted control/direction text and backslashes in rows,
   details and merge alternatives/choices, while retaining exact original models.
3. Preserve newlines in token/custom merge text fields, avoiding silent stored
   value changes during otherwise ordinary editing.
4. Persistent password-rewrap and token-tombstone UI disclosures.

## Files changed

| File | Reason |
| --- | --- |
| build.gradle.kts | Repin sole direct Totipo dependency to 0.1.1 |
| gradle.lockfile | Gradle-generated core/storage version update |
| gradle/verification-metadata.xml | Generated four new Totipo hashes; remove four obsolete ones |
| package.nix | Static 0.1.1 runtime-JAR expectations/comment |
| package-deps.json | Human-generated replacement of only the two Totipo JAR/module/POM cache entries |
| result | Tracked output symlink updated by the human Nix build; retained for review |
| TOTIPO_JAVA_DEPENDENCY.md | Released Maven/tag/spec provenance and explicit conformance boundary |
| README.md | Current dependency/protocol, r18 disclosures, deferred orphan safeguard and completed human package evidence |
| ARCHITECTURE.md | Current dependency, confirmation/escaping/exact editing, orphan observation limitation |
| QUALIFICATION.md | Current dependency wording, preserve historical/non-native scope |
| RELEASE_CHECKLIST.md | Current Java/protocol coordinates only; every gate remains unchecked |
| THIRD_PARTY.md | Packaged released 0.1.1 inventory/tag references |
| src/main/java/org/totipo/desktop/DesktopApplication.java | Guard empty-password creation |
| src/main/java/org/totipo/desktop/ui/LauncherView.java | Explicit confirmation presentation seam |
| src/main/java/org/totipo/desktop/ui/LauncherFrame.java | Warning dialog/default cancel |
| src/main/java/org/totipo/desktop/ui/UntrustedText.java | Added display-only escape helper |
| src/main/java/org/totipo/desktop/ui/TokenPresentation.java | Escape displayed issuer/account/client/field values |
| src/main/java/org/totipo/desktop/ui/TokenEditorPanel.java | Exact newline retention and logical-deletion disclosure |
| src/main/java/org/totipo/desktop/ui/MergeEditorPanel.java | Escape displays, exact custom strings and deletion disclosure |
| src/main/java/org/totipo/desktop/ui/PasswordChangePanel.java | Persistent same-root rewrap disclosure |
| src/test/java/org/totipo/desktop/DesktopApplicationTest.java | Confirmation/open/shutdown/reentrancy coverage |
| src/test/java/org/totipo/desktop/TestSupport.java | Explicit controlled confirmation fake |
| src/test/java/org/totipo/desktop/PasswordChangeTest.java | Select status by accessible name |
| src/test/java/org/totipo/desktop/TextRoundTripTest.java | Added exact update/merge builder string checks |
| src/test/java/org/totipo/desktop/ui/TokenEditorPanelTest.java | Exact prefill/control string validation |
| src/test/java/org/totipo/desktop/ui/UntrustedTextTest.java | Added escaping and original-model preservation checks |
| review/M4B_TOTIPO_JAVA_0_1_1_R18_REPIN_REPORT.md | This review/evidence report |

`package-deps.json` was regenerated by the human; the agent only inspected it.
`flake.lock`, VERSION, wrapper, historical reports and licensing
files are unchanged.

## Stale-reference classification

Active build, lock, verification, package expectations and current dependency/
protocol statements now use Java 0.1.1/r18. Remaining 0.1.0 references outside
historical reports no longer identify 0.1.0/r17 as current. The generated cache
contains only 0.1.1 Totipo entries. Historical matches in M0, M3a
and D1 reports are preserved. This report intentionally records before/after
versions and old tag targets as provenance. No active r17 target remains.

## git diff --stat

```text
 ARCHITECTURE.md                                    | 24 +++++++++--
 QUALIFICATION.md                                   | 14 +++++--
 README.md                                          | 49 ++++++++++++++++------
 RELEASE_CHECKLIST.md                               |  2 +-
 THIRD_PARTY.md                                     |  6 +--
 TOTIPO_JAVA_DEPENDENCY.md                          | 22 ++++++----
 build.gradle.kts                                   |  2 +-
 gradle.lockfile                                    |  4 +-
 gradle/verification-metadata.xml                   | 20 ++++-----
 package-deps.json                                  | 16 +++----
 package.nix                                        |  6 +--
 result                                             |  2 +-
 .../org/totipo/desktop/DesktopApplication.java     | 13 ++++++
 .../java/org/totipo/desktop/ui/LauncherFrame.java  | 11 +++++
 .../java/org/totipo/desktop/ui/LauncherView.java   |  2 +
 .../org/totipo/desktop/ui/MergeEditorPanel.java    |  8 ++--
 .../org/totipo/desktop/ui/PasswordChangePanel.java |  7 +++-
 .../org/totipo/desktop/ui/TokenEditorPanel.java    |  6 ++-
 .../org/totipo/desktop/ui/TokenPresentation.java   | 10 ++---
 .../org/totipo/desktop/DesktopApplicationTest.java | 31 ++++++++++++++
 .../org/totipo/desktop/PasswordChangeTest.java     |  4 +-
 src/test/java/org/totipo/desktop/TestSupport.java  |  7 ++++
 .../totipo/desktop/ui/TokenEditorPanelTest.java    | 13 ++++++
 23 files changed, 211 insertions(+), 68 deletions(-)
```

The four new untracked files (including this report) are listed below; ordinary
`git diff --stat` excludes untracked content.

## git status --short

Final uncommitted status after all supplied human evidence was reviewed.

```text
 M ARCHITECTURE.md
 M QUALIFICATION.md
 M README.md
 M RELEASE_CHECKLIST.md
 M THIRD_PARTY.md
 M TOTIPO_JAVA_DEPENDENCY.md
 M build.gradle.kts
 M gradle.lockfile
 M gradle/verification-metadata.xml
 M package-deps.json
 M package.nix
 M result
 M src/main/java/org/totipo/desktop/DesktopApplication.java
 M src/main/java/org/totipo/desktop/ui/LauncherFrame.java
 M src/main/java/org/totipo/desktop/ui/LauncherView.java
 M src/main/java/org/totipo/desktop/ui/MergeEditorPanel.java
 M src/main/java/org/totipo/desktop/ui/PasswordChangePanel.java
 M src/main/java/org/totipo/desktop/ui/TokenEditorPanel.java
 M src/main/java/org/totipo/desktop/ui/TokenPresentation.java
 M src/test/java/org/totipo/desktop/DesktopApplicationTest.java
 M src/test/java/org/totipo/desktop/PasswordChangeTest.java
 M src/test/java/org/totipo/desktop/TestSupport.java
 M src/test/java/org/totipo/desktop/ui/TokenEditorPanelTest.java
?? review/M4B_TOTIPO_JAVA_0_1_1_R18_REPIN_REPORT.md
?? src/main/java/org/totipo/desktop/ui/UntrustedText.java
?? src/test/java/org/totipo/desktop/TextRoundTripTest.java
?? src/test/java/org/totipo/desktop/ui/UntrustedTextTest.java
```

## Readiness

Gradle/Maven application and generic distributions are aligned to Java 0.1.1/r18.
Production UX fixes are tested. Human cache regeneration and diff/hash review,
x86_64-linux flake check, normal build and forced rebuild passed. The complete
supplied package inventory and JAR hashes passed review; optional launch/quick
smoke was successful as reported by the human. Desktop is correctly aligned to
released Java 0.1.1/v1/r18 and ready for the next desktop application/qualification
step. The recommended orphan-observation safeguard remains explicitly deferred
for human architecture review. No full application-conformance or native release
qualification is claimed; VERSION remains the development sentinel. All changes,
including the human-updated tracked result link, remain uncommitted. Do not
commit/tag/release/push.
