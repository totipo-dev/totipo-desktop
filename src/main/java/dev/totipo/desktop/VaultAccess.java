package dev.totipo.desktop;

import dev.totipo.CreateVaultResult;
import dev.totipo.OpenResult;
import java.nio.file.Path;

/** Only a test boundary around the filesystem application entry point. */
interface VaultAccess {
    OpenResult open(Path directory, char[] password);
    CreateVaultResult create(Path directory, char[] password);
}
