# M1b read-only token browser review

Implemented on committed M1a (`7788d73`). All M1b changes are left uncommitted and unstaged. No tag, publication, or release was created.

## Behavior and structure

The existing observation/status area, manual Refresh, current-state diagnostic codes, and application/session lifecycle remain intact. A horizontal split adds a keyboard-selectable logical-token list and selected-token detail; diagnostics remain below it. Finished remains “Local observation finished”, without synchronization claims.

Production files:

- Added `src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java`: Swing list/detail split, latest-state projection, TokenId selection, code widgets, and close cleanup.
- Added `src/main/java/dev/totipo/desktop/ui/TokenPresentation.java`: immutable row record and plain-text descriptor, competition, and technical-detail projection.
- Added `src/main/java/dev/totipo/desktop/ui/TotpDisplay.java`: EDT-owned ephemeral TOTP state, injected Clock, deterministic tick operation, and coalescing Swing timer.
- Changed `src/main/java/dev/totipo/desktop/ui/VaultPanel.java`: embeds the browser, sends it the same emitted state, and closes it before the existing session-close path proceeds.

`VaultWindowController`, `StateSubscriber`, application ownership, password handling, and session executors are unchanged. There is exactly one production `VaultSession.states()` subscription per window. No token-rendering subscription, domain database, event bus, worker, or framework was added.

`TokenBrowserPanel` rebuilds exactly one row for each `VaultState.tokens()` entry, in supplied order. Rows use `TokenCompetition` issuer/account values: one value is literal text, multiple values are marked conflicting, and absent values have unavailable placeholders. No arbitrary alternative is used as a list winner. Incomplete logical tokens remain visible with a shortened ID. Conflict comes exclusively from `hasConflict()`; unresolved and tombstoned indicators are textual.

Selection is retained as the full `TokenId`, never an index or old token object. Each emission replaces the list, locates that ID, resolves the current `TokenState` through the latest state, and rebuilds detail/TOTP. Reordering preserves identity; disappearance clears selection and detail. Initial selection is empty, with “Select a token to view details.”

Zero complete alternatives produce an explicit incomplete-observation explanation, field-unavailable text, and technical details, without TOTP generation. One alternative shows its complete non-secret descriptor. Multiple equal heads with one alternative explicitly state that the heads carry the same token value, without a conflict or merge claim. Multiple semantic alternatives are all retained, including identical visible descriptors and tombstones. Numeric presentation labels (“Alternative 1”, etc.) follow core order and explicitly do not indicate preference.

For each alternative, detail shows status, issuer, account, algorithm, digits, period, and exact carrying-head revision IDs. Field competition uses the core's `CompetingField` values and alternative memberships for all six fields. Zero values are unavailable; one is the agreed value; multiple values are all shown with membership labels. Secret presentation uses only `CompetingSecret`/`SecretGroup`: count and memberships, never secret bytes or inferred equality. Equal current code digits never collapse alternatives.

Tombstoned-only tokens have a tombstone row indicator and descriptor, with no live code. Mixed active/tombstoned conflicts retain both statuses and generate only the active code. Unresolved references have an obvious list indicator, a detail count, and exact child/parent revision hex IDs. They are not described as corruption, synchronization failure, data loss, or semantic conflict.

Technical heads include full revision IDs, optional literal client names, and optional client-provided raw unsigned time using `Long.toUnsignedString`. Absent metadata is omitted. Metadata is never used for ordering, freshness, or wall-clock conversion.

## TOTP lifetime and timing

`TotpDisplay` runs exclusively on the EDT. Production injects `Clock.systemUTC()`; tests supply a mutable Clock. A 250 ms `javax.swing.Timer` with coalescing enabled invokes the same package-private `tick()` operation used by deterministic tests. No background TOTP executor or tick-count timekeeping exists.

Selection/state changes first stop and clear the prior presentation. Each selected ACTIVE semantic alternative receives one `VaultState.generateTotp(alternative, now)` call, regardless of head count. Only the current state's alternatives and current ephemeral code objects are retained. Tombstones and empty observations receive none.

