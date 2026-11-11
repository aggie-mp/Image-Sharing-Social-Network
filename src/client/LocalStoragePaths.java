package client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Find the same src/client/users location regardless of IntelliJ working directory. */
public final class LocalStoragePaths {
    private LocalStoragePaths() {}

    public static Path usersRoot() throws IOException {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && current != null; depth++, current = current.getParent()) {
            Path src = current.getFileName() != null && current.getFileName().toString().equals("src")
                    ? current : current.resolve("src");
            if (Files.isDirectory(src.resolve("client")) && Files.isDirectory(src.resolve("server"))) {
                Path root = src.resolve("client").resolve("users");
                Files.createDirectories(root);
                return root.toRealPath();
            }
        }
        throw new IOException("Cannot locate project src/client directory from working directory "
                + Paths.get("").toAbsolutePath());
    }
}
