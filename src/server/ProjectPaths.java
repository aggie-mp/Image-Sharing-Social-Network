package server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves shared input files from either the project root or src working directory. */
public final class ProjectPaths {
    private ProjectPaths() {}

    /** Absolute server storage root, independent of the run configuration. */
    public static String serverRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 8 && current != null; i++, current = current.getParent()) {
            Path src = current.getFileName() != null && current.getFileName().toString().equals("src")
                    ? current : current.resolve("src");
            if (Files.isDirectory(src.resolve("server")) && Files.isDirectory(src.resolve("client"))) {
                return src.resolve("server").toAbsolutePath().normalize().toString();
            }
        }
        throw new IllegalStateException("Cannot locate src/server from working directory: "
                + Paths.get("").toAbsolutePath());
    }

    public static String dataFile(String filename) {
        Path direct = Paths.get("data", filename);
        if (Files.isRegularFile(direct)) return direct.toString();
        Path parent = Paths.get("..", "data", filename);
        if (Files.isRegularFile(parent)) return parent.toString();
        throw new IllegalStateException("Missing data file: " + filename
            + " (working directory: " + Paths.get("").toAbsolutePath() + ")");
    }
}
