package org.totipo.desktop;

import org.totipo.desktop.clipboard.TotpClipboard;

import org.totipo.CreateVaultResult;
import org.totipo.OpenResult;
import org.totipo.VaultSession;
import org.totipo.desktop.ui.Edt;
import org.totipo.desktop.ui.LauncherFrame;
import org.totipo.desktop.ui.LauncherPanel;
import org.totipo.desktop.ui.LauncherView;
import org.totipo.desktop.ui.VaultFrame;
import org.totipo.desktop.ui.VaultView;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import javax.swing.SwingUtilities;

/** EDT-owned launcher, application executor, and independent live vault controllers. */
public final class DesktopApplication {
    private final VaultAccess access;
    private final LauncherView launcher;
    private final Function<Path, VaultView> windows;
    private final ExecutorService executor;
    private final Set<VaultWindowController> controllers = new HashSet<>();
    private boolean busy;
    private final TotpClipboard clipboard;
    private boolean shuttingDown;
    private boolean disposed;
    private int nextWindow;
    private final VaultPreferences preferences;
    private VaultWindowController replacing;
    private Path chooserLocation;

    public DesktopApplication() {
        this(new NioVaultAccess(), new LauncherFrame(), VaultFrame::new, new TotpClipboard(),
                new JdkVaultPreferences());
    }

    DesktopApplication(VaultAccess access, LauncherView launcher, Function<Path, VaultView> windows) {
        this(access, launcher, windows, new TotpClipboard());
    }

    DesktopApplication(VaultAccess access, LauncherView launcher, Function<Path, VaultView> windows,
                       TotpClipboard clipboard) {
        this(access, launcher, windows, clipboard, new VaultPreferences() {
            public java.util.Optional<Path> lastVault() { return java.util.Optional.empty(); }
            public void setLastVault(Path path) { }
            public void clearLastVault() { }
        });
    }

