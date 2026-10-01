# M3c — explicit TOTP clipboard copying

## Baseline and scope

Started from committed M3b, `2bc4d77 implement m3b`. Initial
`git status --short` was empty. Inspected TokenBrowserPanel, TotpDisplay, the
existing code widgets, DesktopApplication, VaultWindowController, VaultView,
VaultFrame, VaultPanel, SwingUsability, close/retirement paths and existing tests
before editing. The baseline test invocation succeeded. All 151 pre-M3c tests
remain unchanged.

The submodule started and remains at
`3d97dca72604f39b6b475c0d5a0a8b076632cc42`, exactly matching
TOTIPO_JAVA_PIN.md and the committed gitlink. Its working tree is clean.
No protocol/core/storage behavior or build input was changed.

All M3c work is uncommitted. No commit, tag, publication, release or release
packaging was performed. The explicitly requested Gradle build produces its
ordinary ignored build outputs, including the existing distribution tasks.

## Behavior and structure

Available ACTIVE TOTP codes have adjacent explicit **Copy code** buttons.
Each active semantic alternative has its own button, including equal current
digits; no preferred conflict alternative is inferred. Tombstones, incomplete
tokens and unavailable codes offer no usable Copy action. The copied String
is exactly the core-returned String, including leading zeros, with no adornment.
Only button activation copies. Rollover, selection, filtering and state delivery
do not copy or clear an existing clipboard lease.

DesktopApplication owns one TotpClipboard and passes it to all its controllers.
A controller creates a non-secret Object origin identity and gives its view a
narrow Copy callback guarded by its closing flag. Views cannot obtain the manager,
session executor or protocol capabilities through that callback. The manager
retains no VaultSession, VaultState, TokenState, TokenAlternative, token draft,
secret bytes, password, issuer/account or path.

Production structure:

- `clipboard/TotpClipboard.java`: one EDT-owned active lease, system clipboard
  acquisition boundary, narrow Copy/Access/Scheduler seams, deadline and retries.
- `clipboard/TotpClipboardPayload.java`: custom Transferable and ClipboardOwner.
- Existing application/controller/view classes: shared ownership, origin close,
  shutdown and narrow callback forwarding.
- TotpDisplay: copy-time validation using its existing injected Clock and tick.
- TokenBrowserPanel: adjacent buttons and one non-secret clipboard status label.

The payload exposes exactly two flavors: ordinary DataFlavor.stringFlavor and
`application/x-totipo-copy-marker;class=java.lang.String`. The marker is a new
random UUID v4 String for each copy. It contains no token ID, digits, issuer,
account, vault path or serialized application object. Only clipboard text contains
the code. The manager's lease stores marker, origin, timer cancellation and
clear-retry bookkeeping; it does not retain the payload/code. No copy history exists.

## Validity and lifetime

At activation TotpDisplay reads its injected Clock. A snapshot outside
`validFrom <= now < validUntil` goes through the existing deterministic tick
path first. Copy then rereads the clock and validates the resulting core interval.
Generation failure remains Code unavailable and disables/hides Copy without an
independent generation retry loop. Tests cover exactly expired and future-start
snapshots, new code selection after refresh, and clock advance during generation.

The hard-coded desktop policy is:

`clearAt = min(totp.validUntil, copyInstant + 30 seconds)`

No protocol validity or token period changes. Timer scheduling subtracts elapsed
time before scheduling, rounds up to a millisecond and caps the delay at 30 seconds.
Production uses one-shot javax.swing.Timer instances for the one active lease;
there is no new executor. Callbacks run on EDT, independently of KDF/save/close
executors. These are best-effort deadlines, subject to desktop/EDT scheduling.

A successful new copy performs one setContents, then cancels/replaces the old
lease. It never empties the clipboard first. A failed replacement creates no new
lease and leaves the prior successful lease's cleanup responsibility intact.

## Conditional clearing and ownership

Every deadline/close/shutdown clear follows the same path:

1. Require that the callback's exact lease is still active.
2. Read the current Transferable.
3. Require the private marker flavor and exact equality with the active marker.
4. Only on that positive match replace contents with empty plain text.
5. Drop the lease; an unknown/absent/different marker instead drops it without
   changing the clipboard.

