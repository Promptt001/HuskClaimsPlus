/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.william278.huskclaims.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The config files users see are generated from the {@code CONFIG_HEADER} text
 * blocks in source, so rewriting a generated config.yml by hand is invisible
 * after the next run. Every config header must therefore identify the fork and
 * must not still advertise the upstream project page or authorship banner.
 */
class ConfigHeaderIdentityTests {

    // Written by the fork; the upstream project page and authorship banner
    // point readers at software this is not.
    private static final List<String> UPSTREAM_MARKERS = List.of(
            "william278.net/project/huskclaims",
            "Developed by William278");
    private static final String HEADER_DECLARATION = "CONFIG_HEADER = \"\"\"";
    // :common: tests run with the module directory as CWD
    private static final List<Path> SOURCE_ROOTS = List.of(
            Path.of("src", "main", "java"),
            Path.of("..", "fabric", "src", "main", "java"),
            Path.of("..", "bukkit", "src", "main", "java"),
            Path.of("..", "paper", "src", "main", "java"));

    @Test
    void everyConfigHeaderIdentifiesTheFork() throws IOException {
        final List<Path> headers = new ArrayList<>();
        final List<String> failures = new ArrayList<>();
        for (final Path root : SOURCE_ROOTS) {
            if (!Files.isDirectory(root)) {
                failures.add("source root not found (wrong CWD?): " + root.toAbsolutePath());
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (final Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    final String source = Files.readString(file);
                    if (!source.contains(HEADER_DECLARATION)) {
                        continue;
                    }
                    headers.add(file);
                    final String header = headerText(source);
                    if (!header.contains("HuskClaimsPlus")) {
                        failures.add(file.getFileName() + ": config header does not name HuskClaimsPlus");
                    }
                    for (final String marker : UPSTREAM_MARKERS) {
                        if (header.contains(marker)) {
                            failures.add(file.getFileName() + ": config header still contains \"" + marker + "\"");
                        }
                    }
                }
            }
        }
        assertFalse(headers.isEmpty(),
                "no CONFIG_HEADER text blocks found — the scan is not looking at the right files");
        assertTrue(failures.isEmpty(), "config header identity failures:\n" + String.join("\n", failures));
    }

    private static String headerText(String source) {
        final int start = source.indexOf(HEADER_DECLARATION);
        final int end = source.indexOf("\"\"\";", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

}
