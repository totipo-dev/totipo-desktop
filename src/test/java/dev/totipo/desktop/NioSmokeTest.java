package dev.totipo.desktop;

import dev.totipo.CreateVaultResult;
import dev.totipo.OpenResult;
import dev.totipo.storage.nio.NioTotipo;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NioSmokeTest {
    @TempDir Path directory;

    @Test void createCloseOpenCloseInExistingDirectory() {
        char[] password = {'s', 'm', 'o', 'k', 'e'};
        try {
            CreateVaultResult.Created created = assertInstanceOf(CreateVaultResult.Created.class,
                    NioTotipo.create(directory, password));
            created.session().close();
            OpenResult.Opened opened = assertInstanceOf(OpenResult.Opened.class,
                    NioTotipo.open(directory, password));
            opened.session().close();
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
