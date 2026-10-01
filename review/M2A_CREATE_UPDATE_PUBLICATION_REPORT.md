# M2a — Create, update, and publication uncertainty

Implemented on committed M1b (`672affd`), with all M2a work left uncommitted.
No commit, tag, publication, release, submodule update, or dependency update was
performed. The initial working tree was clean.

## Behavior and structure

The open vault window now offers Create Token. A selected logical token with one
complete alternative offers Edit Token, including a value carried by multiple
equal heads. Conflicted tokens offer an initially unselected Alternative N combo
and Edit Alternative…; the action enables only after an explicit choice. Detail
text explains before opening that this updates only that alternative and does
not resolve competing alternatives. The editor repeats that explanation. Labels
use M1b's existing presentation order and imply no preference. No complete value
means no edit action.

The controller allows one editor or outstanding uncertainty decision per window.
Create/Edit disable while it is active. Editors are owned modeless dialogs;
Refresh, state delivery and TOTP remain available. Save disables controls before
submitting a single task. Cancel restores write actions without protocol work.

New production classes:

| Class | Responsibility |
| --- | --- |
| `Base32` | Strict dependency-free caller-secret ingress parser. |
| `TokenDraft` | Immutable non-secret descriptor and exclusive, explicit ownership of decoded replacement bytes. |
| `TokenWrites` | One synchronous factory/populate/save/close boundary, called on the session executor. |
| `TokenWriteController` | EDT workflow lifetime and executor-owned retry capability, serialization, result presentation and sticky history. |
| `TokenEditorPanel` | Headlessly testable literal fields, validation, secret clearing and submission. No builders. |
| `TokenEditDialog` | Owned modeless window shell; title-bar close uses panel cancellation. |

Changed production classes: `VaultWindowController`, `VaultView`, `VaultFrame`,
`VaultPanel`, and `TokenBrowserPanel`. They connect the write owner to the existing
session executor and add actions, explicit alternative selection and publication
status. The launcher, state subscriber, core projection/TOTP logic and session
execution architecture were not replaced.

Documentation changed: README and ARCHITECTURE, plus this report. Tests added:
`Base32Test`, `TokenWritesTest`, `TokenWriteControllerTest`, `TokenEditorPanelTest`,
`TokenEditingBrowserTest`, and `PublicationPanelTest`. `NioSmokeTest` gained a new
smoke path. `TestSupport.Window` was made extensible for the M2a fake view; no M1a
or M1b semantic tests were removed or weakened.

## Editor fields and captured bases

Create starts ACTIVE, empty issuer/account, SHA1, six digits, thirty seconds;
secret is required and there is no Create status selector. Update prefills exactly
the selected descriptor and permits ACTIVE/TOMBSTONED (not permanent deletion).
Replace secret is opt-in; disabling it clears input. Without replacement, no
existing secret is obtained and no builder.secret call occurs. Reactivation uses
the selected complete value's retained hidden secret.

The exact VaultState and TokenAlternative are captured when the editor opens.
Later emissions continue to render in the browser without replacing either
reference or the draft. Saving calls exactly `base.update(alternative)`; no
latest-state lookup, head selection, rebasing, merge gate or extra concurrent
parents are introduced. Ordinary update therefore does not resolve the other
semantic alternatives. Historical same-session references intentionally remain
valid. Metadata stays at the public API default: no parent metadata, device,
username, hostname, process identity or timestamp is supplied.

## Secret boundary and ownership

Base32 accepts RFC 4648 A–Z/a–z/2–7, unpadded input, correct terminal padding, and
ASCII space/tab/CR/LF/hyphen grouping. It rejects other characters, Unicode
lookalikes, 0/1 substitutions, embedded or incorrect/excessive padding, impossible
symbol lengths, nonzero unused bits, and decoded sizes outside 1–128 bytes.
It reads char[] directly and never creates a String from secret input.

On Save the form obtains a temporary password char[], decodes and wipes it in
finally. Successful decoded bytes transfer exclusively to TokenDraft; the Swing
field clears. Validation failure submits no operation. Non-secret validation
uses the public descriptor bounds (digits 6–8 and integral seconds
1–4294967295) without invented issuer/account policy or wire-format restrictions.
Cancel, close and Replace-secret-off clear the Swing field. Draft.close explicitly
wipes owned bytes, including factory/setter/save exceptional paths.