String equality is never used for clearing. A subsequent external plain-text copy
of identical digits has no matching marker and survives. A late callback from
copy A cannot clear newer copy B. No previous clipboard data is read during copy,
snapshotted, retained or restored; current-payload reads serve identity checks only.

lostOwnership may run off EDT. The payload posts a marker-only callback through
SwingUtilities.invokeLater. Matching loss retires that lease; delayed loss for an
old marker cannot retire a new copy. Absence of loss is never proof of ownership:
the current marker is checked before every automatic overwrite.

The originating controller marks itself closing before clipboard cleanup and
before asynchronous session close. Old copy callbacks reject immediately. The
view disables/removes its presentation controls. Origin A close cannot clear B's
current lease. Application shutdown rejects new copies and conditionally clears
the current lease. Clipboard cleanup never waits for session close, blocks on a
retry loop, uses a session executor, calls System.exit or prevents frame disposal.
Already-started bounded retries are not restarted by additional close notifications.

## Unavailability, privacy and limitations

HeadlessException, SecurityException and IllegalStateException during initial
acquisition/write produce “Clipboard unavailable; code was not copied.”
There is no automatic copy retry or new lease. Success is reported only after
setContents returns successfully, with a non-secret best-effort clearing message.

Unavailable inspection/clear writes and unreadable marker data trigger safe
one-shot retries every 250 ms, at most 20 retries and at most five elapsed seconds.
The count also bounds backward-clock behavior; callbacks delayed past the retry
deadline retire without inspecting/writing. Every retry rechecks the exact current
marker. Exhaustion makes no false clear-success claim. No clear-success or
clear-failure UI is provided (optional in the milestone).

After copying, Totipo no longer exclusively controls the code. The OS, desktop
environment, clipboard manager/history, remote desktop, accessibility software
or another application may read or retain it. Totipo cannot delete those copies.
The immutable Java String cannot be securely wiped. No secure deletion, remote
clipboard deletion, manager-history deletion or prevention of reading is claimed.

JDK Clipboard has no atomic cross-process compare-and-set. The required marker
inspection and replacement are separate platform operations; simultaneous external
replacement between those calls cannot be ruled out by portable JDK APIs. Likewise,
timers can be delayed by a busy EDT, and process teardown may end pending best-effort
retries. The implementation follows the requested conditional-clear algorithm; it
does not claim stronger OS guarantees. These limitations are documented in
ARCHITECTURE.md and need platform qualification.

## Accessibility and keyboard behavior

Normal button accessible name: “Copy TOTP code”. Conflicted buttons identify their
presentation alternative, such as “Copy TOTP code for Alternative 2”. Neither their
names nor descriptions contain digits. The visible TOTP label retains M3b
accessibility and gets cleared on retirement, including detached widgets.

A named “TOTP clipboard status” label exposes concise result text without code
digits. Clipboard operations make no focus request. JButton standard focused
keyboard activation remains available. No global Ctrl/Cmd+C, code-click, selection,
hover or rollover copy is installed. Ordinary Swing text copying is unchanged.
M3b Find, F5, Create and other existing usability tests are preserved.

## Tests and deterministic strategy

Added:

- TotpClipboardTest: exact text/leading zeros, UUID marker, no snapshot/restore,
  8/24/30/240-second policy cases, different and identical external text, different
  marker, new marked copy, cancelled/late deadlines, off-EDT ownership loss,
  old/current callback ordering, origin close, cross-vault close, shutdown,
  unavailable copy, retained prior successful lease, unavailable inspection and
  clear write, recovery/replacement during retries, late elapsed-bound callbacks,
  stale retries, and invalid intervals.
- TotpCopyTest: normal/semantic-conflict/equal-digit/mixed-tombstone/dead/incomplete
  eligibility, exact string selection, copy-time validity, failed generation,
  stale detached buttons, closing, accessible names/status, write-independent Copy,
  no selection/filter/state/rollover clipboard changes, and ordinary text shortcuts.
- ClipboardLifecycleTest: two real controllers through DesktopApplication share
  the injected manager; origin cleanup, application shutdown, late copy rejection,
  off-EDT session close and disposal despite unavailable clipboard.
- ClipboardProbe: cross-package in-memory test fixture only.

