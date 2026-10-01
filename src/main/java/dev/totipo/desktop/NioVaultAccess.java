package dev.totipo.desktop;

import dev.totipo.CreateVaultResult;
import dev.totipo.OpenResult;
import dev.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;

final class NioVaultAccess implements VaultAccess {
    @Override public OpenResult open(Path directory, char[] password) {
        return NioTotipo.open(directory, password);
    }
    @Override public CreateVaultResult create(Path directory, char[] password) {
        return NioTotipo.create(directory, password);
    }
}