On the executor, NewSecret.copyOf makes its defensive copy; desktop bytes are
wiped immediately, before builder.secret. The builder synchronously copies ingress
and the wrapper closes immediately. No decoded bytes or NewSecret wrapper survive
for publication retry. Definite failure leaves the non-secret form editable but
requires secret re-entry when needed. Wiping is only best-effort JVM hygiene.

## Threads and lifecycle

All builder factories, each individual setter, secret ingress, save and close
execute in one task on the existing `totipo-session-N` executor. Thread-recording
public fake CreateToken/UpdateToken implementations assert off-EDT execution and
identical thread identity for every call. Full-controller tests confirm the same
executor thread used by session close. The boundary also rejects EDT invocation.
There is no write executor, SwingWorker or virtual thread.

PublicationRetry is held only in the worker-owned field, never in a Swing form
or EDT callback. Result callbacks carry classifications, not capabilities. Close
marks the workflow closing on EDT, clears and retires the editor, disables actions,
and queues capability cleanup before session close on the same executor. A save
or retry already running is not interrupted. Returned uncertain handles are
installed only in worker state, then released by queued cleanup; late EDT results
cannot reopen UI. Session close remains the final core lifetime backstop.

Latch-driven tests cover unsaved close, close during Saved/Uncertain save, and
close during a retry that produces a successor. They assert that close waits
behind the operation, remains off EDT, and leaves no active editor/retry UI.
No sleep-based race orchestration or graphical CI dependency was added.

## Result mapping

| Result | Handling |
| --- | --- |
| Saved | Retire workflow, restore write actions, show “Token [update] publication acknowledged.” No optimistic browser mutation or claims about sync, newest state, or conflict resolution. |
| Failed(PREPARATION_FAILED) | Explain preparation failure and definite non-publication; preserve editable fields for explicit new submission from the same captured base. |
| Failed(OBSERVATION_UNAVAILABLE) | Explain required local observation unavailable and operation not published; keep editor. |
| Failed(UNRESOLVED_FIELDS) | Explain missing required fields and definite non-publication; keep editor without inventing values. |
| Failed(SESSION_CLOSING) | Start orderly session/window close; no retry prompt. |
| PublicationUncertain | Retire/freeze editor; own exact retry handle and show first-class uncertainty. Never report “Save failed”, “Not saved”, or “Try saving again”. |
| AdditionalConflict | Unexpected create/update invariant failure: close PartialResolution on executor, show generic internal failure; never save it or expose merge choices. |

Pinned public interfaces and API_DESIGN were inspected for all requested types,
including the narrower PartialSaveResult and RetryResult. Pinned implementation
inspection confirms its publication attempt requests local refresh after Saved.
The desktop adds no refresh request and never uses observation as acknowledgement.
Unexpected operation runtime exceptions before a result produce generic internal-operation presentation;
no fake SaveResult, Throwable catch, secret/draft/code logging, or swallowed Error.
Once save returns a result, a later builder-close exception cannot overwrite that
knowledge: Saved remains acknowledged, Failed remains definite non-publication,
and PublicationUncertain remains uncertain. TokenWrites returns the exact result
unchanged and reports only a generic, redacted cleanup error to stderr. Returned
capabilities are independent of the builder, so a builder cleanup failure does
not require discarding them: the controller still owns and handles the retry (or
defensively closes an unexpected partial resolution) through its normal path.

## Publication uncertainty and retries

The UI states that durable acknowledgement could not be determined and that the
operation may already be present. It offers Retry exact publication and Stop
retrying, explains frozen publication without rereading/rebasing, and explains
that another Create is a distinct token which could produce two logical tokens.
Both actions disable during background work. Nothing retries automatically.

