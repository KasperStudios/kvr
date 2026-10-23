package com.kasperstudios.kvr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Seam: the module boundary itself (SPEC section 1, rule 1).
 * The platform-independent core must compile and run without a single mod-loader import;
 * every loader-specific behaviour belongs to the {@code fabric} / {@code neoforge} branches.
 */
class ArchitectureIsolationTest {

    private static final List<String> FORBIDDEN = List.of(
            "net.fabricmc.", "net.neoforged.", "net.minecraftforge."
    );

    @Test
    void commonCoreHasNoLoaderImports() throws IOException {
        Path core = repositoryRoot().resolve("src").resolve("main").resolve("java");
        assertTrue(Files.isDirectory(core), "expected the common source root at " + core.toAbsolutePath());

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(core)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("import ")) continue;
                    for (String forbidden : FORBIDDEN) {
                        if (line.contains(forbidden)) {
                            violations.add(file + ":" + (i + 1) + " -> " + line);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "platform-independent core imported loader classes:\n" + String.join("\n", violations));
    }

    /**
     * Tests run in the Stonecutter node directory ({@code versions/<mc>}), so the shared core
     * sources are located by walking up to the repository root.
     */
    private static Path repositoryRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path settings = dir.resolve("settings.gradle.kts");
            if (Files.isRegularFile(settings) && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the KVR repository root from " + Path.of("").toAbsolutePath());
    }
}
