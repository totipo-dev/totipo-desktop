# Totipo Java released dependency

Desktop directly consumes `org.totipo:totipo-storage-nio:0.1.1` from Maven
Central. Its public dependency exposes `org.totipo:totipo-core:0.1.1`
transitively; desktop deliberately does not declare core directly. Core adds
`org.bouncycastle:bcprov-jdk18on:1.86` at runtime.

Upstream: https://github.com/totipo-org/totipo-java, source tag `v0.1.1`.
Release commit: `dfd76a9a9cff261fdb214ac85457bddb06f1d777`.
That release targets Totipo Vault Format v1/r18 at spec commit
`4623a7e1718e23504903096c92332597057bd8f0` (a committed revision, not an r18 release tag).
Maven Central is the
consumer boundary; Java source/spec snapshots and the conformance corpus are
no longer part of the desktop source or package graph. Their provenance and
conformance validation belong to the Java release. Desktop does not independently
carry/pin those protocol artifacts and does not inherit application conformance
from Java's operation-scoped core/store claims.

The exact Java version is configured in `build.gradle.kts`. Gradle strict
dependency locks and SHA-256 verification metadata enforce the resolved
dependencies. `verifyMavenBoundary`, wired into `check`, requires external
modules and the transitive core relationship. Nix `package-deps.json`
separately pins package-build downloads. Human-operated regeneration for this
repin succeeded; reviewed 0.1.1 JAR/module/POM hashes match Maven Central bytes,
with BC 1.86 and unrelated entries unchanged. Human-operated x86_64-linux flake
check/build/rebuild and final package inventory/hash review passed, recorded
separately in the M4b report.

Desktop uses the high-level `org.totipo` application APIs, including
`VaultSession` and `VaultState`, and the filesystem entry point
`org.totipo.storage.nio.NioTotipo`. It does not consume storage SPI or
implementation internals. No local source or Maven-local fallback is configured.
