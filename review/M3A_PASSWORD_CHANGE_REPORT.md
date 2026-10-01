# M3a password-change review report

## Scope and baseline

M3a implements explicit **Change Password…** for an open vault. All changes are
uncommitted for review; no commit, tag, publication or release was created.

Inspected committed M2b `e680378` before editing, including session/window close,
replay-latest subscription, token workflow ownership, partial/retry cleanup and
existing password validation. The initial working tree was clean. Inspected the
pinned public `VaultSession`, `PasswordChangeResult`, `SessionClosedException`, and
normative `API_DESIGN.md` entry-point, password, threading and close contracts.
No storage SPI or implementation/internal API was used.

The submodule remains clean at `3d97dca72604f39b6b475c0d5a0a8b076632cc42`, matching
`TOTIPO_JAVA_PIN.md` and the committed gitlink. No submodule update was performed.
The protocol target remains v1/r17.

## Structure and changed files

New production files:

- `MutationGate`: one small EDT-owned reservation shared by the two workflow owners.
- `PasswordChangeController`: password form lifetime, one submission, exact typed
  result handling, non-secret result knowledge and conservative retirement.
- `PasswordChangeSubmission`: validation with existing `PasswordInput`, confirmation
  comparison, exclusive caller-array ownership and finally wiping.
- `ui/PasswordChangePanel`: headlessly testable current/new/confirmation fields,
  validation, field clearing, busy state and cancellation.
- `ui/PasswordChangeDialog`: small owned modeless Swing shell.

Changed production files:

- `VaultWindowController`: constructs the shared gate and password controller using
  its existing session executor, retires password UI during ordinary close, and
  delivers post-close explanations. Presentation cleanup cannot abort close.
- `TokenWriteController`: acquires/releases the shared gate at its existing
  workflow boundaries. Create/update/merge, partial-save and retry semantics stay
  in this controller and are unchanged.
- `DesktopApplication`: displays retirement explanations through the launcher
  after session close; suppresses them during application shutdown.
- `ui/VaultView`, `ui/VaultFrame`, `ui/VaultPanel`: action/dialog callbacks, visible
  Change Password action and shared mutation availability.

New tests: `PasswordChangeTest`, `NioPasswordChangeTest`,
`ui/PasswordChangePanelTest`, `ui/PasswordBrowserTest`.
Existing `TokenWriteControllerTest` and `MergeControllerTest` gained password
exclusion checks; `NioSmokeTest.observe` and `TokenEditingBrowserTest.display`
were made package-visible to reuse existing test helpers. No existing test was
removed. `README.md`, `ARCHITECTURE.md`, and this report document M3a.

## Operation ownership and secret lifetime

The gate is held from form/editor opening until that workflow ends. It covers
Create, Update, Merge, AdditionalConflict decisions, partial save, and live exact
publication retry/stop, as well as the password form and submitted password work.
It rejects callbacks as well as disabling Create/Edit/Resolve/Change Password.
Refresh, the single state subscription, token selection and TOTP remain live.
A sticky abandoned TOKEN-publication notice alone does not reserve the gate.

The three Swing password fields use only `getPassword()`. No password is converted
to String. `PasswordChangeSubmission.prepare` reuses `PasswordInput` for current
and new arrays: valid UTF-16 and at most 1024 UTF-8 bytes. Empty passwords and
identical old/new passwords are accepted. There is no extra password policy.

Confirmation is compared directly against new, then immediately overwritten. A
validation error or mismatch wipes all obtained temporary arrays. All three Swing
documents are cleared before the background callback. Controls, including Cancel,
disable before submission. Cancellation before submission clears documents,
releases the reservation and invokes no core operation.

