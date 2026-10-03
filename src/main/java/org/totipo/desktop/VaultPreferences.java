package org.totipo.desktop;

import java.nio.file.Path;
import java.util.Optional;

/** Desktop-only remembered location. Never stores credentials or vault data. */
public interface VaultPreferences {
    Optional<Path> lastVault();
    void setLastVault(Path path);
    void clearLastVault();
}