Every tick reads the clock. Codes are regenerated only when `now < validFrom` or `now >= validUntil`, using the current instant directly. The interval is exactly half-open `[validFrom, validUntil)`. Forward jumps skip missed intervals; backward jumps before validFrom regenerate. Valid ticks update countdown/progress while leaving unchanged code labels alone. Countdown and the normalized 0–1000 bar derive solely from the returned interval, never descriptor period or modulo arithmetic. Core code strings retain leading zeros.

Unexpected generation/runtime interval failures are caught as RuntimeException, clear the affected code, and show only “Code unavailable”. Failed entries do not retry on ticks; the timer stops if no live entries remain. Other live alternatives can continue. A subsequent state or selection can retry. No exception message, code, or secret enters diagnostics or logs, and failure does not close the vault.

`VaultWindowController.close()` already calls `view.closing()` before scheduling blocking session close off the EDT. That path now disables token interaction, stops the timer, clears visible code labels (including detached labels), drops code/state references, and rejects subsequent rendering. Queued ticks after clearing cannot generate. Terminal publisher events use this same existing close path. Immutable String secure erasure is not claimed.

## Literal text and accessibility

The list's `DefaultListCellRenderer` explicitly sets `html.disable` before rendering stored text. Descriptors and client metadata use a noneditable `JTextArea`, which does not interpret HTML. Code labels also disable HTML. No HTML markup is constructed from token text. Tests use issuer/account values beginning with `<html>` and verify literal strings plus absence of Swing's HTML renderer property. Client names are tested with the same prefix.

The ordinary JList supports keyboard selection through normal selection events. List/detail have accessible names; focus remains standard Swing traversal. State indicators use words, not color alone. No top-level window, Robot, screenshot, X11, or Wayland is required by tests.

## Tests

10 new test methods; 38 total tests passed, zero failed, zero skipped (28 retained M1a tests).

- Added `src/test/java/dev/totipo/desktop/ui/TokenFixtures.java`: public-interface fakes, explicit test projection data, controllable Clock, and recorded generation calls. Forbidden APIs fail immediately. Descriptor grouping is test fixture construction only.
- Added `TokenBrowserTest.java` (5 tests): row count/order; literal issuer/account/client text; empty selection; TokenId preservation across reordered and replaced states; disappearance; equal heads; all six competing fields and memberships; secret-only conflict; one/multiple secret groups; incomplete/unresolved states; raw unsigned negative-long metadata; browser close cleanup.
- Added `TotpDisplayTest.java` (4 tests): immediate generation, no in-interval regeneration, countdown based on deliberately non-period-aligned fake intervals, instant before/exact expiry, forward/backward jumps, leading zeros, equal-head single generation, independent equal-digit alternatives, tombstones/status-only conflict, incomplete state, replacement, generic failures without retry, retry after reselection, stop/clear, and late ticks after clear.
- Extended `NioSmokeTest.java` (1 test): creates a temporary real vault and a token strictly in test setup via public API, observes it, calls real-core generateTotp, verifies containment, immediately-before-expiry stability and adjacent interval rollover, and closes resources. This supplements deterministic tests without reproducing RFC vectors. No production mutation API was introduced.
- Adjusted `TestSupport.java` empty states to return an empty token list, and `VaultPanelTest.java` to locate diagnostics independently of the new detail text area. Existing observation and lifecycle assertions continue passing.

## Validation and dependency audit

Completed in the supplied Nix-packaged full OpenJDK 25.0.4.1 environment:

| Command/check | Result |
| --- | --- |
| `git submodule status` | Pin matches; no dirty/mismatch prefix |
| `./gradlew clean test build` | Passed, including real-core integration and bytecode verification |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | Passed, 38 tests; 9 executed tasks |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | Passed, 38 tests; 9 executed tasks |
| `./gradlew --offline dependencies --configuration runtimeClasspath` | Passed; unchanged runtime inventory |
| `git diff --check` | Passed |
| `verifyJava17Bytecode` | Passed on all 24 desktop production classes; version 61, no preview bytecode |
| Submodule status and gitlink comparison | Clean; both HEAD and gitlink remain `3d97dca72604f39b6b475c0d5a0a8b076632cc42` |
| Build/dependency hardening diff | No changes to build, lockfiles, verification metadata, wrapper, Nix files, or pin documentation |