An explicit retry takes and clears handle A, calls its blocking retryPublication
on the session executor, and consumes/closes A. Saved finishes without creating
an abandonment notice. PublicationUncertain replaces A with exactly successor B;
B is the sole owned live handle. Tests cover A -> B -> C -> Saved with no handle
reuse, no second builder, no create/update reconstruction, and no plaintext draft.
Unexpected retry exceptions retire the unusable capability and retain uncertainty
history while reporting an internal operation failure.
If retryPublication returned a result before consumed-handle close throws, that
result likewise survives: Saved stays acknowledged and an uncertain successor
remains owned and usable. Cleanup failure is logged generically without discarding
the successor or inventing abandoned uncertainty after a Saved result.

Stop closes/releases the current handle on the executor without retrying. It sets
a sticky non-secret session boolean and restores write actions. The notice reads:
“One or more earlier token publications have unresolved persistence status.”
It persists after Refresh, later observations and unrelated Saved writes. It is
not a shadow VaultState, event log, retained draft or durability inference.
Even when PublicationRetry.close throws during Stop, the desktop drops its handle
reference, sets/retains the sticky unresolved-publication notice and reports a
generic cleanup error. Cleanup failure cannot turn uncertainty into definite
failure or clear earlier uncertainty. The session remains the final core cleanup
backstop for a capability whose close unexpectedly failed.
Observation while a handle is live neither consumes it nor determines success or
failure. The browser always follows emitted VaultState independently.

## Tests and validation

Final suite: **67 tests, 0 failures, 0 errors, 0 skipped**. M1b baseline had 38
tests; M2a adds 29. Existing lifecycle, replay-latest, password, browser, conflict,
literal-text, selection and TOTP interval tests remain passing.

Added coverage includes RFC examples and all requested Base32 rejection/boundary
categories; temporary char-array and decoded-byte wiping; immediate ingress-copy
wiping; closed NewSecret lifetime; factory failure cleanup; defaults/validation;
no replacement ingress when disabled; explicit conflict selection; equal-head edit;
TOTP timer survival; all save outcomes; defensive partial-capability discard;
no automatic retry; successive capability transfers; sticky abandonment through
later state/Refresh/Saved; captured update basis despite newer state; reactivation;
repeated clicks; cancellation; generic runtime errors; and save/retry close races.
Five focused deterministic cleanup regressions cover Saved plus builder.close
failure, PublicationUncertain plus builder.close failure with a still-usable retry,
Failed plus builder.close failure, Stop plus retry.close failure (including a later
Saved write retaining the warning), and uncertain/Saved retry results surviving
consumed-capability close failures. These use the existing public fakes and
executor/EDT event barriers, with no new architecture or feature behavior.

Real NIO coverage creates a temporary vault, creates through TokenWrites, observes
the resulting logical token, updates the captured alternative through TokenWrites
without replacement secret, observes the new descriptor, and generates TOTP.
Caller-owned test arrays are wiped. Existing NIO open/close and TOTP interval
smokes remain. PublicationUncertain uses public fake capabilities, not filesystem
fault manufacture; no upstream protocol corpus was duplicated.

| Validation | Result |
| --- | --- |
| `git submodule status` | Exact pin `3d97dca72604f39b6b475c0d5a0a8b076632cc42`. |
| `./gradlew clean test build` | Passed; tests, classfile verification and distributions built. |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | Passed; 67 tests, all tasks forced. |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | Passed; 67 tests, all tasks forced offline. |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | Passed; unchanged production inventory. |
| `git diff --check` | Passed. |
| Java 17 classfile verification | Passed for all 34 production classfiles, major version 61, no preview bytecode. |
| Strict dependency verification/locking | Active and unchanged; no lock/verification metadata churn. |
| Submodule cleanliness and gitlink diff | Clean; no gitlink movement. |
| Nix build | Unavailable: no `nix` executable in this environment. JDK 25 is supplied from the existing Nix store. |
| Graphical smoke | Unavailable: no graphical display. All panel/controller tests run headlessly; actual top-level window interaction remains a manual validation limit. |

Early development runs caught a missing integration edit, a test-harness lint
warning, and a browser test selecting a scrollbar button; these were corrected.
The final commands above pass. No build configuration was relaxed.

Production inventory before and after: Java 17 API surface, Swing/AWT,
java.time/concurrency, pinned storage-nio, pinned core, and core-owned Bouncy Castle
`bcprov-jdk18on:1.86`. Zero added production or test dependencies. Gradle 9.8.0
wrapper/checksum, JDK 25, --release 17, UTF-8, -Xlint:all, -Werror, strict locking,
strict verification and the full-JDK Nix shell are unchanged.