Tests use an in-memory Access fake, a manually triggered one-shot scheduler and
controlled clocks. They deliberately deliver cancelled old callbacks and inject
availability failures. There are no sleeps for clipboard expiry, host clipboard
reads/writes, graphical clipboard tests, or new test dependencies.

## Validation

Validation results and final counts are recorded below after the final runs.

Final result: **181 tests, zero failures/errors/skips**: all 151 existing tests
plus 30 new tests (18 clipboard, 10 presentation, 2 lifecycle). No existing test
file was changed, removed or weakened.

| Check | Result |
| --- | --- |
| `git submodule status` | PASS; exact pin, no gitlink movement |
| `./gradlew clean test build` | PASS, 24s |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 35s; all tasks executed |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS, 35s; all tasks executed |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | Exit success, but replayed initial cached unresolved dependency report; investigated below |
| Same dependency report with `--no-configuration-cache` | PASS; all runtime dependencies resolved offline |
| `git diff --check` | PASS |
| Java 17 bytecode check | PASS; Gradle verifyJava17Bytecode and independent inspection of all 61 production classes: major version 61 |
| Strict verification / locking | Unchanged and active: strict verification property, lockAllConfigurations and LockMode.STRICT |
| Wrapper / compiler / Nix inputs | Unchanged Gradle 9.8.0 wrapper and checksums, JDK 25, release 17, UTF-8, -Xlint:all, -Werror and full-JDK flake |
| Submodule working tree / pin document | Clean / unchanged |

The first offline dependency inventory ran before Bouncy Castle was cached and
reported FAILED entries, although the dependency-report task itself exited zero.
Online test/build resolution populated the cache. The requested final report
reused that earlier configuration cache and replayed its stale failure entries.
A fresh offline report with `--no-configuration-cache` resolved everything,
consistent with the successful forced offline test build. No lock, verification
metadata, dependency or repository change was needed.

Build JVM: Nix-provided OpenJDK 25.0.4.1. The `nix` executable is unavailable in
this environment, so `nix develop` could not be run. Existing flake inputs were
left unchanged. A Fontconfig warning appeared during an early headless test run;
the headless Swing suite completed successfully.

No graphical DISPLAY or WAYLAND_DISPLAY is available. **None of the requested
real system-clipboard/manual GUI smoke scenarios was performed.** They remain
release-qualification work: ordinary paste/leading zeros, exact expiry/cap clearing,
different/identical external replacement, late old-copy callbacks, two-vault close,
selection/rollover behavior, conflict/tombstone eligibility, contention, and normal
Search/editor/merge/password behavior. Headless tests cover the corresponding
logic but do not establish native clipboard flavor interoperability, screen-reader
behavior or behavior of external clipboard managers.

### Dependency inventory before and after

Identical runtime tree:

```
desktop
└── pinned dev.totipo:storage-nio
    └── pinned core
        └── org.bouncycastle:bcprov-jdk18on:1.86
```

Tests retain JUnit BOM/Jupiter/Platform 6.1.3, apiguardian 1.1.2, jspecify 1.0.0,
opentest4j 1.3.0 and the same production tree. **Zero production or test dependencies
added.** Lockfiles, strict verification metadata, wrapper files/checksums, Gradle
configuration and Nix inputs have no diff.

## Production source audit

- All application clipboard acquisition, current-content reads and setContents
  calls are inside TotpClipboard. Normal Swing text-component behavior is untouched.
- Copy accepts only the displayed code String and its core interval/time through
  a presentation callback. No secret/Base32/password/token-ID copy action exists.
- No global Ctrl/Cmd+C, automatic code copy, code click/hover/selection copy,
  rollover clipboard update, history, preferences, notification or monitoring added.
- No previous clipboard snapshot/restore or retained previous payload list.
- The only automatic overwrite is guarded by active lease identity and exact
  current marker. Neither text equality nor missing lostOwnership authorizes it.
- Origin identity and marker checks prevent cross-vault cleanup; old deadlines,
  ownership callbacks and retries cannot affect a newer copy.
- Unavailable clearing has both elapsed-time and attempt bounds, no sleep or
  unbounded retry, and no claims of successful clearing.
- No code logging, diagnostics/history, clipboard contents in errors or code digits
  in Copy/status accessible metadata. Visible code accessibility is preserved.