    DesktopApplication(VaultAccess access, LauncherView launcher, Function<Path, VaultView> windows,
                       TotpClipboard clipboard, VaultPreferences preferences) {
        Edt.require();
        this.preferences = preferences;
        this.clipboard = clipboard;
        this.access = access;
        this.launcher = launcher;
        this.windows = windows;
        executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "totipo-application"));
        launcher.actions(() -> prompt(false), () -> prompt(true), this::shutdown);
    }

    public void show() {
        Edt.require();
        if (busy || shuttingDown) { return; }
        var remembered = preferences.lastVault();
        if (remembered.isEmpty()) {
            launcher.showWindow();
            return;
        }
        Path path = remembered.get();
        chooserLocation = path;
        busy = true;
        launcher.busy("Finding previous vault…", true);
        executor.execute(() -> {
            boolean usable;
            try {
                usable = Files.isDirectory(path) && Files.isReadable(path) && Files.isExecutable(path);
            } catch (SecurityException unavailable) {
                usable = false;
            }
            boolean found = usable;
            SwingUtilities.invokeLater(() -> {
                if (shuttingDown) { finishOperation(); return; }
                if (found) {
                    busy = false;
                    prompt(false, path);
                } else {
                    preferences.clearLastVault();
                    launcher.showWindow();
                    try {
                        launcher.message("Previous vault unavailable",
                                "The previously used vault could not be found. Choose a vault to continue.");
                    } finally { finishOperation(); }
                }
            });
        });
    }

    private void prompt(boolean create) {
        prompt(create, null);
    }

    private void prompt(boolean create, Path remembered) {
        Edt.require();
        if (busy || shuttingDown) {
            return;
        }
        // Modal dialogs run nested EDT loops. Reserve the launcher before prompting,
        // and recheck shutdown after every dialog before accepting password ownership.
        busy = true;
        launcher.busy(create ? "Create Vault" : "Open Vault", true);
        char[] password = null;
        boolean handedOff = false;
        try {
            Path directory = remembered == null ? launcher.chooseDirectory(chooserLocation, create) : remembered;
            if (directory == null || shuttingDown) {
                return;
            }
            password = launcher.password(directory.toAbsolutePath().normalize(), create);
            if (password == null || shuttingDown) {
                return;
            }
            busy = false;
            char[] submittedPassword = password;
            password = null;
            handedOff = true;
            begin(directory, submittedPassword, create);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            // An operation owns busy after handoff; cancellation owns it here.
            if (!handedOff) {
                finishOperation();
            }
        }
    }

    /** Takes ownership of password even when rejected. Also used by headless lifecycle tests. */
    void begin(Path directory, char[] password, boolean create) {
        Edt.require();
        if (busy || shuttingDown) {
            Arrays.fill(password, '\0');
            return;
        }
        if (!PasswordInput.valid(password)) {
            Arrays.fill(password, '\0');
            // Keep actions disabled throughout the modal result dialog.
            busy = true;
            launcher.busy("Invalid password input", true);
            try {
                launcher.message("Invalid password input",
                        "Enter valid Unicode using at most 1024 UTF-8 bytes.");
            } finally {
                finishOperation();
            }
            return;
        }
        busy = true;
        launcher.busy(create ? "Creating vault…" : "Opening vault…", true);
        if (create && password.length == 0) {
            boolean confirmed = false;
            try {
                confirmed = launcher.confirmEmptyPassword();
            } finally {
                // A modal confirmation can process shutdown/reentrant launcher actions.
                if (!confirmed || shuttingDown) {
                    Arrays.fill(password, '\0');
                    finishOperation();
                }
            }
            if (!confirmed || shuttingDown) { return; }
        }
        executor.execute(() -> {
            OpenResult opened = null;
            CreateVaultResult created = null;
            boolean failed = false;
            try {
                if (create) {
                    created = access.create(directory, password);
                } else {
                    opened = access.open(directory, password);
                }
            } catch (RuntimeException unexpected) {
                failed = true;
                System.err.println("Totipo: unexpected open/create failure (details redacted).");
            } finally {
                Arrays.fill(password, '\0');
            }
            OpenResult openResult = opened;
            CreateVaultResult createResult = created;
            boolean unexpectedFailure = failed;
            SwingUtilities.invokeLater(() -> accept(directory, openResult, createResult, unexpectedFailure));
        });
    }

    private void accept(Path directory, OpenResult open, CreateVaultResult create, boolean failed) {
        Edt.require();
        VaultSession session = open instanceof OpenResult.Opened opened ? opened.session()
                : create instanceof CreateVaultResult.Created created ? created.session() : null;
        if (session != null) {
            if (shuttingDown) {
                closeUnclaimed(session);
                return;
            }
            VaultView view = null;
            VaultWindowController controller;
            try {
                view = windows.apply(directory);
                controller = new VaultWindowController(session, view, ++nextWindow, this::controllerClosed,
                        reason -> {
                            if (!shuttingDown) { launcher.message("Vault closed — reopen required", reason); }
                        }, clipboard);
            } catch (RuntimeException unexpected) {
                // No controller accepted ownership; cleanup stays on the application executor.
                try {
                    if (view != null) {
                        view.dispose();
                    }
                    launcher.message("Application failure", "The vault window could not be created.");
                } finally {
                    // Keep the launcher reserved across the modal result dialog and cleanup.
                    closeUnclaimed(session);
                }
                return;
            }
            controllers.add(controller);
            view.quitAction(this::shutdown);
            VaultWindowController previous = replacing;
            view.changeVaultAction(() -> {
                if (busy || shuttingDown) { return; }
                replacing = controller;
                chooserLocation = directory;
                launcher.showWindow();
                prompt(false);
            });
            try {
                if (controller.start()) {
                    preferences.setLastVault(directory);
                    chooserLocation = directory;
                    if (previous != null) { previous.close(); }
                }
            } finally {
                finishOperation();
            }
            return;
        }
        try {
            if (!shuttingDown) {
                if (failed) {
                    launcher.message("Application failure", "The vault operation encountered an unexpected application failure.");
                } else if (open != null) {
                    present(open);
                } else if (create != null) {
                    present(create);
                } else {
                    launcher.message("Application failure", "The vault operation returned no result.");
                }
            }
        } finally {
            finishOperation();
        }
    }

    private void present(OpenResult result) {
        if (result instanceof OpenResult.Absent) {
            launcher.message("Vault absent", "No canonical Totipo vault was observed in the selected directory.");
        } else if (result instanceof OpenResult.Unavailable) {
            launcher.message("Vault unavailable", "The selected directory or vault could not be reliably accessed.");
        } else if (result instanceof OpenResult.InvalidVault) {
            launcher.message("Invalid vault", "Vault data was observed but could not be used as a valid vault.");
        } else if (result instanceof OpenResult.AuthenticationFailed) {
            launcher.message("Authentication did not succeed",
                    "Authentication did not succeed. This does not prove that the entered password is incorrect;\n"
                    + "authenticated vault data may also have changed or become unusable.");
        } else {
            throw new IllegalArgumentException("Unhandled open result");
        }
    }

    private void present(CreateVaultResult result) {
        if (result instanceof CreateVaultResult.AlreadyExists) {
            launcher.message("Vault already exists", "A vault was already observed at this location. Nothing was overwritten.\n"
                    + "You may explicitly choose Open Vault.");
        } else if (result instanceof CreateVaultResult.Failed) {
            launcher.message("Creation failed", "This create operation definitely failed. It was not retried.");
        } else if (result instanceof CreateVaultResult.Uncertain) {
            launcher.message("Creation uncertain", "Creation may have succeeded. Totipo cannot assert whether this operation became canonical.\n"
                    + "Do not blindly retry creation. Use Open Vault to re-observe the directory.");
        } else {
            throw new IllegalArgumentException("Unhandled create result");
        }
    }

    private void closeUnclaimed(VaultSession session) {
        executor.execute(() -> {
            boolean failed = false;
            try {
                session.close();
            } catch (RuntimeException unexpected) {
                failed = true;
                System.err.println("Totipo: unexpected unclaimed session close failure (details redacted).");
            } finally {
                boolean closeFailed = failed;
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (closeFailed) {
                            launcher.message("Session failure", "The returned vault session encountered an unexpected close failure.");
                        }
                    } finally {
                        finishOperation();
                    }
                });
            }
        });
    }

    private void finishOperation() {
        Edt.require();
        busy = false;
        if (!shuttingDown) {
            launcher.busy(LauncherPanel.READY_TEXT, false);
            if (controllers.isEmpty()) { launcher.showWindow(); }
            else { launcher.hideWindow(); }
        }
        replacing = null;
        finishShutdown();
    }

    void shutdown() {
        Edt.require();
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        clipboard.shutdown();
        launcher.busy("Closing…", true);
        for (VaultWindowController controller : Set.copyOf(controllers)) {
            controller.close();
        }
        finishShutdown();
    }

    private void controllerClosed(VaultWindowController controller) {
        Edt.require();
        controllers.remove(controller);
        if (!shuttingDown && !busy && controllers.isEmpty()) { launcher.showWindow(); }
        finishShutdown();
    }

    private void finishShutdown() {
        if (shuttingDown && !busy && controllers.isEmpty() && !disposed) {
            disposed = true;
            executor.shutdown();
            launcher.dispose();
        }
    }

    boolean executorShutdown() {
        return executor.isShutdown();
    }
}