A single executor task exclusively owns current/new arrays. The only production
`changePassword` call is inside `PasswordChangeSubmission.execute` with finally
wiping both arrays and dropping their references, including on every typed result,
SessionClosedException, unexpected RuntimeException and propagating Error.
Rejected scheduling and rejected submissions also wipe. There is no password
material in application/window state, preferences, diagnostics or recovery state.
This is best-effort caller-buffer hygiene, not JVM secure erasure.

## Exact lifecycle mapping

| Result | Behavior |
| --- | --- |
| CHANGED | Record positive wrapper acknowledgement; retire form, release gate and say “Vault password change acknowledged.” Keep the same session open. Do not refresh, reopen, rewrite tokens or alter browser state. |
| AUTHENTICATION_FAILED | Explain that authentication with the supplied current password did not succeed for observed vault data, does not prove mere mistyping, and this attempt did not change the password. Keep session and form available for explicit fresh entry. |
| FAILED | Explain required observation/staging failed definitely and this attempt did not change the password. Keep the session; fresh entry permits an explicit new attempt. No uncertainty inferred. |
| STALE | Explain canonical data changed before replacement and this attempt did not replace it. Immediately retire this stale session; no continued token operations or password retry. Require explicit reopen. No either-password assertion. |
| UNCERTAIN | Explain durable acknowledgement was not established, and either previous or new password may be canonical. Immediately retire session and require explicit reopen/re-observation. No preferred password, retry, rollback or automatic Open. |

STALE retires because the authenticated root or canonical BASE changed, so the
current session is no longer an acceptable basis for lifecycle work. UNCERTAIN
retires because replacement was attempted without established acknowledgement;
the desktop has no authority to choose a current password.

Password uncertainty has no `PublicationRetry`, frozen TOKEN bytes or
`PartialResolution`. It is not TOKEN `PublicationUncertain` and is not substituted
by the sticky token notice. Live token capabilities prevent password entry through
the gate. Abandoning the token retry permits password entry; CHANGED preserves the
sticky notice. No password controller code accesses token capability/history APIs.

Unexpected RuntimeException does not fabricate a typed result. It records an
internal failure separately and conservatively closes with a generic explanation
that the outcome cannot be inferred. Exception text is never displayed or logged.
SessionClosedException follows close without a fresh password-error workflow.
Typed knowledge is recorded before UI cleanup and remains unchanged by later
cleanup exceptions, including while closing.

## Retirement, close and reopen

STALE/UNCERTAIN immediately use the ordinary close path: mark closing on EDT,
disable mutation entry points, retire/clear password UI, cancel state delivery,
clear the latest presentation reference and stop TOTP. Session close runs off EDT
on the same `totipo-session-N` executor, behind any running password call. Once the
session and frame are closed, the launcher presents the reopen-required message.
Thus a disposed frame cannot hide the explanation, and no stale session stays
interactive while the user reads it. Shutdown suppresses new result messages.

Window close and application shutdown never interrupt KDF/replacement and never
wait on EDT. Late results retain their exact meanings internally, but cannot
restore actions, reopen a dialog or show normal success/error UI. Close remains
idempotent for STALE/UNCERTAIN. Unsent forms create no task. Shutdown waits through
the existing controller-close lifecycle, including cleanup failure handling.

Explicit Open Vault is the only reopen path. It creates a new session with fresh
user-supplied password input. No old VaultState, alternatives, heads, merge inputs,
partial/retry capabilities or password arrays cross the boundary. Tests verify
launcher message lifetime, no automatic Open and explicit creation of a new scope.

## Tests and integration evidence

The final suite contains **143 tests**, preserving all **97 M1/M2 tests** and adding
46 M3a cases (including parameterized outcomes). All pass headlessly with no
failures, errors or skipped tests.

Coverage includes every typed result; all typed window-close and application-
shutdown races; unexpected exceptions and absent typed knowledge; SessionClosed;
cleanup-result preservation; same executor/thread for change and close; no refresh
on CHANGED; fresh-entry retries for definite failures; unsent cancellation/close;
confirmation and caller-buffer wiping; active token/merge/partial/retry exclusion;
password-form/running-work exclusion of all token entries; actual abandoned-token
sticky history across CHANGED; ongoing state, selection, TOTP ticking and Refresh;
and post-close launcher messages followed by explicit reopen.

