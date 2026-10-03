# U1.2 — remembered-vault password dialog actions

## Baseline and review scope

Baseline commit (committed U1.1): `7d208fda82fedccbcb9399ee823cb6efc0360963`.
Before editing, `git status --short` was empty. Baseline
`./gradlew clean test build` passed, including `verifyMavenBoundary`, Java 17
bytecode verification, qualification compilation, and distribution verification.
Baseline: **209 tests**, zero failures, errors, or skips. The baseline printed
the existing nonfatal fontconfig configuration warning.

After: **222 tests**, zero failures, errors, or skips (**+13**).
The wording-context follow-up began with 221 tests and adds one focused routing test.
All changes are uncommitted. Nothing was published, tagged, or released.

## Password dialog and actions

Open Vault keeps its existing title, read-only selectable full normalized path,
password field, spacing, and trailing action layout. Automatic remembered-vault
startup uses **Welcome back. Enter the password for:** above the distinct path
field, followed by the Password label/input and the three actions:

```text
Open Vault

Welcome back. Enter the password for:
/absolute/path/to/vault

Password
[ ........ ]

[ Exit ] [ Change Vault… ] [ Open ]
```

Explicit Open Existing Vault, Change Vault alternate selection, and other manual
Open flows use **Enter the password for:** in the same label position. This remains
true when the user manually selects the remembered path. Create wording remains
**Create a vault in:**.

`PasswordPromptContext` carries the presentation origin through the launcher
boundary into the password form. Only automatic remembered startup supplies
`REMEMBERED_STARTUP`; manual entry points supply `EXPLICIT`, and Change Vault
switches subsequent prompts to `EXPLICIT`. Swing selects wording from that context
without examining preferences or inferring anything from the vault path. The
context has no effect on actions, selection, persistence, or shutdown behavior.

The hierarchy adds no extra Vault label, Totipo heading, or explanatory paragraph.
The path stays selectable, non-editable, and separate from the introductory label,
which also supplies its accessible name. No layout changes accompany the context
correction.

Change Vault uses the same Unicode ellipsis as the existing menus and launcher.
The three buttons retain Swing keyboard traversal and accessible names from
their labels. No new mnemonics were introduced. Open remains the root default
button; Enter in the password field submits. The window-open listener still
requests initial focus in the password field.

`PasswordPromptResult` distinguishes submission, Exit, Change Vault, and Create
cancellation at the existing launcher presentation boundary. The dialog content
and action/window/keyboard wiring live in a headless-testable `PasswordPrompt.Form`
used by the production modal dialog. It accepts only the first action and
disposes the prompt before control returns to the application.

| Action | Exact behavior |
| --- | --- |
| Open | Returns the entered password to the existing application validation/open path. Password validation, asynchronous vault access, failure messages, successful window startup, and password ownership/clearing are unchanged. |
| Exit | Retires the prompt and calls `DesktopApplication.shutdown`, the same logical quit method installed for File → Exit and main-window close. The outstanding prompt reservation is released in its existing finally block. Existing session/executor cleanup and launcher disposal run normally. No launcher appears, no vault operation starts, and the remembered preference is untouched. |
| Change Vault… | Retires the prompt, clears its fields, and opens the existing explicit directory chooser with the displayed vault as the location hint. The application keeps the prompt operation reserved across chooser/password steps, preventing reentrant launcher actions. It can repeat selection without nesting password prompts. No open starts for abandoned attempts and no preference is written. |

All Open Vault password prompts use these actions, including remembered startup,
launcher Open, and alternate selection from a live vault. Exit always quits the
application, including when another vault window is open. The shared Create
Vault form retains **Cancel / Create**, its confirmation field, mismatch
handling, and empty-password confirmation; it has no Exit or Change Vault action.
No other dialog was redesigned.

## Window-close and Escape semantics

For Open Vault, both **Escape and the dialog window close control mean Exit**.
The dialog uses `DO_NOTHING_ON_CLOSE` with its explicit window-closing listener;
the listener and Escape binding select the same Exit decision as the button and
dispose the prompt. They cannot fall through to launcher cancellation. Repeated
close/action events cannot replace the selected decision or dispose it twice.
Create Vault retains cancellation for Escape and window-close.

No `System.exit` is used. Tests exercise normal shutdown through the existing
lifecycle boundaries without terminating the test JVM. Shutdown waits for owned
session cleanup and pending operations before shutting down the application
executor and disposing the launcher, allowing natural process termination.

## Change Vault and remembered-vault preservation

The existing chooser receives the displayed/current vault location. Its existing
parent/preselection behavior, layout, and fallback are unchanged. Cancelling the
chooser with no open vault returns to the launcher and leaves the application
usable. With an existing vault open, chooser cancellation or failed alternate
open retains that window/session. Exiting the next Open Vault password prompt
quits through normal cleanup and still retains the previous preference.

The preference is updated only by the existing successful controller-startup
path. A successful alternate open remembers its normalized path; failed open,
chooser cancellation, password Exit, and Change Vault itself never overwrite or
clear the previous remembered vault. Stale remembered-path clearing/warning and
first-run launcher behavior are unchanged. The preference store implementation
and keys are unchanged.

## Tests added and updated

- `PasswordPromptTest`: five new deterministic tests cover ordered accessible
  Exit / Change Vault… / Open buttons, default Open, Enter submission, equivalent
  Exit/Escape/window-close decisions, one-time retirement and field clearing,
  distinct Change Vault behavior, and preserved Create actions/confirmation and
  cancellation bindings. The tests invoke the production content, Escape action,
  and window listener without constructing top-level windows.
  The existing path/password test checks both exact introductory strings for the
  supplied origin, their placement directly above the distinct path field, and
  the corresponding accessible path name; the Create test verifies its unchanged
  introductory wording.
