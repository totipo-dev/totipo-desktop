# Native clipboard smoke — UNQUALIFIED until executed

Record date, artifact, OS, architecture, Java, desktop/window manager, X11/Wayland,
clipboard manager/history application and version (or explicitly none), paste
application/version and any remote-desktop layer. Use disposable test secrets.
Run from the actual package. Each unchecked item remains UNQUALIFIED.

- [ ] Deliberate Copy code pastes exactly the displayed digits in another application.
- [ ] A leading-zero code retains all digits.
- [ ] Current owned payload clears at its code expiry.
- [ ] Current owned payload clears no later than the 30-second cap (use a long-period test token).
- [ ] Different external replacement survives Totipo's expiry/close cleanup.
- [ ] Identical text copied by another application survives cleanup; marker ownership, not text equality, governs clearing.
- [ ] A newer Totipo copy survives an older copy's callback/expiry; its own expiry still applies.
- [ ] Closing another vault does not clear the origin vault's current copy.
- [ ] Closing the origin vault/application attempts to clear its owned payload.
- [ ] TOTP rollover does not automatically copy a new code or restore old data.
- [ ] Selection and filter changes do not automatically copy; hidden selections clear normally.
- [ ] Active conflict alternatives have independent Copy buttons; ineligible/tombstoned states do not copy.
- [ ] Clipboard contention/unavailability is handled without freeze, automatic recopy or restoration; recovery needs another deliberate click. If not practical, record UNQUALIFIED.
- [ ] Observe clipboard manager/history retention and identical replacement behavior explicitly.

Clearing is best effort and cannot remove history or external copies. Record any
manager's loss of Totipo's marker or retention of codes as a limitation; do not
claim secure erasure. Do not save actual codes or clipboard contents in evidence.