- No MutationGate call for copying. TokenWriteController, merge machinery,
  publication retry, partial resolution and password-change classes are unchanged.
- Exactly one production state subscription remains, in VaultWindowController.
  No clipboard data enters VaultState or VaultDiagnostic.
- Close remains off EDT on the existing session executor; clipboard cleanup does
  not use that executor or the application NIO executor. Clipboard has no new thread.
- No storage SPI/protocol/core change, dependency change, gitlink movement or
  submodule edit.

## Files added and changed

Added production files:

- `src/main/java/dev/totipo/desktop/clipboard/TotpClipboard.java`
- `src/main/java/dev/totipo/desktop/clipboard/TotpClipboardPayload.java`

Changed production files:

- `DesktopApplication.java`: owns/injects the shared service; shutdown cleanup.
- `VaultWindowController.java`: opaque origin, guarded callback and close cleanup.
- `ui/TotpDisplay.java`: availability projection and copy-time clock validation.
- `ui/TokenBrowserPanel.java`: explicit adjacent buttons, stale-button guard/status.
- `ui/VaultView.java`, `ui/VaultFrame.java`, `ui/VaultPanel.java`: Copy forwarding.

Added tests: `ClipboardLifecycleTest.java`, `clipboard/TotpClipboardTest.java`,
`clipboard/ClipboardProbe.java`, and `ui/TotpCopyTest.java`.
Updated README.md and ARCHITECTURE.md; added this report.

## Deviations and remaining qualification

No feature-scope, dependency or protocol deviations. Optional clear-result UI was
omitted. Required graphical/native-clipboard smoke and Nix-shell validation were
unavailable, as detailed above. The specified JDK conditional-clear algorithm is
implemented, but its separate check/write calls cannot provide an absolute atomic
guarantee against simultaneous external clipboard replacement; no stronger claim
is made. Application teardown does not wait for pending best-effort retries.
These are material platform/lifecycle limitations, not secure-erasure guarantees.

## Review snapshot

The following literal Git outputs were captured after writing this report.
`git diff --stat` excludes untracked added files; the status and file list above
also identify those additions. Nothing is staged or committed.

### git status --short

```text
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/DesktopApplication.java
 M src/main/java/dev/totipo/desktop/VaultWindowController.java
 M src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java
 M src/main/java/dev/totipo/desktop/ui/TotpDisplay.java
 M src/main/java/dev/totipo/desktop/ui/VaultFrame.java
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultView.java
?? review/M3C_TOTP_CLIPBOARD_REPORT.md
?? src/main/java/dev/totipo/desktop/clipboard/
?? src/test/java/dev/totipo/desktop/ClipboardLifecycleTest.java
?? src/test/java/dev/totipo/desktop/clipboard/
?? src/test/java/dev/totipo/desktop/ui/TotpCopyTest.java
```

### git diff --stat

```text
 ARCHITECTURE.md                                    | 72 ++++++++++++++++++++--
 README.md                                          | 18 +++++-
 .../dev/totipo/desktop/DesktopApplication.java     | 12 +++-
 .../dev/totipo/desktop/VaultWindowController.java  | 17 +++++
 .../dev/totipo/desktop/ui/TokenBrowserPanel.java   | 41 +++++++++++-
 .../java/dev/totipo/desktop/ui/TotpDisplay.java    | 23 ++++++-
 .../java/dev/totipo/desktop/ui/VaultFrame.java     |  3 +
 .../java/dev/totipo/desktop/ui/VaultPanel.java     |  3 +
 src/main/java/dev/totipo/desktop/ui/VaultView.java |  3 +
 9 files changed, 180 insertions(+), 12 deletions(-)
```

## Before the first release-oriented hardening milestone

M3c supplies the bounded, marker-identified clipboard behavior with deterministic
headless coverage. It is not native desktop qualification or a release readiness
claim. The next milestone should be **M4a: desktop release hardening — local
filesystem qualification, GUI smoke suite, packaging strategy and release
checklist**. It should qualify supported filesystems, exercise actual native
clipboard marker interoperability/ownership under contention and shutdown, run the
full graphical/accessibility smoke suite across target desktops, and define
packaging/update/signing/release gates before any first desktop release.
