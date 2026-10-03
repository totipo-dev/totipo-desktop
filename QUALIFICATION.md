# Qualification evidence

Release status: **NOT QUALIFIED**. No first release has been declared.
Linux is the first desktop qualification target. No Windows/macOS support claim
is made. PASS below applies only to the named check and exact tested environment.
Build success, headless component tests and source inspection are not native GUI
or clipboard qualification.

The previously recorded filesystem qualification used the old source-consumed
Java implementation. Released Java 0.1.1 targets v1/r18 with unchanged portable semantics;
the M4b dependency repin and headless tests do not constitute new filesystem qualification.
Rerun the harness against a candidate package during later release qualification.
Prior Nix build evidence below predates the released-dependency migration; the
0.1.1 dependency cache has passed human-operated regeneration and diff review;
human-operated x86_64-linux flake check/build/rebuild passed. Supplied package/JAR
inventory and hash review passed. The operator reported successful packaged
launch and a quick smoke test; this does not complete the native GUI, clipboard
or accessibility qualification checklists.
See [the M4b repin report](review/M4B_TOTIPO_JAVA_0_1_1_R18_REPIN_REPORT.md) for
current source/build/package evidence. Native qualification remains unchanged.

| OS/distribution | Architecture | Desktop/window manager | Java runtime | Filesystem | Route | GUI smoke | Clipboard smoke | Filesystem qualification | Result/date | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Linux 6.18.53; isolated development environment, distribution not exposed (/etc/os-release absent) | amd64 | None available | Nix OpenJDK 25.0.4.1+1; java.vendor=N/A | ext4; POSIX=true | Explicit Gradle filesystem harness | UNQUALIFIED | UNQUALIFIED | PASS | Filesystem-only PASS, 2026-10-01T14:57:54Z | Create/reopen/token/update/conflict/merge/password rewrap/TOTP/root stability and cleanup passed; root path intentionally omitted |
| Same isolated Linux environment | amd64 | None available | OpenJDK 25.0.4.1+1 | ext4 | Generic installDist/ZIP/TAR | UNQUALIFIED | UNQUALIFIED | See harness row; not a GUI path test | NOT QUALIFIED, 2026-10-01 | Build/content/bytecode verification is separate from native qualification |
| Linux target, operator build environment not fully recorded | UNQUALIFIED | UNQUALIFIED | Pinned full jdk25 | UNQUALIFIED | Nix package | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | NOT QUALIFIED | Operator reports successful cache/package workflow after source-filter, UTF-8 locale and category-check fixes; native qualification and exact forced-rebuild comparison not recorded |
| Any environment with Java 17 | UNQUALIFIED | UNQUALIFIED | Java 17 unavailable here | UNQUALIFIED | Generic distribution | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | Java 17 bytecode inspection is not Java 17 runtime testing |
| Windows / macOS | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | No native package | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | UNQUALIFIED | Windows launcher source inspection does not qualify Windows |

Accessibility/keyboard native smoke, screen readers, desktop entry launching,
clipboard managers/history and GUI responsiveness are all **UNQUALIFIED**.
DISPLAY and WAYLAND_DISPLAY are absent. No Xvfb was added or used.

The ext4 result is not a claim for all ext4 mounts, all Linux filesystems, crash
or power-loss durability, remote synchronization, NFS, SMB, FUSE, network shares,
Syncthing, Dropbox, Nextcloud, or any cloud-sync folder. Those are **UNQUALIFIED**.
The harness does not reproduce the protocol corpus or test adversarial storage.

For each actual candidate, create rows with exact OS/distribution, architecture,
desktop/window manager and display protocol, runtime, filesystem, packaging route,
date, artifact identity and results. Record clipboard manager and paste target,
and screen-reader/version only if tested. Do not record production vault paths,
passwords, secrets or codes. Preserve failures and unperformed checks explicitly.
A platform/package cannot be claimed qualified while its required native checks
remain UNQUALIFIED.

Manual checklists:

- [GUI](qualification/GUI_SMOKE.md)
- [Clipboard](qualification/CLIPBOARD_SMOKE.md)
- [Accessibility](qualification/ACCESSIBILITY_SMOKE.md)
- [Packaged launch](qualification/PACKAGED_LAUNCH.md)
- [Blocking release checklist](RELEASE_CHECKLIST.md)

Build, runtime-loading and reproducibility evidence is recorded separately in
[the M4a report](review/M4A_RELEASE_HARDENING_REPORT.md).