## Production source audit

Reviewed the complete new write path and diff, plus focused searches for factories,
setters, save/retry, subscriptions, executors, secret conversions and restricted APIs:

- No live builder in Swing state; factory/setters/save/close stay in one executor task.
- Exact captured update receiver/alternative; no latest-state substitution or head update.
- No MergeToken use, merge call, PartialResolution.save, merge UX or winner selection.
- No automatic retry, second retry builder or reconstruction from semantic fields.
- Uncertainty remains distinct from failure; observations never clear it.
- No secret-to-String conversion or existing-vault-secret extraction.
- No metadata setter, implicit identity/time policy, or browser mutation after Saved.
- Still exactly one production state subscription and the original two executor roles
  (application lifecycle, and one executor per session).
- Only public dev.totipo application APIs and NioTotipo; no SPI/internal imports.
- No blocking save/retry on EDT, extra production dependency or graphical CI requirement.

No material implementation scope deviations. Graphical and Nix validation are the
unavailable checks noted above. Failed submissions intentionally require re-entry
of secret ingress because the submitted plaintext has already been wiped.

## Review working tree

The following snapshots include tracked changes and untracked M2a files. Ordinary
`git diff --stat` excludes untracked files; the status listing records those additions.

```text
$ git status --short
 M ARCHITECTURE.md
 M README.md
 M src/main/java/dev/totipo/desktop/VaultWindowController.java
 M src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultFrame.java
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
 M src/main/java/dev/totipo/desktop/ui/VaultView.java
 M src/test/java/dev/totipo/desktop/NioSmokeTest.java
 M src/test/java/dev/totipo/desktop/TestSupport.java
?? review/M2A_CREATE_UPDATE_PUBLICATION_REPORT.md
?? src/main/java/dev/totipo/desktop/Base32.java
?? src/main/java/dev/totipo/desktop/TokenDraft.java
?? src/main/java/dev/totipo/desktop/TokenWriteController.java
?? src/main/java/dev/totipo/desktop/TokenWrites.java
?? src/main/java/dev/totipo/desktop/ui/TokenEditDialog.java
?? src/main/java/dev/totipo/desktop/ui/TokenEditorPanel.java
?? src/test/java/dev/totipo/desktop/Base32Test.java
?? src/test/java/dev/totipo/desktop/TokenWriteControllerTest.java
?? src/test/java/dev/totipo/desktop/TokenWritesTest.java
?? src/test/java/dev/totipo/desktop/ui/PublicationPanelTest.java
?? src/test/java/dev/totipo/desktop/ui/TokenEditingBrowserTest.java
?? src/test/java/dev/totipo/desktop/ui/TokenEditorPanelTest.java

$ git diff --stat
 ARCHITECTURE.md                                    | 147 ++++++++++++++++++---
 README.md                                          |  27 ++--
 .../dev/totipo/desktop/VaultWindowController.java  |   5 +
 .../dev/totipo/desktop/ui/TokenBrowserPanel.java   |  48 +++++++
 .../java/dev/totipo/desktop/ui/VaultFrame.java     |  13 ++
 .../java/dev/totipo/desktop/ui/VaultPanel.java     |  50 ++++++-
 src/main/java/dev/totipo/desktop/ui/VaultView.java |  11 ++
 src/test/java/dev/totipo/desktop/NioSmokeTest.java |  44 ++++++
 src/test/java/dev/totipo/desktop/TestSupport.java  |   2 +-
 9 files changed, 321 insertions(+), 26 deletions(-)
```

## Readiness for M2b

Ready for review as the foundation for **M2b: merge/conflict-resolution UX +
AdditionalConflict + PartialResolution**. M2a establishes draft ownership,
thread confinement and exact-publication lifecycle without implementing merge
or conflict resolution. M2b must deliberately add its own selection, unresolved
field and partial-resolution semantics; ordinary M2a editing must retain the
captured-alternative behavior. Graphical smoke testing remains advisable before
accepting the UI milestone.