Validation integration exercises both fields with empty, ASCII, BMP and supplementary
Unicode; malformed UTF-16; exact 1024-byte limits (including multibyte input);
over-limit input; matching/identical old/new values; and confirmation mismatch.
No duplicate validator implementation is used. Secret buffers are checked with
redacted boolean assertions; input-case test names do not print password contents.

Real NIO success test creates an existing temporary directory vault and active
token, captures root fingerprint and fixed-instant TOTP, receives CHANGED, and
continues using the same session. The fingerprint and TOTP are unchanged. After
close, the old password returns AuthenticationFailed, the new password opens,
and a newly observed token produces the identical fixed-instant TOTP under the
same fingerprint. All resources close and test-owned passwords/secrets are wiped.

Real NIO authentication-failure test receives AUTHENTICATION_FAILED for a different
current password, continues state/refresh/builder use, closes and successfully
opens with the old password and original fingerprint. No fragile filesystem races
were introduced for STALE/FAILED/UNCERTAIN; those use deterministic public fakes.
These local NIO tests are not universal filesystem/provider qualification.

## Production source audit

Reviewed all new production files and the integration diff, plus searches for
password conversion, password field reads, executors, state subscriptions, refresh,
publication capabilities and internal/storage SPI imports.

- No password-to-String conversion, getText password access, password cache,
  remembered recovery password, diagnostic secret or retained operation buffer.
- One blocking `session.changePassword` call, only through the existing session
  executor; no password executor, SwingWorker or virtual threads.
- No automatic password retry, rollback or reopen; no password retry capability.
- No `PublicationRetry`/`PartialResolution` in password handling; no token sticky
  notice clearing or substitution for password uncertainty.
- STALE remains definite pre-replacement; UNCERTAIN is not FAILED; authentication
  failure is not reduced to “wrong password.” No fabricated result on exception.
- CHANGED changes no token/browser state and requests no refresh. No new subscription,
  polling, token rebuild, root rotation or fingerprint authorization was introduced.
- The shared reservation excludes every live token workflow/capability. No storage
  SPI, implementation/internal API or production dependency was added.

## Reproducibility and validation

Production dependencies before and after are identical:
`dev.totipo:storage-nio` → pinned `dev.totipo:core` →
`org.bouncycastle:bcprov-jdk18on:1.86` (core-owned). Zero new production or test
dependencies. Lockfiles and verification metadata have no diff.

Gradle 9.8.0 wrapper/checksums, JDK 25 toolchain, `--release 17`, UTF-8,
`-Xlint:all`, `-Werror`, strict dependency verification and strict locking remain
unchanged. `verifyJava17Bytecode` passes on every production class (major 61,
non-preview). The full-JDK Nix shell configuration and exact gitlink are unchanged.

| Command/check | Result |
| --- | --- |
| `git submodule status` | PASS; exact pin, no modification marker. |
| `./gradlew clean test build` | PASS; tests, bytecode check and build/distributions. |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; all nine tasks executed. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; all nine tasks executed offline. |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | PASS; unchanged three-component production graph. |
| `git diff --check` | PASS. |
| `git -C vendor/totipo-java status --short` | Empty; clean submodule. |
| Gitlink, build configuration, locks, verification metadata, wrapper and Nix files | Unchanged. |
| Test XML totals | 143 tests; 0 failures, 0 errors, 0 skipped. |

Validation used the available full OpenJDK 25.0.4.1+1 from the Nix store. The `nix`
executable is absent, so `nix develop` could not be run. DISPLAY and WAYLAND_DISPLAY
are unset; no graphical smoke was possible. Swing panels and callbacks were tested
headlessly; no X11/Wayland requirement enters CI. Manual graphical review remains
an environment limitation, particularly dialog sizing/readability and real-window
interaction. No unsafe filesystem manipulation was attempted.

