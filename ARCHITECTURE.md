# Desktop architecture

M1a implements local NIO vault open/create, session/window lifecycle, local
observation progress and diagnostic codes, and manual refresh. The single-project
Swing bootstrap and pinned composite dependency from M0 remain intact. Production
code consumes only `dev.totipo.*` and `dev.totipo.storage.nio.NioTotipo`, never the
storage SPI or implementation internals. See [the dependency pin](TOTIPO_JAVA_PIN.md).

## Ownership

```text
TotipoDesktop: invokeLater
  -> DesktopApplication (EDT ownership)
       -> LauncherFrame / LauncherPanel
       -> one serialized application lifecycle executor
       -> zero or more VaultWindowController
            -> one VaultSession
            -> one VaultFrame / VaultPanel
            -> one StateSubscriber / states() subscription
            -> one serialized session executor
            -> at most the latest immutable VaultState reference
```

There is no global current vault. Independent windows have independent controllers,
subscriptions and executors, even if the user selects the same directory twice.
`DesktopApplication` owns the launcher and tracks controllers until session close
finishes. Frames render and emit actions; they do not own protocol behavior.
`LauncherView` and `VaultView` are small presentation interfaces for testing these
owners without constructing top-level windows. They are not a general UI framework.

## Threading and entry points

All ownership transitions, user actions and Swing changes occur on the EDT. The
small `Edt` guard checks those boundaries. `TotipoDesktop` only schedules creation
of `DesktopApplication` and shows its launcher.

One ordinary JDK single-thread executor, named `totipo-application`, runs blocking
open/create and closes returned sessions that cannot be transferred to a controller.
The package-private `VaultAccess` seam has only `open(Path, char[])` and
`create(Path, char[])`; `NioVaultAccess` delegates directly to `NioTotipo`. Tests
substitute lifecycle outcomes without KDF or filesystem work. No protocol or
storage-provider behavior is reproduced in this seam.

Each controller owns a separate single-thread executor named `totipo-session-N`.
M1a uses it only for blocking session close. Future blocking mutations will use
this same serialized boundary. Open, create and close never run on the EDT.
Executors are explicitly shut down; no shutdown interrupts an in-flight operation.
The application uses neither SwingWorker nor virtual threads nor reactive libraries.
Production code remains compatible with Java 17.

## Launcher and passwords

Open and Create choose an existing directory with Swing's directory chooser.
Desktop code does not create directories, recursively inspect them, or canonicalize
paths. The NIO result is authoritative. Only one launcher operation is accepted at
a time; both buttons stay disabled across prompts, work, result presentation and
unclaimed-session cleanup. Modal dialogs can run nested EDT loops, so shutdown is
checked after prompting as well as when the background result reaches the EDT.

Passwords come from `JPasswordField.getPassword()` as desktop-owned `char[]` values.
No password is converted to String, logged, or included in presentation/diagnostics.
Create compares password and confirmation arrays directly. Confirmation is wiped
immediately after comparison; a mismatch also wipes the primary array. Fields are
cleared when dismissed. Cancellation/rejection wipes any retrieved primary array.

`PasswordInput` validates UTF-16 and counts UTF-8 bytes without an encoded copy.
It rejects unpaired surrogates and more than 1024 UTF-8 bytes. Empty input and all
other valid Unicode within the limit are accepted; there is no extra password policy.
Once accepted for background work, the operation owns the primary array and wipes
it in `finally`, whether NIO returns a session, another outcome, or throws. This is
best-effort JVM secret hygiene, not a secure-erasure guarantee. No passwords are
remembered for retries or subsequent Open actions. Unexpected exceptions use generic
application/session failures; exception text and stack traces are not displayed or
logged because they could contain caller-controlled data.

## Lifecycle outcomes

Every public open/create result has distinct handling:

| Result | Desktop behavior |
| --- | --- |
| Opened / Created | Transfer the session once to a registered controller, then start its window/subscription. If construction fails before transfer or shutdown has begun, close the session on the application executor. After transfer, the controller owns all cleanup, including start/subscription failure. |
| Absent | No canonical Totipo vault was observed; never implicitly create. |
| Unavailable | Directory/vault could not be reliably accessed; no password/corruption diagnosis. |
| InvalidVault | Observed vault data could not be used as a valid vault; no repair/replacement. |
| AuthenticationFailed | Authentication did not succeed; this does not prove the password is incorrect, and authenticated data may have changed or become unusable. |
| AlreadyExists | A vault was observed and nothing was overwritten; Open remains an explicit later action. |
| Failed | Definite failure of this create operation; no automatic retry. |
| Uncertain | Creation may have succeeded; canonical outcome cannot be asserted. Do not blindly retry; explicitly use Open Vault to re-observe. |

