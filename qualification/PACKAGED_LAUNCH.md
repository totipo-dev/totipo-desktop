# Packaged launch qualification — UNQUALIFIED until executed

Record exact package version, checksum/store path, runtime, OS/architecture,
desktop/window manager, date and each result. Do not use `gradlew run` as evidence.

## Generic layout and extracted ZIP/TAR

- [ ] Run `installDist distZip distTar verifyDistribution verifyDistributionArchives`.
- [ ] Inspect Unix and Windows scripts for build-host absolute paths, secrets,
      the attach flag and a classpath made solely of packaged libraries. Windows inspection is not Windows qualification.
- [ ] Launch installDist from outside the checkout with JDK 25.
- [ ] Extract each archive to a fresh location (also test a path with spaces), and launch there with a minimal environment and no Gradle/check-out/vendor access.
- [ ] Repeat on Java 17 if available; otherwise record UNQUALIFIED, never PASS.
- [ ] Confirm supplied JAVA_HOME or PATH Java is the only developer requirement.
- [ ] Run native GUI, clipboard and accessibility checklists.

## Nix package

- [ ] Generate/review the official MITM cache; build `nix build .` with checks enabled.
- [ ] Inspect package source: desktop sources and Gradle locks/verification present; Java implementation source, spec snapshots/corpus, .git, build, .gradle, IDE/temp/review files absent. Released Totipo JARs come from the reviewed Maven dependency cache.
- [ ] Inspect `result/bin/totipo-desktop`: managed full JDK JAVA_HOME is fixed; utilities are supplied; no build-directory/classpath leakage.
- [ ] Inspect four runtime JARs under lib/totipo-desktop/lib; no test JARs or mutable caches/configuration.
- [ ] Inspect license material in share/doc/totipo-desktop and runtime legal notices.
- [ ] Inspect share/applications/totipo-desktop.desktop: Name=Totipo, Exec=totipo-desktop, Categories=Utility (optional trailing semicolon), Terminal=false; no handlers/autostart.
- [ ] From outside the checkout, launch the absolute result/bin/totipo-desktop with JAVA_HOME unset, minimal PATH and no development variables.
- [ ] `nix run .` and the native desktop entry both launch successfully.
- [ ] Run native GUI, clipboard and accessibility checklists using the actual package.

## Storage and shutdown (both routes)

- [ ] Vault chooser uses only the explicitly selected disposable directory.
- [ ] Create/open/close/reopen observes normal vault semantics; no default vault,
      migration or writes to the package installation directory appear.
- [ ] Run filesystemQualification separately on every claimed filesystem/environment.
- [ ] Window/application shutdown completes without lingering application process.

A successful headless launch reaching a HeadlessException proves only runtime
entry-point loading. It is not a GUI pass. A second cached Nix build is not a
reproducibility rebuild; use `nix build --rebuild .` when possible and record it.
