# Totipo Java dependency pin

- Repository: https://github.com/totipo-dev/totipo-java
- Exact inspected and pinned commit: `3d97dca72604f39b6b475c0d5a0a8b076632cc42`
- Observed on upstream `main` at pin time, 2026-09-30.
- Protocol target: Totipo Vault Format v1/r17.
- Corpus status observed at pin time: **90/90**, none deferred.

The pinned [README](vendor/totipo-java/README.md) reports the implementation
coverage. [SPEC_PIN.md](vendor/totipo-java/SPEC_PIN.md) records r17 and the
90-case snapshot at specification commit
`1d42a481f230e0adbb89dbeaa936d3c956e70fdc`. These are upstream evidence,
not a new desktop conformance or security claim.

The Git submodule gitlink at `vendor/totipo-java` is authoritative for the exact
source pin. It does not implicitly track a branch. Updates must be deliberate
and reviewed. Moving the submodule pointer is a dependency update and must not
happen incidentally. Review protocol/API changes and reproducibility inputs
together, and update this document when deliberately changing the pin.

The desktop consumes the high-level public application API (`dev.totipo`) and
`NioTotipo` (`dev.totipo.storage.nio`). Ordinary application code must not
bypass these APIs to call the storage SPI or implementation internals. The
single dependency on `storage-nio` publicly exposes `core`; no Java source is
copied from upstream.