The first development compile caught serialization lint warnings on retained nonserializable presentation state; those fields are now transient. Subsequent builds pass `-Xlint:all -Werror`. A development test run emitted a fontconfig configuration warning but passed; tests remain headless.

Production dependency inventory before and after is identical: direct `dev.totipo:storage-nio`, pinned composite-build `dev.totipo:core`, and core-owned transitive `org.bouncycastle:bcprov-jdk18on:1.86`. Zero new production dependencies. Strict dependency locking and verification, Gradle wrapper/version/checksum, JDK 25 compilation, `--release 17`, and UTF-8 remain unchanged.

Source audit confirms:

- No generic `alternatives().get(0)` winner; no head-count conflict inference.
- One production state subscription, in the existing window controller.
- TOTP only through the public `VaultState.generateTotp` call; no HMAC/counter implementation or period-derived expiry.
- No secret extraction, comparison, hashing, encoding, export, or code-based secret equality.
- No token create/update/merge/editor/save, PublicationRetry, or PartialResolution calls in production.
- No background TOTP executor, TOTP logging/history/title/diagnostic persistence, or clipboard access.
- No stored token text embedded in Swing HTML.
- No storage SPI/internal API, System.exit, new store operations on the EDT, or graphical CI requirement.
- M1a blocking open/create/close execution remains unchanged; timer cleanup occurs before off-EDT session close.

Nix executable is unavailable (`command -v nix` found none), so `nix develop` could not be run despite the installed Nix-packaged JDK. DISPLAY and WAYLAND_DISPLAY are unset, so the requested interactive graphical smoke scenarios could not be performed. Deterministic headless tests cover the specified semantic cases and timing boundaries. Material deviations: only these unavailable environment validations; no scope or dependency deviations.

## Review worktree

`git status --short` (including this report):

```text
 M src/main/java/dev/totipo/desktop/ui/VaultPanel.java
 M src/test/java/dev/totipo/desktop/NioSmokeTest.java
 M src/test/java/dev/totipo/desktop/TestSupport.java
 M src/test/java/dev/totipo/desktop/ui/VaultPanelTest.java
?? review/M1B_READ_ONLY_TOKEN_REPORT.md
?? src/main/java/dev/totipo/desktop/ui/TokenBrowserPanel.java
?? src/main/java/dev/totipo/desktop/ui/TokenPresentation.java
?? src/main/java/dev/totipo/desktop/ui/TotpDisplay.java
?? src/test/java/dev/totipo/desktop/ui/TokenBrowserTest.java
?? src/test/java/dev/totipo/desktop/ui/TokenFixtures.java
?? src/test/java/dev/totipo/desktop/ui/TotpDisplayTest.java
```

`git diff --stat` (Git excludes untracked additions from this command):

```text
 .../java/dev/totipo/desktop/ui/VaultPanel.java     | 15 +++++---
 src/test/java/dev/totipo/desktop/NioSmokeTest.java | 42 ++++++++++++++++++++++
 src/test/java/dev/totipo/desktop/TestSupport.java  |  1 +
 .../java/dev/totipo/desktop/ui/VaultPanelTest.java |  5 +--
 4 files changed, 57 insertions(+), 6 deletions(-)
```

The six new Java files add 700 lines, in addition to this report. Nothing is staged or committed. No token mutation or new persistence behavior was implemented in production.

## Next milestone assessment

Ready for review as the read-only foundation for **M2a: create/update editors + save-result and PublicationUncertain handling**. The authoritative-state, identity-preserving selection, semantic conflict, and timer boundaries are established and tested. M2a should retain those boundaries and deliberately add editor ownership and save-result lifetimes; it must not reuse presentation labels, head counts, or TOTP digits as semantic authority. A graphical smoke pass remains advisable when a display is available.
