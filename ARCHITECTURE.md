# Desktop architecture

M0 is a single-project Swing application and deliberately non-functional shell.
The launcher has disabled Open Vault and Create Vault buttons. The only
production dependency is the pinned Totipo Java `storage-nio` composite project,
which exposes `core` and its Bouncy Castle runtime dependency. Application code
will use the public application API and `NioTotipo`, never the storage SPI in
ordinary application work. See [the dependency pin](TOTIPO_JAVA_PIN.md).

## Threading

Swing widgets and presentation state belong to the EDT. The entry point
schedules UI creation using `SwingUtilities.invokeLater`. The small `Edt`
helper rejects off-EDT construction of launcher content.

Potentially blocking Totipo work must never execute on the EDT.
`VaultSession.states()` callbacks are not assumed to run on the EDT.
Published states must eventually be handed to Swing through
`SwingUtilities.invokeLater`. When implementing the state subscriber, prefer
one-at-a-time demand so replay-latest behavior naturally coalesces intermediate
observations.

Create/update/merge builders are thread-confined. Forms will own desktop draft
values, not live core builders. A core builder will eventually be created,
populated, saved, and closed within one background operation/thread.

## Ownership

The intended future hierarchy is:

```text
DesktopApplication
  -> zero or more VaultWindowController
       -> one VaultSession
       -> one VaultFrame
       -> one state subscription
       -> one serialized session task executor
```

There is no global "current vault". These future owners are not implemented in
M0. `LauncherFrame` only sets up a top-level window and hosts `LauncherPanel`;
it owns no application/domain state.

## State

1. Protocol/application state: `VaultSession` and `VaultState`.
2. Presentation state: selection, search, window state, and dialogs.
3. Operation state: editor drafts and outstanding result capabilities such as
   publication retry or partial resolution.

The desktop application must not maintain a mutable shadow copy of vault/token state.

`VaultState` remains authoritative. Desktop draft values belong to an operation
and do not replace published protocol state.

## Semantics

The UI must not collapse:

- conflicts into arbitrary winners;
- partial observation into completeness;
- publication uncertainty into failure or success;
- local publication into remote synchronization;
- multiple equal causal heads into semantic conflict.

Automatic retry, automatic merge, and invented sync semantics are prohibited
unless deliberately designed later.

## M0 boundary

M0 implements no open/create/read/edit operations, password dialogs, sessions,
state subscriptions, token lists, TOTP display, builders, Base32 decoding,
diagnostics, observation progress, password change, AdditionalConflict or
PublicationUncertain handling. It also excludes filesystem watching, automatic
refresh, clipboard, QR, tray, keychain, installers, jpackage, and desktop release
artifacts. Having the API on the classpath does not authorize these features.

Headless tests construct the panel on the EDT, check that actions are disabled,
and exercise the EDT guard. They create no top-level windows and require no
display, Robot, or screenshot infrastructure. Build/test also inspect every
desktop production class for Java 17 classfile version 61.
