# Blocking release checklist

Current status: **NOT QUALIFIED**. M4a establishes build/qualification machinery;
no first release has been declared. Choose an actual candidate version and
complete the native qualification matrix in M4b before considering release.
Every required box must have evidence; UNQUALIFIED is a blocker, not PASS.
A green Gradle build alone cannot pass this checklist.

## Source/repository

- [ ] Clean worktree and reviewed release commit; no untracked packaging inputs.
- [ ] Exact gitlink matches TOTIPO_JAVA_PIN.md; upstream pin reviewed; protocol v1/r17 confirmed.
- [ ] VERSION contains the intended version and is **not `0.0.0-dev`**.
- [ ] `./gradlew validateVersion -PreleaseBuild=true` passes; intended tag equals VERSION.
- [ ] Dependency/verification/lock/cache changes reviewed; no unexpected dependencies.

## Build

- [ ] `./gradlew clean test build -PreleaseBuild=true`.
- [ ] `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test`.
- [ ] `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test`.
- [ ] Strict dependency verification and locking preserved, including composite package checks.
- [ ] All desktop/core/storage-nio production classfiles verified as Java 17.
- [ ] `./gradlew installDist distTar distZip verifyDistribution verifyDistributionArchives`.
- [ ] Two clean builds compared (semantic contents and SHA-256); reproducibility differences resolved/documented.
- [ ] Official `mitmCache.updateScript` generated a current reviewed package-deps.json; bootstrap marker absent.
- [ ] `nix flake check` and `nix build .` pass on every claimed Nix architecture.
- [ ] Nix Gradle/JDK versions reviewed; source filtering includes exact vendor source.
- [ ] Nix package contents, wrapper, entry and runtime inspected; repeat build/rebuild evidence recorded honestly.

## Tests

- [ ] All unit and real-NIO integration tests pass.
- [ ] Composite `:totipo-java:check` passes for packaged source.
- [ ] Explicit filesystemQualification passes on **every claimed filesystem/environment**, with cleanup confirmed.

## Native UI — blocking for every claimed platform/package

- [ ] QUALIFICATION.md has complete dated environment rows, not generic “Linux supported” claims.
- [ ] qualification/GUI_SMOKE.md passed; AdditionalConflict presentation evidence recorded.
- [ ] qualification/CLIPBOARD_SMOKE.md passed; clipboard manager/history limitations recorded.
- [ ] qualification/ACCESSIBILITY_SMOKE.md keyboard/accessibility checks passed; screen-reader scope stated accurately.
- [ ] qualification/PACKAGED_LAUNCH.md passed from actual installDist/extracted archives and Nix command as claimed.
- [ ] JDK 25 tested; Java 17 runtime status explicitly recorded (UNQUALIFIED if unavailable).
- [ ] Unperformed GUI/clipboard/filesystem checks leave release status **NOT QUALIFIED**.

## Security

- [ ] No secret logging, accidental credentials, host paths or source/cache material in artifacts/launchers.
- [ ] Narrow four-JAR runtime allowlist and dependency inventory unchanged or deliberately reviewed.
- [ ] `-XX:+DisableAttachMechanism` present in packaged launchers; no inflated security claims.
- [ ] Clipboard policy smoke passed with native interoperability evidence.
- [ ] Uncertainty/stale workflows reviewed; no implied synchronization/freshness.
- [ ] THIRD_PARTY.md, upstream licenses, notices and runtime legal material reviewed.
- [ ] No updater/network check, default vault directory, or packaging-dependent vault behavior.

## Artifacts

- [ ] Archive filenames/version correct; both archive contents inspected.
- [ ] Licenses/notices included; intended runtime only; no developer environment requirement.
- [ ] Final artifact SHA-256 generated with `scripts/sha256-release-artifacts.sh FILE...` and retained alongside exact artifacts.
- [ ] Any missing intentional application icon recorded as polish work, not replaced by placeholder branding.

## Release — future actions only; do not execute during M4a

- [ ] Release notes reviewed; precise qualified scope and limitations stated.
- [ ] All preceding gates pass before any tag is created.
- [ ] Tag equals chosen VERSION and identifies the reviewed release commit.
- [ ] Upload only final verified artifacts/checksums after explicit release authorization.

Manual updates only. Signing/notarization, native installers and auto-update need
separate decisions; this checklist does not authorize implementing or publishing them.