Unexpected runtime exceptions are application failures, never converted into a
protocol outcome. `Error` is not caught or swallowed. There is no automatic open,
create, observation or lifecycle retry.

## State and replay-latest delivery

`VaultState` is authoritative immutable application state. A controller retains
only its latest received reference, clears it on close, and does not maintain a
shadow vault model or state history. Swing rendering models are rebuilt from the
current state, not merged into a desktop protocol representation.

`StateSubscriber` requests exactly one item on subscription. `onNext` enqueues an
EDT runnable, which stores/renders the state only while the controller remains
active. Only after rendering does it request one further item. Demand therefore
stays zero while the EDT is pending, allowing the core's replay-latest coalescing
to discard intermediate observations. There is no desktop state queue or unbounded
demand. Cancellation drops the subscription reference and suppresses pending UI
application. Terminal callbacks are also handed to the EDT and handled once.

A stream error marks the session unusable, disables interaction, presents a generic
session failure distinct from observation diagnostics, and begins close. Unexpected
completion retires the session/window cleanly. Explicit close cancels first, so
completion cannot start another close lifecycle. Rendering/runtime failures use
the same terminal cleanup path.

## Local observation presentation

`VaultPanel` reads only `observation()` and `diagnostics()`:

- Enumerating: “Observing local vault — discovered N objects”, with an indeterminate
  bar. The count is not described as final.
- Processing: “Processing local observation — X of Y”, with exact long counts.
  Only the visual bar is scaled to 0–1000 to avoid Swing integer-range overflow.
- Finished: “Local observation finished — N objects processed”, optionally followed
  by “diagnostics present”. This means only that the local pass ended.

The current diagnostic codes replace the previous text, preserving order and
repeated entries. Codes receive no invented causal explanations and are not a
persistent event log. Neither progress nor diagnostics establish freshness,
complete history, remote synchronization, rollback resistance or absence of conflicts.

Refresh invokes `VaultSession.requestRefresh()` directly on the EDT because the
API guarantees a non-blocking request. It is disabled during close. There is no
polling, filesystem watcher, automatic refresh loop or remote sync behavior.

## Close and shutdown

Both frames use `DO_NOTHING_ON_CLOSE`; owners control disposal. A window close
request idempotently marks its controller closing, cancels the subscriber, drops
the latest reference, disables Refresh and displays “Closing…”. Its session executor
calls `session.close()`. After that call finishes, the executor is shut down and an
EDT callback disposes the frame and removes the controller from the application.
A runtime close failure is reported generically and still retires the executor and
window, without retry or a claim that unexpected core cleanup failure was repaired.
No frame disposal substitutes for session close.

Launcher close marks the application shutting down, disables Open/Create and asks
every live controller to close. No new vault window can be created thereafter.
An already-running open/create is allowed to finish without interruption. A session
returned after shutdown is explicitly closed on the application executor; passwords
are still wiped normally. The launcher remains owned until no launcher operation
(including prompts, result presentation or unclaimed cleanup) is pending and all
controllers have reported closed. Then the application executor shuts down and the
launcher is disposed. The EDT never waits for background work. There is no
`System.exit()` shortcut.

## Preserved principles and later work

1. Protocol/application state belongs to `VaultSession` and `VaultState`.
2. Presentation state includes selection, window state and dialogs.
3. Future operation state includes editor drafts and outstanding result capabilities.

Future create/update/merge builders are thread-confined. Forms will own desktop
drafts; each builder will be created, populated, saved and closed in one background
operation. None of this editing behavior exists in M1a.

The desktop must not collapse conflicts into arbitrary winners, partial observation
into completeness, uncertainty into success/failure, local publication into remote
synchronization, or equal causal heads into semantic conflict. No automatic merge,
retry or invented synchronization semantics are introduced.

Token list/detail/search, TOTP display/timing and all token mutations remain outside
M1a, as do password change, Base32/QR/import/export, clipboard, keychain, remembered
passwords, recent history, tray, shortcuts, theming, watchers, remote providers,
installers and release publishing.

## Verification

Headless tests construct panels and drive lifecycle owners on the EDT, using narrow
fake views/access/sessions and controlled publishers/latches. They test lifecycle
outcomes, password validation/wiping, subscriber backpressure, terminal/cancel
races, observation-only rendering, refresh, independent session close and shutdown
during open/create. Fake states reject every token/editing/TOTP method. One small
real-NIO temporary-directory test covers create/close/open/close. Build/test also
verify every production class is Java 17 classfile version 61. No new production or
test dependencies were needed.