Final command logs are in `/tmp/m3a-build-final.log`, `/tmp/m3a-rerun-final.log`,
`/tmp/m3a-offline-final.log`, and `/tmp/m3a-dependencies-final.log` in this workspace
session. Earlier development runs exposed an incomplete wiring compile, a strict
lint warning in a test, and a test assertion that confused demand requests with
subscriptions; these were corrected before the final successful runs.

## Working tree for review

The following snapshots include this untracked report. `git diff --stat` shows
tracked-file changes only; the new files are listed by `git status --short` and
in the inventory above. Nothing is staged or committed.

`git status --short`:

```text
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/DesktopApplication.java
 M src/main/java/dev/totipo/desktop/TokenWriteController.java
 M src/main/java/dev/totipo/desktop/VaultWindowController.java
 M src/main/java/dev/totipo/desktop/ui/VaultFrame.java
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultView.java
 M src/test/java/dev/totipo/desktop/MergeControllerTest.java
 M src/test/java/dev/totipo/desktop/NioSmokeTest.java
 M src/test/java/dev/totipo/desktop/TokenWriteControllerTest.java
 M src/test/java/dev/totipo/desktop/ui/TokenEditingBrowserTest.java
?? review/M3A_PASSWORD_CHANGE_REPORT.md
?? src/main/java/dev/totipo/desktop/MutationGate.java
?? src/main/java/dev/totipo/desktop/PasswordChangeController.java
?? src/main/java/dev/totipo/desktop/PasswordChangeSubmission.java
?? src/main/java/dev/totipo/desktop/ui/PasswordChangeDialog.java
?? src/main/java/dev/totipo/desktop/ui/PasswordChangePanel.java
?? src/test/java/dev/totipo/desktop/NioPasswordChangeTest.java
?? src/test/java/dev/totipo/desktop/PasswordChangeTest.java
?? src/test/java/dev/totipo/desktop/ui/PasswordBrowserTest.java
?? src/test/java/dev/totipo/desktop/ui/PasswordChangePanelTest.java
```

`git diff --stat`:

```text
 ARCHITECTURE.md                                    | 88 +++++++++++++++++++++-
 README.md                                          | 15 +++-
 .../dev/totipo/desktop/DesktopApplication.java     |  5 +-
 .../dev/totipo/desktop/TokenWriteController.java   | 14 ++--
 .../dev/totipo/desktop/VaultWindowController.java  | 32 +++++++-
 .../java/dev/totipo/desktop/ui/VaultFrame.java     | 10 +++
 .../java/dev/totipo/desktop/ui/VaultPanel.java     |  6 ++
 src/main/java/dev/totipo/desktop/ui/VaultView.java |  4 +
 .../dev/totipo/desktop/MergeControllerTest.java    | 12 ++-
 src/test/java/dev/totipo/desktop/NioSmokeTest.java |  2 +-
 .../totipo/desktop/TokenWriteControllerTest.java   | 13 +++-
 .../totipo/desktop/ui/TokenEditingBrowserTest.java |  2 +-
 12 files changed, 182 insertions(+), 21 deletions(-)
```

## Deviations and readiness

No material implementation deviation from the requested M3a scope. The shared
coordination uses the permitted tiny per-window gate. Explanations use the permitted
post-close launcher pattern. Graphical smoke and execution through `nix develop`
were unavailable as described above; the supplied full JDK and all requested Gradle
checks were used directly. No deferred production behavior or new dependencies.

M3a is ready for code review, with manual graphical smoke still to be performed
when a display is available. A suitable next target is **M3b: desktop usability
hardening — clipboard policy, search/filter, keyboard/accessibility and safe
convenience features**. No M3b features are included in this change.
