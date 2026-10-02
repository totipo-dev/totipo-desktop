# Production runtime inventory

| Component | Version/revision | Upstream | License |
| --- | --- | --- | --- |
| Totipo Desktop | [VERSION](VERSION) (unreleased development) | https://github.com/totipo-org/totipo-desktop | Apache-2.0; packaged LICENSE |
| `org.totipo:totipo-storage-nio` | 0.1.0 | https://github.com/totipo-org/totipo-java/tree/v0.1.0 | Apache-2.0; licenses/TOTIPO_JAVA_LICENSE |
| `org.totipo:totipo-core` (transitive) | 0.1.0 | https://github.com/totipo-org/totipo-java/tree/v0.1.0 | Apache-2.0; licenses/TOTIPO_JAVA_LICENSE |
| Bouncy Castle bcprov-jdk18on | 1.86 | https://www.bouncycastle.org/ | MIT; licenses/BOUNCY_CASTLE_LICENSE.html |
| Nix full OpenJDK runtime | jdk25 from nixpkgs b6c8664de9b6cc07fe5666a29f91884ba81197c4; installed build environment 25.0.4.1+1 | https://openjdk.org/ | GPL-2.0 with Classpath exception; runtime legal notices remain in its Nix store closure |

The generic distribution supplies only the first four rows and requires a
user-supplied Java 17 or newer runtime. Nix selects the full JDK as a managed
runtime dependency; the package itself has not yet been built/qualified here.
The JDK's own legal directory includes additional third-party notices.
JUnit and other test dependencies are not shipped. Gradle is a build tool, not
an application runtime dependency. Maven-fetched Totipo Java and Bouncy Castle bytecode is
identified by Nix `sourceProvenance.binaryBytecode`.

The Bouncy Castle notice is copied unchanged from
https://raw.githubusercontent.com/bcgit/bc-java/r1rv86/LICENSE.html.
The already-packaged Totipo Java Apache-2.0 license notice is retained unchanged.
Java components are released Maven Central artifacts from source tag `v0.1.0`;
storage-nio exposes core transitively, and core brings BC 1.86 at runtime.
