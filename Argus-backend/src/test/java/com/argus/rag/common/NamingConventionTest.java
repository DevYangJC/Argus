package com.argus.rag.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NamingConventionTest {

    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "dd" + "rag",
            "dd" + "_rag",
            "dd" + "-rag"
    );

    private static final Set<String> SKIPPED_DIRS = Set.of(
            ".git",
            "target",
            "node_modules",
            "dist"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java",
            ".xml",
            ".yml",
            ".yaml",
            ".properties",
            ".md",
            ".txt",
            ".example",
            ".env",
            ".json"
    );

    @Test
    void repositoryTextFilesDoNotContainLegacyProjectMarkers() throws IOException {
        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();

        List<String> matches;
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            matches = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isTextFile)
                    .filter(this::isNotInSkippedDirectory)
                    .flatMap(path -> findForbiddenMarkers(repositoryRoot, path).stream())
                    .toList();
        }

        assertThat(matches).isEmpty();
    }

    private boolean isTextFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean isNotInSkippedDirectory(Path path) {
        for (Path part : path) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        return true;
    }

    private List<String> findForbiddenMarkers(Path repositoryRoot, Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            return List.of(repositoryRoot.relativize(path) + ": unreadable as UTF-8");
        }

        return FORBIDDEN_MARKERS.stream()
                .filter(content::contains)
                .map(marker -> repositoryRoot.relativize(path) + ": " + marker)
                .toList();
    }
}
