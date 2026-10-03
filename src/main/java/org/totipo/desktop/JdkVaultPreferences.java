package org.totipo.desktop;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

/** Best-effort user preference; unavailable preferences must not prevent opening a vault. */
final class JdkVaultPreferences implements VaultPreferences {
    static final String NODE = "/org/totipo/desktop";
    static final String KEY = "lastVault";
    private final Preferences store;

    JdkVaultPreferences() { this(userStore()); }
    private static Preferences userStore() {
        try { return Preferences.userRoot().node(NODE); }
        catch (SecurityException unavailable) { return null; }
    }
    JdkVaultPreferences(Preferences store) { this.store = store; }

    @Override public Optional<Path> lastVault() {
        if (store == null) { return Optional.empty(); }
        try {
            String value = store.get(KEY, null);
            return value == null ? Optional.empty() : Optional.of(Path.of(value).toAbsolutePath().normalize());
        } catch (InvalidPathException invalid) {
            clearLastVault();
            return Optional.empty();
        } catch (SecurityException unavailable) {
            return Optional.empty();
        }
    }
    @Override public void setLastVault(Path path) {
        if (store == null) { return; }
        try { store.put(KEY, path.toAbsolutePath().normalize().toString()); }
        catch (SecurityException | IllegalArgumentException unavailable) { /* Optional desktop convenience. */ }
    }
    @Override public void clearLastVault() {
        if (store == null) { return; }
        try { store.remove(KEY); }
        catch (SecurityException unavailable) { /* Optional desktop convenience. */ }
    }
}
