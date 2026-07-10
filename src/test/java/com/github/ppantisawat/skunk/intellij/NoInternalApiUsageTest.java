package com.github.ppantisawat.skunk.intellij;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

public final class NoInternalApiUsageTest {
    @Test
    public void productionSourcesDoNotUsePluginManagerCore() throws IOException {
        Path sourceRoot = Path.of("src", "main");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            List<String> violations = paths
                .filter(Files::isRegularFile)
                .filter(NoInternalApiUsageTest::isSourceFile)
                .filter(NoInternalApiUsageTest::containsPluginManagerCore)
                .map(Path::toString)
                .collect(Collectors.toList());

            assertTrue("Internal API usage found: " + violations, violations.isEmpty());
        }
    }

    private static boolean isSourceFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".java") || fileName.endsWith(".scala") || fileName.endsWith(".kt");
    }

    private static boolean containsPluginManagerCore(Path path) {
        try {
            return Files.readString(path).contains("PluginManagerCore");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