- `RememberedVaultTest`: seven new tests cover Change Vault chooser cancellation,
  subsequent password Exit, successful alternate open, failed alternate open,
  repeated selection with reentrant launcher actions, shutdown during the
  chooser, and explicit Open of the remembered path. Context assertions prove
  automatic startup supplies `REMEMBERED_STARTUP`, explicit Open supplies
  `EXPLICIT`, and Change Vault alternate prompts use `EXPLICIT` both after startup
  and from a live window. The former startup cancellation test now verifies Exit,
  executor shutdown, one launcher disposal, no launcher display, and no preference write.
  Existing startup Open, stale-path handling, normalized preference, failure,
  live-vault replacement, and shutdown tests remain green. The live-vault chooser
  cancellation/failure test retains session and preference assertions; password
  dismissal now has the explicit Exit semantics covered separately.
- `ApplicationQuitTest`: one new test runs password Exit with an existing session
  through the same cleanup assertions used for File → Exit and main-window close:
  exactly one session close/subscription cancellation/window disposal/launcher
  disposal, executor shutdown after asynchronous cleanup, idempotent repeat quit,
  and no launcher redisplay.
- `DesktopApplicationTest`: the cancellation-to-ready test now uses directory
  cancellation and Create password cancellation, matching the remaining Cancel
  flow, and checks that Create uses the explicit presentation context. Existing
  nested-dialog shutdown still verifies password-array clearing.
- The existing launcher test fixture now returns explicit prompt decisions while
  retaining caller-owned password arrays and lifecycle latches, and records the
  supplied presentation contexts.

These checks establish action wiring and lifecycle behavior in headless tests.
Actual desktop focus, window decorations, and process termination remain manual
operator checks below.

## Validation

| Command | Result |
| --- | --- |
| Baseline `./gradlew clean test build` | PASS; 209 tests |
| Final `./gradlew clean test build` | PASS; 222 tests; Maven boundary, Java 17 bytecode, qualification, and distribution checks passed |
| `./gradlew --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 222 tests; all tasks rerun without build cache |
| `./gradlew --offline --no-daemon --no-build-cache --rerun-tasks clean test` | PASS; 222 tests; all tasks rerun offline without build cache |
| `./gradlew installDist verifyDistribution verifyDistributionArchives` | PASS; runtime inventory, notices, launch scripts, Java 17 classes, ZIP/TAR contents verified |
| `git diff --check` | PASS; rechecked after report completion |

The final validation results include the wording-context correction.

An intermediate compile caught serialization lint warnings on UI callbacks;
those fields are now transient and the final full build passed with `-Werror`.

No protocol/storage semantics, remembered-vault persistence rules, token,
clipboard, or conflict semantics changed. No Totipo Java dependency, Maven/Nix
architecture, build file, lockfile, dependency verification metadata,
`package-deps.json`, or Nix file changed. No dependency was added and no Nix
dependency cache was regenerated. Production changes are confined to password
dialog presentation, the prompt decision boundary, and the application prompt routing. The launcher,
chooser, main window, token browser/detail area, and other dialogs have no layout
changes. Create Vault changes only through minimal shared prompt plumbing.

## Operator checklist — manual execution pending

Use disposable vaults when exercising startup and alternate selection.

1. [ ] Startup with remembered vault shows Exit / Change Vault… / Open.
2. [ ] Open still unlocks the displayed vault.
3. [ ] Exit quits the application.
4. [ ] Dialog close button quits the application.
5. [ ] Escape follows the documented quit behavior.
6. [ ] Change Vault… opens the alternate-vault flow.
7. [ ] Cancelling Change Vault… does not corrupt the remembered vault.
8. [ ] Successfully opening another vault updates the remembered vault.
9. [ ] No launcher appears when choosing Exit from the remembered-vault prompt.

## Review state

Final `git status --short` and `git diff --stat` are recorded below after validation.
The standard diff stat excludes untracked files, which are listed in status.

```text
$ git status --short
 M src/main/java/org/totipo/desktop/DesktopApplication.java
 M src/main/java/org/totipo/desktop/ui/LauncherFrame.java
 M src/main/java/org/totipo/desktop/ui/LauncherView.java
 M src/main/java/org/totipo/desktop/ui/PasswordPrompt.java
 M src/test/java/org/totipo/desktop/ApplicationQuitTest.java
 M src/test/java/org/totipo/desktop/DesktopApplicationTest.java
 M src/test/java/org/totipo/desktop/RememberedVaultTest.java
 M src/test/java/org/totipo/desktop/TestSupport.java
 M src/test/java/org/totipo/desktop/ui/PasswordPromptTest.java
?? review/U1_2_PASSWORD_DIALOG_ACTIONS_REPORT.md
?? src/main/java/org/totipo/desktop/ui/PasswordPromptContext.java
?? src/main/java/org/totipo/desktop/ui/PasswordPromptResult.java

$ git diff --stat
 .../org/totipo/desktop/DesktopApplication.java     |  34 +++--
 .../java/org/totipo/desktop/ui/LauncherFrame.java  |   4 +-
 .../java/org/totipo/desktop/ui/LauncherView.java   |   4 +-
 .../java/org/totipo/desktop/ui/PasswordPrompt.java |  99 +++++++++----
 .../org/totipo/desktop/ApplicationQuitTest.java    |  11 +-
 .../org/totipo/desktop/DesktopApplicationTest.java |   7 +-
 .../org/totipo/desktop/RememberedVaultTest.java    | 158 +++++++++++++++++++--
 src/test/java/org/totipo/desktop/TestSupport.java  |  12 +-
 .../org/totipo/desktop/ui/PasswordPromptTest.java  | 120 ++++++++++++++--
 9 files changed, 384 insertions(+), 65 deletions(-)
```
