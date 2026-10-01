# M1a lifecycle and observation review

Date: 2026-09-30 (America/New_York).

## Implemented behavior

M1a builds on the committed M0 bootstrap. The launcher now creates and opens local
NIO vaults in selected existing directories. Each successful operation transfers
one session to one independent vault window. Windows render local observation
progress and the current diagnostic codes, and provide manual Refresh. Window and
application shutdown close sessions off the EDT without interrupting in-flight
open/create operations. All changes remain uncommitted and unstaged for review.

There is no token display, token inspection, token mutation or TOTP functionality.
There is no automatic retry, merge, refresh loop, directory creation, filesystem
watcher or synchronization behavior. No build architecture, toolchain, dependency,
lock, verification metadata or submodule update was required.

## Files and final class/package structure

Changed existing files:

- `src/main/java/dev/totipo/desktop/TotipoDesktop.java`: small EDT entry point now
  creates the application owner.
- `src/main/java/dev/totipo/desktop/ui/Edt.java`: same guard, made public so owners
  in the parent package can enforce EDT access.
- `src/main/java/dev/totipo/desktop/ui/LauncherFrame.java`: directory/password
  prompts and presentation, with application-owned close handling.
- `src/main/java/dev/totipo/desktop/ui/LauncherPanel.java`: enabled actions, busy
  state and launcher status.
- `src/test/java/dev/totipo/desktop/ui/LauncherPanelTest.java`: updated M0 tests
  for working actions, busy transitions and EDT enforcement.
- `ARCHITECTURE.md`: implemented ownership, threading and semantics.
- `README.md`: current M1a capabilities and explicit token/TOTP exclusions.

Added production files:

```text
dev.totipo.desktop
  DesktopApplication         application owner and result handling
  VaultAccess                package-private open/create test seam
  NioVaultAccess             direct NioTotipo delegates
  PasswordInput              char[] UTF-16 / UTF-8 length validation
  StateSubscriber            replay-latest subscriber and EDT handoff
  VaultWindowController      one session/window lifetime

dev.totipo.desktop.ui
  LauncherView               narrow launcher presentation boundary
  PasswordPrompt             JPasswordField input and confirmation hygiene
  VaultView                  narrow vault presentation boundary
  VaultFrame                 top-level vault presentation
  VaultPanel                 observation/diagnostic rendering
```

Added tests:

```text
dev.totipo.desktop
  DesktopApplicationTest
  NioSmokeTest
  PasswordInputTest
  StateSubscriberTest
  TestSupport
  VaultWindowControllerTest

dev.totipo.desktop.ui
  PasswordPromptTest
  VaultPanelTest
```

This report is the remaining added file. The two presentation interfaces permit
headless owner tests; there is no generic MVC framework, service container or
second domain model.

## Ownership and lifecycle

```text
EDT: TotipoDesktop -> DesktopApplication
                        |
                        +-- LauncherFrame / LauncherPanel
                        +-- application executor: totipo-application
                        |     open / create / unclaimed-session close
                        |
                        +-- VaultWindowController (0..N)
                              +-- exactly one VaultSession
                              +-- VaultFrame / VaultPanel
                              +-- exactly one StateSubscriber
                              +-- latest VaultState reference only
                              +-- executor: totipo-session-N
                                    blocking session close
```

All application/window ownership begins on the EDT. No global current vault is
introduced. The application registers a successfully constructed controller before
starting its view and subscription. A controller accepts ownership on successful
construction; failures during its later start still use that controller's close
lifecycle. Before transfer, the application owns the returned session and explicitly
closes it if window/controller construction fails or shutdown has already begun.
Every returned session is either transferred or explicitly closed.

The application uses one ordinary single-thread executor for NIO entry points and
unclaimed-session cleanup. A reserved launcher busy state spans prompts, background
work, result presentation and cleanup. This prevents queued password-bearing
operations, including through nested EDT processing in modal dialogs. The operation
returns the result to the EDT only after its password `finally` has run.

Each controller has its own ordinary single-thread executor. Close is serialized
there, leaving independent windows independent. Executors are explicitly shut down;
there is no `shutdownNow`, interruption shortcut, EDT wait, virtual thread,
SwingWorker or new concurrency library.

## Open result mapping

| Variant | Presentation / ownership |
| --- | --- |
| Opened | Transfer once to a controller; otherwise close on the application executor. |
| Absent | No canonical Totipo vault was observed in the selected directory. Never create implicitly. |
| Unavailable | The selected directory/vault could not be reliably accessed. No password or corruption claim. |
| InvalidVault | Vault data was observed but could not be used as a valid vault. No repair or replacement. |
| AuthenticationFailed | Authentication did not succeed; this does not prove an incorrect password. Authenticated data may have changed or become unusable. |

