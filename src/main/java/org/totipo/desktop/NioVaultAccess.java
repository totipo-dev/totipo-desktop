package org.totipo.desktop;

import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import org.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;

final class NioVaultAccess implements VaultAccess {
    @Override public OpenResult open(Path directory, char[] password) {
        return NioTotipo.open(directory, password);
    }
    @Override public CreateVaultResult create(Path directory, char[] password) {
        return NioTotipo.create(directory, password);
    }
}
