package org.totipo.desktop;

import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import java.nio.file.Path;

/** Only a test boundary around the filesystem application entry point. */
interface VaultAccess {
    OpenResult open(Path directory, char[] password);
    CreateVaultResult create(Path directory, char[] password);
}