## Create result mapping

| Variant | Presentation / ownership |
| --- | --- |
| Created | Same single ownership transfer/explicit cleanup rule as Opened. |
| AlreadyExists | A vault was observed, nothing was overwritten, and Open is an explicit subsequent action. |
| Failed | Definite failure of this create operation. No automatic retry. |
| Uncertain | Creation may have succeeded; Totipo cannot assert whether the operation became canonical. Do not blindly retry. Use Open Vault to re-observe. |

Uncertain never automatically creates again or opens. The password is already
wiped, and another Open requires an explicit user action and new password input.
Unexpected runtime exceptions remain generic application/session failures, not
protocol outcomes. UI and stderr never include exception messages or stack traces;
only fixed redacted development messages are written to stderr. No `Throwable` or
`Error` catch was introduced.

## Password and directory handling

The Swing chooser is directory-only, selects one path, and passes it through to
the NIO entry point. Desktop code does not create directories, canonicalize the
path or recursively inspect storage. NIO's later observation is authoritative.

Passwords never become Strings. The primary array belongs to the desktop; after
handoff the background operation owns it and overwrites it in `finally` after
open/create returns or throws. Rejected or unsubmitted arrays are wiped on the EDT.
Create compares two arrays directly, wipes confirmation immediately and wipes the
primary on mismatch. Password fields are cleared on dismissal, including mismatch
and cancellation. Password material never appears in titles, labels or diagnostics.
This is best-effort JVM secret hygiene, not a secure-erasure guarantee.

The dependency-free validator checks all UTF-16 code units and counts exact UTF-8
widths (1/2/3 bytes for BMP code points, 4 for a valid surrogate pair). It rejects
unpaired high/low surrogates and counts above 1024 bytes, saturating the internal
counter to avoid overflow. It retains no input or encoded copy. Empty input remains
valid; there is no added minimum, character-class or ASCII policy.

## State subscription, observation and diagnostics

The subscriber requests one item initially. Every callback can arrive off-EDT;
`onNext` enqueues one EDT application, then requests exactly one further item only
after rendering. While Swing has not processed it, demand stays zero and the core
coalesces replay-latest state. There is no unbounded demand or desktop state queue.
Cancellation clears the subscription reference and suppresses pending UI updates.
Terminal signals go to the EDT and are idempotent; stream errors are generic session
failures, separate from observation diagnostics. Unexpected completion retires the
window; explicit close cannot trigger a second close lifecycle.

A controller keeps only the latest authoritative immutable `VaultState` reference
and clears it on close. The panel calls only `observation()` and `diagnostics()`:

- Enumerating shows the discovered count and an indeterminate bar.
- Processing shows exact long processed/total counts. Only the visual bar is scaled
  to Swing's integer range, including counts near `Long.MAX_VALUE`.
- Finished says the local observation finished, with processed count and optional
  “diagnostics present”. It makes no freshness/completeness/synchronization claim.

Diagnostic codes replace the prior list on every render. Repeated current entries
remain repeated; no history, deduplication or invented causal explanation is added.
Tests supply states that fail on every token/editing/TOTP method.

Refresh calls the explicitly non-blocking `requestRefresh()` directly from the EDT.
It performs no independent filesystem work, creates no background refresh task and
is disabled when closing begins.

## Window close and application shutdown

Both frames use `DO_NOTHING_ON_CLOSE`. A window close request marks its controller
closing once, cancels the subscriber, clears the latest state, disables interaction
and shows Closing. The session executor calls `close()` off-EDT, shuts down, and
posts disposal/owner notification to the EDT. Repeated closes are harmless. A runtime
close exception is reported generically, retires the executor/window and is never
retried; the desktop cannot guarantee what an unexpectedly failing core close
managed to release. Normal close is the pinned API's contract and is tested with
both fake and real sessions.

Application shutdown disables Open/Create and initiates every controller close.
A running open/create is not interrupted. A late session result cannot create a
window: it is closed on the application executor and that cleanup remains part of
shutdown. The application keeps the launcher until no launcher lifecycle work is
pending and all controllers have finished closing. Then it explicitly shuts down
its executor and disposes the launcher. The EDT never blocks waiting for those
conditions and no `System.exit()` is used.

## Tests and real-NIO smoke coverage

Final suite: **28 tests, zero failures, zero errors, zero skips**. Table-driven loops
within tests exercise additional result variants and both open/create race cases.

| Test class | Tests | Coverage |
| --- | ---: | --- |
| DesktopApplicationTest | 12 | Every open/create result; ownership transfer; window-construction failure; password wiping after success/non-session/runtime results; off-EDT entry points; busy rejection; invalid human input; prompt cancellation; nested-dialog shutdown; two independent windows; shutdown during both open and create; no automatic retry/open after uncertainty. |
| StateSubscriberTest | 5 | Initial demand 1; blocked EDT prevents additional demand/render; render before next request; queued-state cancellation; late/duplicate subscription cancellation; EDT terminal callbacks; terminal idempotence; render failure; cancellation of queued terminal handling. |
| VaultWindowControllerTest | 4 | Latest-state rendering, direct refresh, off-EDT close, repeated close, disabled interaction, cancellation, executor retirement, owner notification, terminal events, post-transfer start failures, unexpected close failure. |
| PasswordInputTest | 1 | Empty/ASCII/NUL, non-ASCII BMP, supplementary pair, lone/mismatched surrogates, exact 1024 and 1025 UTF-8 boundaries across 1/2/3/4-byte encodings. |
| PasswordPromptTest | 2 | Confirmation always overwritten, primary preserved on match and overwritten on mismatch. |
| LauncherPanelTest | 2 | EDT guard and enabled/busy/re-enabled action transitions. |
| VaultPanelTest | 1 | Every observation variant, zero total, very large exact counts, current diagnostic replacement/repetition, no token access, disabled Refresh during close. |
| NioSmokeTest | 1 | Existing temporary directory, real create/close/open/close, test password wiped in finally. |

Tests use EDT handoffs, controlled subscriptions and bounded latches rather than
sleep-based timing, screenshots or Robot. The real-NIO smoke test took about
0.88–0.90 seconds in the measured full runs, so including it is practical. All
lifecycle unit tests still use the tiny `VaultAccess` seam; they do not depend on
KDF/filesystem operations. No upstream conformance corpus was duplicated.

## Validation commands and results

Environment: Linux, full Nix-store OpenJDK **25.0.4.1**, wrapper Gradle **9.8.0**;
production `--release 17`, UTF-8, `-Xlint:all`, `-Werror`. The initial wrapper invocation
downloaded its pinned distribution using the existing checksum configuration.

| Command / check | Result |
| --- | --- |
| `git submodule status` (before and after) | `3d97dca72604f39b6b475c0d5a0a8b076632cc42 vendor/totipo-java`; matches the pin document and HEAD gitlink. |
| `git -C vendor/totipo-java status --porcelain` | Empty; submodule remains clean. |
| `git ls-tree HEAD vendor/totipo-java` | Same exact gitlink. |
| `./gradlew compileJava` | Passed under existing lint/release settings. |
| `./gradlew test` during development | Initial run: 25/26 passed; the UI test selected a scrollbar button instead of Refresh. Corrected the test selector; subsequent run passed 26/26. Two more lifecycle tests were then added. |
| `./gradlew clean test build` | Passed, 28 tests; build/distribution tasks succeeded. |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | Passed, 28 tests, all nine tasks executed; about 10 seconds. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | Passed, 28 tests, all nine tasks executed; about 9 seconds. |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | Passed; unchanged storage-nio/core/Bouncy Castle inventory below. |
| `verifyJava17Bytecode` in each test/build | Passed for every production class, major 61 and no preview minor version. |
| `rg --files --no-ignore build/classes/java/main \| sort \| xargs javap -verbose \| rg 'Classfile \|major version:'` | All **17** production classfiles independently reported major version **61**. The first exploratory command omitted `--no-ignore`, so ignored build files were not listed; rerun with that flag supplied the complete audit. |
| `git diff --check` | Passed; no whitespace errors. Added source/report files were also checked for trailing whitespace. |
| Diff of build/settings/properties/wrapper/locks/verification metadata/Nix files/pin/gitlink | Empty. No reproducibility inputs regenerated or changed. |
| Source audit with `rg`, plus ownership/control-flow review | No storage SPI/internal API, System.exit, EXIT_ON_CLOSE, token/TOTP/editing calls, password-to-String conversion, unbounded subscription demand, blocking work on EDT or automatic retry. |
| `command -v nix` | No executable available in this environment; `nix develop` could not be exercised here. The existing flake still specifies full `jdk25`, not a headless package. |
| `DISPLAY` / `WAYLAND_DISPLAY` | Both unset; graphical manual smoke checks could not be performed. Headless panel/lifecycle tests and the real-NIO smoke test passed. |

An initial Swing test run printed a fontconfig configuration warning in this
restricted environment. It did not prevent headless Swing rendering tests; all
final validations passed. No X11/Wayland/Robot/screenshot requirement was added.

Strict dependency verification remains active through
`org.gradle.dependency.verification=strict`. Strict locking remains active through
`lockAllConfigurations()` and `LockMode.STRICT`, with unchanged lockfiles. Wrapper
version/checksums, JDK 25 toolchain, release 17 checks and the full-JDK Nix declaration
are unchanged.

## Production dependency inventory before / after

| Dependency | M0 | M1a |
| --- | --- | --- |
| JDK API | Java 17 Swing/AWT/concurrency surface, built with JDK 25 | Unchanged |
| `dev.totipo:storage-nio` | Pinned composite submodule project | Unchanged |
| `dev.totipo:core` | Exposed transitively by storage-nio at the same pin | Unchanged |
| `org.bouncycastle:bcprov-jdk18on` | 1.86, transitively owned by core | Unchanged |

**Zero new production dependencies and zero new test dependencies.**
The `totipo-java` pin did not move; `TOTIPO_JAVA_PIN.md` is unchanged.

## Design choices and remaining limits

There are no material deviations from the requested ownership/threading/semantics
architecture. The implementation adds two narrow view interfaces for deterministic
headless tests alongside the explicitly requested `VaultAccess` seam. It reserves
the launcher already during prompts to protect nested EDT loops. Progress uses a
0–1000 visual scale while preserving exact counts. Exception details are deliberately
redacted rather than logged, to avoid accidental disclosure of caller text.

No graphical interaction was validated in this environment, so launcher layout,
native directory chooser behavior and real window-manager close interaction still
need a manual smoke pass on a machine with a display. A fresh `nix develop` launch
also remains unvalidated here because Nix itself is unavailable. These limitations
do not change the passing headless lifecycle, real-NIO and reproducibility checks.

## Final working-tree evidence

`git status --short`:

```text
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/TotipoDesktop.java
 M src/main/java/dev/totipo/desktop/ui/Edt.java
 M src/main/java/dev/totipo/desktop/ui/LauncherFrame.java
 M src/main/java/dev/totipo/desktop/ui/LauncherPanel.java
 M src/test/java/dev/totipo/desktop/ui/LauncherPanelTest.java
?? review/M1A_LIFECYCLE_OBSERVATION_REPORT.md
?? src/main/java/dev/totipo/desktop/DesktopApplication.java
?? src/main/java/dev/totipo/desktop/NioVaultAccess.java
?? src/main/java/dev/totipo/desktop/PasswordInput.java
?? src/main/java/dev/totipo/desktop/StateSubscriber.java
?? src/main/java/dev/totipo/desktop/VaultAccess.java
?? src/main/java/dev/totipo/desktop/VaultWindowController.java
?? src/main/java/dev/totipo/desktop/ui/LauncherView.java
?? src/main/java/dev/totipo/desktop/ui/PasswordPrompt.java
?? src/main/java/dev/totipo/desktop/ui/VaultFrame.java
?? src/main/java/dev/totipo/desktop/ui/VaultPanel.java
?? src/main/java/dev/totipo/desktop/ui/VaultView.java
?? src/test/java/dev/totipo/desktop/DesktopApplicationTest.java
?? src/test/java/dev/totipo/desktop/NioSmokeTest.java
?? src/test/java/dev/totipo/desktop/PasswordInputTest.java
?? src/test/java/dev/totipo/desktop/StateSubscriberTest.java
?? src/test/java/dev/totipo/desktop/TestSupport.java
?? src/test/java/dev/totipo/desktop/VaultWindowControllerTest.java
?? src/test/java/dev/totipo/desktop/ui/PasswordPromptTest.java
?? src/test/java/dev/totipo/desktop/ui/VaultPanelTest.java
```

`git diff --stat`:

```text
 ARCHITECTURE.md                                    | 251 +++++++++++++++------
 README.md                                          |  13 +-
 .../java/dev/totipo/desktop/TotipoDesktop.java     |   5 +-
 src/main/java/dev/totipo/desktop/ui/Edt.java       |   4 +-
 .../java/dev/totipo/desktop/ui/LauncherFrame.java  |  38 +++-
 .../java/dev/totipo/desktop/ui/LauncherPanel.java  |  24 +-
 .../dev/totipo/desktop/ui/LauncherPanelTest.java   |  34 +--
 7 files changed, 264 insertions(+), 105 deletions(-)
```

The diff stat above covers tracked modifications only. The added production/test
files and this report are intentionally untracked and listed in the status snapshot;
nothing has been staged or committed. No tag, publication or release was created.

## M1b readiness assessment

The repository is ready for M1b: **read-only token projection + conflict-aware detail
+ TOTP display/timing**, subject to the normal review of these uncommitted changes
and the outstanding graphical/Nix environment checks above. The needed session
ownership, one-at-a-time state delivery, EDT rendering boundary, manual refresh and
orderly close lifecycle are implemented and tested. No token/TOTP/editing behavior
was introduced ahead of that milestone.
